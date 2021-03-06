package search.system.peer.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortField.Type;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.kompics.web.Web;
import se.sics.kompics.web.WebRequest;
import se.sics.kompics.web.WebResponse;
import search.simulator.snapshot.Snapshot;
import search.system.peer.AddIndexText;
import search.system.peer.IndexPort;
import search.system.peer.search.messages.Find;
import search.system.peer.search.messages.Insert;
import search.system.peer.search.messages.Push;
import search.system.peer.search.messages.Push.Accept;
import search.system.peer.search.messages.Push.Offer;
import search.system.peer.search.messages.Push.Payload;
import tman.system.peer.tman.TManSample;
import tman.system.peer.tman.TManSamplePort;

import common.configuration.SearchConfiguration;
import common.configuration.TManConfiguration;

import cyclon.system.peer.cyclon.CyclonSample;
import cyclon.system.peer.cyclon.CyclonSamplePort;

/**
 * Should have some comments here.
 * 
 * @author jdowling
 */
public final class Search extends ComponentDefinition {

    public static class SearchSchedule extends Timeout {

	public SearchSchedule(SchedulePeriodicTimeout request) {
	    super(request);
	}

	public SearchSchedule(ScheduleTimeout request) {
	    super(request);
	}
    }

    public static class SearchTimeout extends Timeout {

	private final UUID id;

	public SearchTimeout(UUID id, ScheduleTimeout timeout) {
	    super(timeout);
	    this.id = id;
	}

	public UUID getId() {
	    return id;
	}
    }

    private common.Logger.Instance logger;
    private final Positive<IndexPort> indexPort = positive(IndexPort.class);
    private final Positive<Network> networkPort = positive(Network.class);
    private final Positive<Timer> timerPort = positive(Timer.class);
    private final Negative<Web> webPort = negative(Web.class);
    private final Positive<CyclonSamplePort> cyclonSamplePort = positive(CyclonSamplePort.class);
    private final Positive<TManSamplePort> tmanPort = positive(TManSamplePort.class);
    // /////////
    private Address self;
    private Address leader;
    private final ArrayList<Address> allNeighbours = new ArrayList<>();
    private Collection<Address> gradientAbove = new LinkedList<>();
    private Collection<Address> gradientBelow = new LinkedList<>();
    private Map<Integer, Address> routes = new HashMap<>(
	    TManConfiguration.PARTITION_COUNT);
    private final Object sync = new Object();
    private final Random r = new Random(System.currentTimeMillis());
    private final Set<String> knownEntries = new HashSet<>();
    /**
     * The requests awaiting confirmation from the leader key - source|uuid
     */
    private final Map<String, Insert.Request> requests = new HashMap<>(8);
    /**
     * The requests awaiting confirmation from the leaders neighbours key -
     * source|uuid
     */
    private final Map<String, Insert.Request> waiting = new HashMap<>(8);
    private final Set<Integer> existingIds = new HashSet<>();
    private final Map<UUID, SearchRequestInfo> pendingSearches = new HashMap<>();
    private final Set<UUID> have = new HashSet<>();
    // Apache Lucene used for searching
    private final StandardAnalyzer analyzer = new StandardAnalyzer(
	    Version.LUCENE_42);
    private final Directory index = new RAMDirectory();
    private final IndexWriterConfig config = new IndexWriterConfig(
	    Version.LUCENE_42, analyzer);
    // //////////////
    int maxIndexEntry = 0;

    // -------------------------------------------------------------------
    public Search() {

	subscribe(handleInit, control);
	subscribe(handleWebRequest, webPort);
	subscribe(handlerCyclonSample, cyclonSamplePort);
	subscribe(handlerAddIndexText, indexPort);
	subscribe(handlerSchedule, timerPort);
	subscribe(handlerSearchTimeout, timerPort);
	subscribe(handlerTManSample, tmanPort);

	subscribe(handlerInsertRequest, networkPort);
	subscribe(handlerInsertResponse, networkPort);
	subscribe(handlerPushAccept, networkPort);
	subscribe(handlerPushOffer, networkPort);
	subscribe(handlerPushPayload, networkPort);
	subscribe(handlerPushRequest, networkPort);
	subscribe(handlerFindRequest, networkPort);
	subscribe(handlerFindResponse, networkPort);
    }

    // -------------------------------------------------------------------
    Handler<SearchInit> handleInit = new Handler<SearchInit>() {
	@Override
	public void handle(SearchInit init) {
	    synchronized (sync) {
		self = init.getSelf();
		logger = new common.Logger.Instance(self + "[" + self.getId()
			% TManConfiguration.PARTITION_COUNT + "]");

		SchedulePeriodicTimeout rst = new SchedulePeriodicTimeout(
			SearchConfiguration.SEARCH_SCHEDULE_TIMEOUT * 2,
			SearchConfiguration.SEARCH_SCHEDULE_TIMEOUT);

		rst.setTimeoutEvent(new SearchSchedule(rst));

		trigger(rst, timerPort);

		// TODO super ugly
		// workaround...
		IndexWriter writer;
		try {
		    writer = new IndexWriter(index, config);
		    writer.commit();
		    writer.close();
		} catch (IOException e) {
		    // TODO Auto-generated
		    // catch block
		    e.printStackTrace();
		}
	    }
	}
    };
    Handler<WebRequest> handleWebRequest = new Handler<WebRequest>() {
	@Override
	public void handle(WebRequest event) {

	    synchronized (sync) {
		String[] args = event.getTarget().split("-");

		String response;

		try {
		    if (args[0].compareToIgnoreCase("search") == 0) {
			triggerSearch(event, args[1]);
			return;
		    } else if (args[0].compareToIgnoreCase("add") == 0) {
			response = addEntryHtml(args[1]);
		    } else {
			throw new Exception();
		    }
		} catch (Exception ex) {
		    ex.printStackTrace();
		    response = "Invalid request";
		}
		trigger(new WebResponse(response, event, 1, 1), webPort);
	    }
	}
    };

    private void triggerSearch(WebRequest request, String query) {
	logger.log("Starting search for " + query);
	SearchRequestInfo sfi = new SearchRequestInfo(request);
	UUID id = UUID.randomUUID();
	pendingSearches.put(id, sfi);
	for (Address a : routes.values()) {
	    Find.Request r = new Find.Request(self, a, query, id);
	    sfi.addPartitionRequestId(a);
	    trigger(r, networkPort);
	}
	Find.Request r = new Find.Request(self, self, query, id);
	sfi.addPartitionRequestId(self);
	// Send to switch thread
	trigger(r, networkPort);

	ScheduleTimeout to = new ScheduleTimeout(
		SearchConfiguration.SEARCH_REQUEST_TIMEOUT);
	to.setTimeoutEvent(new SearchTimeout(id, to));
	trigger(to, timerPort);
    }

    private String searchPageHtml(Collection<IndexEntry> entries) {
	StringBuilder sb = new StringBuilder("<!DOCTYPE html PUBLIC \"-//W3C");
	sb.append("//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR");
	sb.append("/xhtml1/DTD/xhtml1-transitional.dtd\"><html xmlns=\"http:");
	sb.append("//www.w3.org/1999/xhtml\"><head><meta http-equiv=\"Conten");
	sb.append("t-Type\" content=\"text/html; charset=utf-8\" />");
	sb.append("<title>Kompics P2P Bootstrap Server</title>");
	sb.append("<style type=\"text/css\"><!--.style2 {font-family: ");
	sb.append("Arial, Helvetica, sans-serif; color: #0099FF;}--></style>");
	sb.append("</head><body><h2 align=\"center\" class=\"style2\">");
	sb.append("ID2210 (Decentralized Search for Piratebay)</h2><br>");

	sb.append("Found ").append(entries.size()).append(" entries.<ul>");
	for (IndexEntry e : entries) {
	    sb.append("<li>");
	    sb.append(e.getText());
	    sb.append("</li>");
	}
	sb.append("</ul>");

	sb.append("</body></html>");
	return sb.toString();
    }

    private String addEntryHtml(String title) {
	StringBuilder sb = new StringBuilder("<!DOCTYPE html PUBLIC \"-//W3C");
	sb.append("//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR");
	sb.append("/xhtml1/DTD/xhtml1-transitional.dtd\"><html xmlns=\"http:");
	sb.append("//www.w3.org/1999/xhtml\"><head><meta http-equiv=\"Conten");
	sb.append("t-Type\" content=\"text/html; charset=utf-8\" />");
	sb.append("<title>Adding an Entry</title>");
	sb.append("<style type=\"text/css\"><!--.style2 {font-family: ");
	sb.append("Arial, Helvetica, sans-serif; color: #0099FF;}--></style>");
	sb.append("</head><body><h2 align=\"center\" class=\"style2\">");
	sb.append("ID2210 Uploaded Entry</h2><br>");
	try {
	    handlerAddIndexText.handle(new AddIndexText(title));
	    sb.append("Entry: ").append(title);
	} catch (Exception ex) {
	    sb.append(ex.getMessage());
	    java.util.logging.Logger.getLogger(Search.class.getName()).log(
		    Level.SEVERE, null, ex);
	}
	sb.append("</body></html>");
	return sb.toString();
    }

    private Collection<Integer> getMissingIds(int lastId) {
	int missingCount = lastId - existingIds.size();
	Collection<Integer> missingIds = new ArrayList<>();
	if (missingCount > 0) {
	    for (int i = lastId; i > 0 && missingCount > 0; --i) {
		if (!existingIds.contains(i)) {
		    missingIds.add(i);
		    missingCount--;
		}
	    }
	} else if (!existingIds.contains(lastId)) {
	    missingIds.add(lastId);
	}
	return missingIds;
    }

    private synchronized boolean addEntry(String title, int id) {
	if (existingIds.contains(id)) {
	    return false;
	}
	try {
	    IndexWriter w = new IndexWriter(index, config);
	    Document doc = new Document();
	    doc.add(new TextField("title", title, Field.Store.YES));
	    // Use a NumericRangeQuery to find missing index entries:
	    // http://lucene.apache.org/core/4_2_0/core/org/apache/lucene/search/NumericRangeQuery.html
	    // http://lucene.apache.org/core/4_2_0/core/org/apache/lucene/document/IntField.html
	    doc.add(new IntField("id", id, Field.Store.YES));
	    w.addDocument(doc);
	    w.close();
	    Snapshot.incNumIndexEntries(self);
	    existingIds.add(id);
	    maxIndexEntry = Math.max(maxIndexEntry, id);
	    // logger.log("[ADDING] " + id + ": " + title);
	    return true;
	} catch (IOException ex) {
	    Logger.getLogger(Search.class.getName())
	    .log(Level.SEVERE, null, ex);
	    return false;
	}
    }

    private Collection<IndexEntry> find(String querystr) throws ParseException,
    IOException {

	// the "title" arg specifies the default field to use when no field is
	// explicitly specified in the query.
	Query q = new QueryParser(Version.LUCENE_42, "title", analyzer)
	.parse(querystr);
	IndexSearcher searcher = null;
	IndexReader reader = null;
	try {
	    reader = DirectoryReader.open(index);
	    searcher = new IndexSearcher(reader);
	} catch (IOException ex) {
	    java.util.logging.Logger.getLogger(Search.class.getName()).log(
		    Level.SEVERE, null, ex);
	    System.exit(-1);
	}

	int hitsPerPage = 10;
	TopScoreDocCollector collector = TopScoreDocCollector.create(
		hitsPerPage, true);
	searcher.search(q, collector);
	ScoreDoc[] hits = collector.topDocs().scoreDocs;

	Collection<IndexEntry> result = new LinkedList<>();

	for (int i = 0; i < hits.length; ++i) {
	    int docId = hits[i].doc;
	    Document d = searcher.doc(docId);
	    IndexEntry e = new IndexEntry(Integer.parseInt(d.get("id")),
		    d.get("title"));
	    result.add(e);
	}

	// reader can only be closed when there
	// is no need to access the documents any more.
	reader.close();
	return result;
    }

    ScoreDoc[] getExistingDocsInRange(int min, int max, IndexReader reader,
	    IndexSearcher searcher) throws IOException {
	reader = DirectoryReader.open(index);
	searcher = new IndexSearcher(reader);
	// The line below is dangerous - we should bound the number of entries
	// returned
	// so that it doesn't consume too much memory.
	int hitsPerPage = max - min > 0 ? max - min + 1 : 1;
	Query query = NumericRangeQuery.newIntRange("id", min, max, true, true);
	TopDocs topDocs = searcher.search(query, hitsPerPage, new Sort(
		new SortField("id", Type.INT)));
	return topDocs.scoreDocs;
    }

    List<IndexEntry> getEntries(LinkedList<Integer> ids) {

	LinkedList<IndexEntry> entries = new LinkedList<>();
	Collections.sort(ids);
	try {
	    IndexSearcher searcher = null;
	    IndexReader reader = null;
	    ScoreDoc[] hits = getExistingDocsInRange(ids.getFirst(),
		    Math.min(ids.getLast(), maxIndexEntry), reader, searcher);
	    if (hits != null) {
		for (ScoreDoc hit : hits) {
		    int docId = hit.doc;
		    Document d;
		    try {
			reader = DirectoryReader.open(index);
			searcher = new IndexSearcher(reader);
			d = searcher.doc(docId);
			int indexId = Integer.parseInt(d.get("id"));
			if (ids.contains(indexId)) {
			    entries.add(new IndexEntry(indexId, d.get("title")));
			}
		    } catch (IOException ex) {
			java.util.logging.Logger.getLogger(
				Search.class.getName()).log(Level.SEVERE, null,
					ex);
		    }
		}
	    }
	} catch (IOException ex) {
	    Logger.getLogger(Search.class.getName())
	    .log(Level.SEVERE, null, ex);
	}
	return entries;
    }

    Handler<CyclonSample> handlerCyclonSample = new Handler<CyclonSample>() {
	@Override
	public void handle(CyclonSample event) {
	}
    };
    // -------------------------------------------------------------------
    Handler<AddIndexText> handlerAddIndexText = new Handler<AddIndexText>() {
	@Override
	public void handle(AddIndexText event) {

	    synchronized (sync) {

		Insert.Request rq = new Insert.Request(self, null, event.getText());

		int selfPid = getPartitionFor(self);
		int eventPid = getPartitionFor(event.getText());

		logger.log(selfPid + " -> " + eventPid);

		if (selfPid != eventPid) {
		    Address route = routes.get(eventPid);

		    if (route != null) {
			logger.log("R :: Adding index entry: "
				+ event.getText() + "; routing request to "
				+ route + "[" + eventPid + "]");
			rq.setDestination(route);
		    } else {
			// What do we do when we have no one to route to?
			// Send it to the leader and hope he can route?
			logger.log("RL :: Adding index entry: "
				+ event.getText()
				+ "; routing request through leader " + leader
				+ "[" + selfPid + "]");
			rq.setDestination(getRandomNeighbour());
		    }
		} else {
		    logger.log("L :: Adding index entry: " + event.getText()
			    + "; sending request to leader " + leader);
		    rq.setDestination(leader);
		}

		requests.put(rq.getId(), rq);
		if (rq.getDestination() != null) {
		    if (leader != null) {
			trigger(rq, networkPort);
		    }
		} else {
		    logger.log("ERROR: something went wrong adding "
			    + event.getText());
		    //throw new IllegalStateException(
		    //	    "ERROR: something went wrong adding "
		    //		    + event.getText());
		}
	    }
	}
    };
    Handler<TManSample> handlerTManSample = new Handler<TManSample>() {
	@Override
	public void handle(TManSample event) {
	    // Naively accept
	    // everything.
	    synchronized (sync) {
		leader = event.getLeader();
		allNeighbours.clear();
		allNeighbours.addAll(event.getAllNeighbours());
		gradientAbove = event.getHigherNeighbours();
		gradientBelow = event.getLowerNeighbours();
		routes = event.getRoutes();
	    }
	}
    };
    Handler<SearchSchedule> handlerSchedule = new Handler<SearchSchedule>() {
	@Override
	public void handle(SearchSchedule event) {

	    synchronized (sync) {
		for (Address address : allNeighbours) {
		    //trigger(new Push.Offer(self, address, maxIndexEntry), networkPort);
		    trigger(new Push.Request(self, address, maxIndexEntry), networkPort);
		}

		if (leader != null) {
		    for (Insert.Request rq : requests.values()) {
			logger.log("Trying to insert " + rq.getTitle()
				+ " again...");
			rq.setDestination(leader);
			trigger(rq, networkPort);
		    }
		}
	    }
	}
    };
    Handler<SearchTimeout> handlerSearchTimeout = new Handler<SearchTimeout>() {
	@Override
	public void handle(SearchTimeout e) {
	    synchronized (sync) {
		SearchRequestInfo sri = pendingSearches.get(e.getId());
		if (sri != null) {
		    String html = searchPageHtml(sri.getAllResults());
		    WebResponse r = new WebResponse(html, sri.getWeb(), 1, 1);
		    trigger(r, webPort);
		    pendingSearches.remove(e.getId());
		}
	    }
	}
    };

    Handler<Push.Request> handlerPushRequest = new Handler<Push.Request>() {
	@Override
	public void handle(Push.Request event) {
	    synchronized (sync) {
		if(event.getVersion() < maxIndexEntry) {
		    trigger(new Push.Offer(event, maxIndexEntry), networkPort);
		}
	    }
	}
    };

    Handler<Push.Offer> handlerPushOffer = new Handler<Push.Offer>() {
	@Override
	public void handle(Offer event) {
	    synchronized (sync) {
		Collection<Integer> missingIds = getMissingIds(event
			.getEntryId());
		if (!missingIds.isEmpty()) {
		    trigger(new Push.Accept(event, missingIds), networkPort);
		}
	    }
	}
    };

    Handler<Push.Accept> handlerPushAccept = new Handler<Push.Accept>() {
	@Override
	public void handle(Accept event) {

	    synchronized (sync) {
		LinkedList<Integer> missingIds = new LinkedList<>(
			event.getMissingIds());
		List<IndexEntry> entries = getEntries(missingIds);
		if (!entries.isEmpty()) {
		    trigger(new Push.Payload(event, entries), networkPort);
		}

		String id = event.getId();
		// Protection for if the leader goes down.
		Insert.Request request = waiting.get(id);
		if (request != null
			&& missingIds.contains(request.getEntryId())) {
		    // If this doesn't happen, the node that requested the
		    // insert will keep
		    // petitioning the leader.
		    trigger(new Insert.Response(request, true), networkPort);
		    // Remove this event from the queue so we don't
		    // send another response.
		    waiting.remove(id);
		    knownEntries.add(event.getId());
		}
	    }
	}
    };
    Handler<Push.Payload> handlerPushPayload = new Handler<Push.Payload>() {
	@Override
	public void handle(Payload event) {
	    // Make sure the entries are sorted
	    // logger.log("Updating to " + (maxIndexEntry +
	    // event.getEntries().size()));
	    synchronized (sync) {
		boolean log = false;
		for (IndexEntry e : event.getEntries()) {
		    if (addEntry(e.getText(), e.getIndexId())) {
			log = true;
		    }
		}
		if (log) {
		    logger.log("I now have " + existingIds.size()
			    + " entries in the index");
		}
	    }
	}
    };

    private int getPartitionFor(String text) {
	return Math.abs(text.hashCode() % TManConfiguration.PARTITION_COUNT);
    }

    private int getPartitionFor(Address peer) {
	return peer.getId() % TManConfiguration.PARTITION_COUNT;
    }

    private Address getRandomNeighbour()
    {
	if(!allNeighbours.isEmpty()) {
	    return allNeighbours.get(r.nextInt(allNeighbours.size()));
	} else {
	    return leader;
	}

    }

    Handler<Insert.Request> handlerInsertRequest = new Handler<Insert.Request>() {
	@Override
	public void handle(Insert.Request event) {

	    synchronized (sync) {

		if(knownEntries.contains(event.getId()))
		{
		    return;
		}

		int selfPid = getPartitionFor(self);
		int eventPid = getPartitionFor(event.getTitle());
		int sourcePid = getPartitionFor(event.getSource());

		// Message routing, this message got delivered to the wrong
		// partition.
		if (selfPid != eventPid) {

		    Address route = routes.get(eventPid);
		    // If we can route the message, let's do so.
		    if (route != null) {
			event.setDestination(route);
		    } else {
			// Otherwise, try to route the message again through another neighbour
			if(event.hop()) {
			    event.setDestination(getRandomNeighbour());
			}
		    }
		    trigger(event, networkPort);
		    return;
		}

		// If we were routed this message without being the leader,
		// pass it on to the leader.
		if (leader != self) {
		    if (leader != null) {
			event.setDestination(leader);
			trigger(event, networkPort);
		    }
		    return;
		}

		// If we're already handling this message, ignore it.
		if (!waiting.containsKey(event.getId())) {
		    event.setEntryId(maxIndexEntry + 1);

		    if (!addEntry(event.getTitle(), event.getEntryId())) {
			trigger(new Insert.Response(event, false), networkPort);
			return;
		    }

		    logger.log(event.getSource() + "[" + sourcePid
			    + "] requested to add " + event.getTitle()
			    + " | assigned " + event.getEntryId());
		    waiting.put(event.getId(), event);
		    // Push the change downwards in the gradient.
		    for (Address address : gradientBelow) {
			trigger(new Push.Offer(self, address, event.getEntryId(),
				event.getId()), networkPort);
		    }
		}


	    }
	}
    };
    Handler<Insert.Response> handlerInsertResponse = new Handler<Insert.Response>() {
	@Override
	public void handle(Insert.Response event) {
	    synchronized (sync) {
		Insert.Request request = requests.get(event.getRequestId());
		if (request == null) {
		    return;
		}
		if (event.isSuccess()) {
		    requests.remove(event.getRequestId());
		    logger.log("Finished adding an entry: "
			    + event.getEntryId() + " " + request.getTitle());
		} else {
		    logger.log("Failed inserting " + request.getTitle());
		}
	    }
	}
    };
    Handler<Find.Request> handlerFindRequest = new Handler<Find.Request>() {
	@Override
	public void handle(Find.Request event) {
	    synchronized (sync) {
		logger.log("Received search request for " + event.getQuery());
		try {
		    Collection<IndexEntry> result = find(event.getQuery());
		    Find.Response response = new Find.Response(event, result);
		    trigger(response, networkPort);
		} catch (Exception e) {
		    // If anything
		    // goes wrong,
		    // just whistle
		    // and go on as
		    // usual
		    e.printStackTrace();
		}
	    }
	}
    };
    Handler<Find.Response> handlerFindResponse = new Handler<Find.Response>() {
	@Override
	public void handle(Find.Response event) {
	    synchronized (sync) {
		logger.log("Got response from " + event.getSource().getId());
		UUID id = event.getId();
		SearchRequestInfo sfi = pendingSearches.get(id);
		if (sfi == null) {
		    return;
		}

		sfi.addResult(event.getSource(), event.getResult());
		if (sfi.receivedAll()) {
		    String html = searchPageHtml(sfi.getAllResults());
		    WebResponse r = new WebResponse(html, sfi.getWeb(), 1, 1);
		    trigger(r, webPort);
		    pendingSearches.remove(id);
		}
	    }
	}
    };
}