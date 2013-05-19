package search.system.peer.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

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
import se.sics.kompics.timer.Timer;
import se.sics.kompics.web.Web;
import se.sics.kompics.web.WebRequest;
import se.sics.kompics.web.WebResponse;
import search.simulator.snapshot.Snapshot;
import search.system.peer.AddIndexText;
import search.system.peer.IndexPort;
import search.system.peer.search.messages.Insert;
import search.system.peer.search.messages.Insert.Request;
import search.system.peer.search.messages.Push;
import search.system.peer.search.messages.Push.Accept;
import search.system.peer.search.messages.Push.Offer;
import search.system.peer.search.messages.Push.Payload;
import tman.system.peer.tman.TManSample;
import tman.system.peer.tman.TManSamplePort;

import common.configuration.SearchConfiguration;

import cyclon.system.peer.cyclon.CyclonSample;
import cyclon.system.peer.cyclon.CyclonSamplePort;
import cyclon.system.peer.cyclon.PeerDescriptor;

/**
 * Should have some comments here.
 *
 * @author jdowling
 */
public final class Search extends ComponentDefinition {

	private common.Logger.Instance logger;

	private final Positive<IndexPort> indexPort = positive(IndexPort.class);
	private final Positive<Network> networkPort = positive(Network.class);
	private final Positive<Timer> timerPort = positive(Timer.class);
	private final Negative<Web> webPort = negative(Web.class);
	private final Positive<CyclonSamplePort> cyclonSamplePort = positive(CyclonSamplePort.class);
	private final Positive<TManSamplePort> tmanPort = positive(TManSamplePort.class);
	private final ArrayList<Address> neighbours = new ArrayList<Address>();

	private Address self;
	private Address leader;
	private Collection<Address> gradientAbove;
	private Collection<Address> gradientBelow;

	private final Object[] sync = new Object[]{};

	/** The requests awaiting confirmation from the leader */
	private final Map<UUID, Insert.Request> requests = new HashMap<>(8);
	/** The requests awaiting confirmation from the leaders neighbours */
	private final Map<UUID, Insert.Request> waiting = new HashMap<>(8);


	private SearchConfiguration searchConfiguration;
	// Apache Lucene used for searching
	StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_42);
	Directory index = new RAMDirectory();
	IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_42, analyzer);
	int lastMissingIndexEntry = 0;
	int lastPushIndex = 0;
	int maxIndexEntry = 0;
	Random random;
	// When you partition the index you need to find new nodes
	// This is a routing table maintaining a list of pairs in each partition.
	private Map<Integer, List<PeerDescriptor>> routingTable;
	Comparator<PeerDescriptor> peerAgeComparator = new Comparator<PeerDescriptor>() {
		@Override
		public int compare(PeerDescriptor t, PeerDescriptor t1) {
			if (t.getAge() > t1.getAge()) {
				return 1;
			} else {
				return -1;
			}
		}
	};

	//-------------------------------------------------------------------	
	public Search() {

		subscribe(handleInit, control);
		subscribe(handleWebRequest, webPort);
		subscribe(handleCyclonSample, cyclonSamplePort);
		subscribe(handleAddIndexText, indexPort);
		subscribe(handleUpdateIndexTimeout, timerPort);
		subscribe(handleMissingIndexEntriesRequest, networkPort);
		subscribe(handleMissingIndexEntriesResponse, networkPort);
		subscribe(handleTManSample, tmanPort);

		subscribe(handleInsertRequest, networkPort);
		subscribe(handleInsertResponse, networkPort);
		subscribe(handlePushAccept, networkPort);
		subscribe(handlePushOffer, networkPort);
		subscribe(handlePushPayload, networkPort);
	}
	//-------------------------------------------------------------------	
	Handler<SearchInit> handleInit = new Handler<SearchInit>() {
		@Override
		public void handle(SearchInit init) {
			self = init.getSelf();
			logger = new common.Logger.Instance("Search." + self);
			searchConfiguration = init.getConfiguration();
			routingTable = new HashMap<Integer, List<PeerDescriptor>>(searchConfiguration.getNumPartitions());
			random = new Random(init.getConfiguration().getSeed());
			long period = searchConfiguration.getPeriod();
			SchedulePeriodicTimeout rst = new SchedulePeriodicTimeout(period, period);
			rst.setTimeoutEvent(new UpdateIndexTimeout(rst));
			trigger(rst, timerPort);

			// TODO super ugly workaround...
			IndexWriter writer;
			try {
				writer = new IndexWriter(index, config);
				writer.commit();
				writer.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	};
	Handler<WebRequest> handleWebRequest = new Handler<WebRequest>() {
		@Override
		public void handle(WebRequest event) {

			String[] args = event.getTarget().split("-");

			logger.log("Handling Webpage Request");

			WebResponse response;
			if (args[0].compareToIgnoreCase("search") == 0) {
				response = new WebResponse(searchPageHtml(args[1]), event, 1, 1);
			} else if (args[0].compareToIgnoreCase("add") == 0) {
				response = new WebResponse(addEntryHtml(args[1], Integer.parseInt(args[2])), event, 1, 1);
			} else {
				response = new WebResponse(searchPageHtml(event
								.getTarget()), event, 1, 1);
			}
			trigger(response, webPort);
		}
	};

	private String searchPageHtml(String title) {
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
		try {
			query(sb, title);
		} catch (ParseException ex) {
			java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
			sb.append(ex.getMessage());
		} catch (IOException ex) {
			java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
			sb.append(ex.getMessage());
		}
		sb.append("</body></html>");
		return sb.toString();
	}

	private String addEntryHtml(String title, int id) {
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
			addEntry(title, id);
			sb.append("Entry: ").append(title).append(" - ").append(id);
		} catch (IOException ex) {
			sb.append(ex.getMessage());
			java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
		}
		sb.append("</body></html>");
		return sb.toString();
	}

	private void addEntry(String title, int id) throws IOException {
		IndexWriter w = new IndexWriter(index, config);
		Document doc = new Document();
		doc.add(new TextField("title", title, Field.Store.YES));
		// Use a NumericRangeQuery to find missing index entries:
		//    http://lucene.apache.org/core/4_2_0/core/org/apache/lucene/search/NumericRangeQuery.html
		// http://lucene.apache.org/core/4_2_0/core/org/apache/lucene/document/IntField.html
		doc.add(new IntField("id", id, Field.Store.YES));
		w.addDocument(doc);
		w.close();
		Snapshot.incNumIndexEntries(self);
		maxIndexEntry++;
	}

	private String query(StringBuilder sb, String querystr) throws ParseException, IOException {

		// the "title" arg specifies the default field to use when no field is explicitly specified in the query.
		Query q = new QueryParser(Version.LUCENE_42, "title", analyzer).parse(querystr);
		IndexSearcher searcher = null;
		IndexReader reader = null;
		try {
			reader = DirectoryReader.open(index);
			searcher = new IndexSearcher(reader);
		} catch (IOException ex) {
			java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
			System.exit(-1);
		}

		int hitsPerPage = 10;
		TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage, true);
		searcher.search(q, collector);
		ScoreDoc[] hits = collector.topDocs().scoreDocs;

		// display results
		sb.append("Found ").append(hits.length).append(" entries.<ul>");
		for (int i = 0; i < hits.length; ++i) {
			int docId = hits[i].doc;
			Document d = searcher.doc(docId);
			sb.append("<li>").append(i + 1).append(". ").append(d.get("id")).append("\t").append(d.get("title")).append("</li>");
		}
		sb.append("</ul>");

		// reader can only be closed when there
		// is no need to access the documents any more.
		reader.close();
		return sb.toString();
	}
	Handler<UpdateIndexTimeout> handleUpdateIndexTimeout = new Handler<UpdateIndexTimeout>() {
		@Override
		public void handle(UpdateIndexTimeout event) {

			// Check if we have something new to push
			if(lastMissingIndexEntry < maxIndexEntry)
			{
				// If so, push to all nodes below in the gradient.
				UUID id = UUID.randomUUID();
				if(gradientBelow != null)
				{
					for(Address a : gradientBelow)
					{
						trigger(new Push.Offer(self, a, maxIndexEntry-1, id), networkPort);
					}
				}
				// If so, push to all nodes below in the gradient.
				if(gradientAbove!= null)
				{
					for(Address a : gradientAbove)
					{
						trigger(new Push.Offer(self, a, maxIndexEntry-1, id), networkPort);
					}
				}
			}

			if(leader!=null) {
				for(Insert.Request rq : requests.values())
				{
					logger.log("Trying to insert " + rq.getTitle() + " again...");
					rq.setDestination(leader);
					trigger(rq, networkPort);
				}
			}

			/*
			// pick a random neighbour to ask for index updates from. 
			// You can change this policy if you want to.
			// Maybe a gradient neighbour who is closer to the leader?
			if (neighbours.isEmpty()) {
				return;
			}
			Address dest = neighbours.get(random.nextInt(neighbours.size()));

			// find all missing index entries (ranges) between lastMissingIndexValue
			// and the maxIndexValue
			List<Range> missingIndexEntries = getMissingRanges();

			// Send a MissingIndexEntries.Request for the missing index entries to dest
			MissingIndexEntries.Request req = new MissingIndexEntries.Request(self, dest,
							missingIndexEntries);
			trigger(req, networkPort);
			 */
		}		
	};

	ScoreDoc[] getExistingDocsInRange(int min, int max, IndexReader reader,
					IndexSearcher searcher) throws IOException {
		reader = DirectoryReader.open(index);
		searcher = new IndexSearcher(reader);
		// The line below is dangerous - we should bound the number of entries returned
		// so that it doesn't consume too much memory.
		int hitsPerPage = max - min > 0 ? max - min : 1;
		Query query = NumericRangeQuery.newIntRange("id", min, max, true, true);
		TopDocs topDocs = searcher.search(query, hitsPerPage, new Sort(new SortField("id", Type.INT)));
		return topDocs.scoreDocs;
	}

	List<Range> getMissingRanges() {
		List<Range> res = new ArrayList<>();
		IndexReader reader = null;
		IndexSearcher searcher = null;
		try {
			reader = DirectoryReader.open(index);
			searcher = new IndexSearcher(reader);
			ScoreDoc[] hits = getExistingDocsInRange(lastMissingIndexEntry, maxIndexEntry,
							reader, searcher);
			if (hits != null) {
				int startRange = lastMissingIndexEntry;
				// This should terminate by finding the last entry at position maxIndexValue
				for (int id = lastMissingIndexEntry + 1; id <= maxIndexEntry; id++) {
					// We can skip the for-loop if the hits are returned in order, with lowest id first
					boolean found = false;
					for (int i = 0; i < hits.length; ++i) {
						int docId = hits[i].doc;
						Document d;
						try {
							d = searcher.doc(docId);
							int indexId = Integer.parseInt(d.get("id"));
							if (id == indexId) {
								found = true;
							}
						} catch (IOException ex) {
							java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
						}
					}
					if (found) {
						if (id != startRange) {
							res.add(new Range(startRange, id - 1));
						}
						startRange = id == Integer.MAX_VALUE ? Integer.MAX_VALUE : id + 1;
					}
				}
				// Add all entries > maxIndexEntry as a range of interest.
				res.add(new Range(maxIndexEntry + 1, Integer.MAX_VALUE));

			}
		} catch (IOException ex) {
			java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException ex) {
					java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
				}
			}

		}


		return res;
	}

	List<IndexEntry> getMissingIndexEntries(Range range) {
		List<IndexEntry> res = new ArrayList<IndexEntry>();
		IndexSearcher searcher = null;
		IndexReader reader = null;
		try {
			reader = DirectoryReader.open(index);
			searcher = new IndexSearcher(reader);
			ScoreDoc[] hits = getExistingDocsInRange(range.getLower(),
							range.getUpper(), reader, searcher);
			if (hits != null) {
				for (int i = 0; i < hits.length; ++i) {
					int docId = hits[i].doc;
					Document d;
					try {
						d = searcher.doc(docId);
						int indexId = Integer.parseInt(d.get("id"));
						String text = d.get("title");
						res.add(new IndexEntry(indexId, text));
					} catch (IOException ex) {
						java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
					}
				}
			}
		} catch (IOException ex) {
			java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException ex) {
					java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
				}
			}

		}

		return res;
	}

	/**
	 * Called by null null     {@link #handleMissingIndexEntriesRequest(MissingIndexEntries.Request) 
	 * handleMissingIndexEntriesRequest}
	 *
	 * @return List of IndexEntries at this node great than max
	 */
	List<IndexEntry> getEntriesGreaterThan(int max) {
		List<IndexEntry> res = new ArrayList<IndexEntry>();

		IndexSearcher searcher = null;
		IndexReader reader = null;
		try {
			ScoreDoc[] hits = getExistingDocsInRange(max, maxIndexEntry,
							reader, searcher);

			if (hits != null) {
				for (int i = 0; i < hits.length; ++i) {
					int docId = hits[i].doc;
					Document d;
					try {
						reader = DirectoryReader.open(index);
						searcher = new IndexSearcher(reader);
						d = searcher.doc(docId);
						int indexId = Integer.parseInt(d.get("id"));
						String text = d.get("title");
						res.add(new IndexEntry(indexId, text));
					} catch (IOException ex) {
						java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
					}
				}
			}
		} catch (IOException ex) {
			java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException ex) {
					java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
				}
			}

		}
		return res;
	}
	Handler<MissingIndexEntries.Request> handleMissingIndexEntriesRequest = new Handler<MissingIndexEntries.Request>() {
		@Override
		public void handle(MissingIndexEntries.Request event) {

			List<IndexEntry> res = new ArrayList<IndexEntry>();
			for (Range r : event.getMissingRanges()) {
				res.addAll(getMissingIndexEntries(r));
			}

			// TODO send missing index entries back to requester
		}
	};
	Handler<MissingIndexEntries.Response> handleMissingIndexEntriesResponse = new Handler<MissingIndexEntries.Response>() {
		@Override
		public void handle(MissingIndexEntries.Response event) {
			// TODO merge the missing index entries in your lucene index 
		}
	};
	Handler<CyclonSample> handleCyclonSample = new Handler<CyclonSample>() {
		@Override
		public void handle(CyclonSample event) {

			// receive a new list of neighbours

			/*neighbours.clear();
			neighbours.addAll(event.getSample());

			// update routing tables
			for (Address p : neighbours) {
				int partition = p.getId() % searchConfiguration.getNumPartitions();
				List<PeerDescriptor> nodes = routingTable.get(partition);
				if (nodes == null) {
					nodes = new ArrayList<>();
					routingTable.put(partition, nodes);
				}
				// Note - this might replace an existing entry in Lucene
				nodes.add(new PeerDescriptor(p));
				// keep the freshest descriptors in this partition
				Collections.sort(nodes, peerAgeComparator);
				List<PeerDescriptor> nodesToRemove = new ArrayList<PeerDescriptor>();
				for (int i = nodes.size(); i > searchConfiguration.getMaxNumRoutingEntries(); i--) {
					nodesToRemove.add(nodes.get(i - 1));
				}
				nodes.removeAll(nodesToRemove);
			}*/
		}
	};
	//-------------------------------------------------------------------	
	Handler<AddIndexText> handleAddIndexText = new Handler<AddIndexText>() {
		@Override
		public void handle(AddIndexText event) {

			logger.log("Adding index entry: " + event.getText() + "; " + leader);

			Insert.Request rq = new Insert.Request(self, leader, event.getText());
			requests.put(rq.getId(), rq);
			if(leader!=null) {
				trigger(rq, networkPort);
			}
		}
	};

	Handler<TManSample> handleTManSample = new Handler<TManSample>() {
		@Override
		public void handle(TManSample event) {
			// Naively accept everything.
			leader = event.getLeader();
			gradientAbove = event.getHigherNeighbours();
			gradientBelow = event.getLowerNeighbours();
		}
	};

	Handler<Push.Offer> handlePushOffer = new Handler<Push.Offer>()	{
		@Override
		public void handle(Offer event) {
			if(event.getMaxIndexNumber()<=maxIndexEntry) {
				return;
			}
			logger.log("Yes, I want to update from " + maxIndexEntry + " to " + event.getMaxIndexNumber());
			trigger(new Push.Accept(event, maxIndexEntry), networkPort);
		}
	};

	Handler<Push.Accept> handlePushAccept = new Handler<Push.Accept>(){
		@Override
		public void handle(Accept event) {

			// Double safeguard
			if(event.getMaxIndexNumber()>=maxIndexEntry) {
				return;
			}

			logger.log("Here, have " + event.getMaxIndexNumber() + "-" + (maxIndexEntry-1) + " (" + getEntriesGreaterThan(event.getMaxIndexNumber()).size()+")");

			/*StringBuilder sb = new StringBuilder();
			for(IndexEntry e : getEntriesGreaterThan(event.getMaxIndexNumber())) {
				sb.append("\n\t" + e.getText() + "(" + e.getIndexId() + ")");
			}
			logger.log(sb);
			 */


			trigger(new Push.Payload(event, getEntriesGreaterThan(event.getMaxIndexNumber())), networkPort);

			UUID id = event.getId();

			// Protection for if the leader goes down.
			Insert.Request rq = waiting.get(id);
			if(rq!=null) {
				// If this doesn't happen, the node that requested the insert
				// will keep petitioning the leader.
				trigger(new Insert.Response(rq, true), networkPort);
				// Remove this event from the queue so we don't send another response.
				waiting.remove(id);
			}

		}
	};

	Handler<Push.Payload> handlePushPayload = new Handler<Push.Payload>(){
		@Override
		public void handle(Payload event) {
			// Make sure the entries are sorted
			//logger.log("Updating to " + (maxIndexEntry + event.getEntries().size()));
			Collections.sort(event.getEntries(), new Comparator<IndexEntry>(){
				@Override
				public int compare(IndexEntry arg0, IndexEntry arg1) {
					return arg0.getIndexId() - arg1.getIndexId();
				}
			});

			StringBuilder sb = new StringBuilder();
			// Now, insert the entries in-order.
			sb.append("Adding:");
			for(IndexEntry e : event.getEntries())
			{

				// Abort if an entry is missing for some reason.
				if(e.getIndexId()<maxIndexEntry) {
					sb.append("\n\t-" + e.getText() + " (" + e.getIndexId() + ")");
					continue;
				}else
				{
					sb.append("\n\t+" + e.getText() + " (" + e.getIndexId() + ")");
				}

				try {
					addEntry(e.getText(), e.getIndexId());
				} catch (IOException e1) {
					// Error!
					logger.log(e1);
				}
			}
			logger.log(sb);
		}
	};

	Handler<Insert.Request> handleInsertRequest = new Handler<Insert.Request>(){
		@Override
		public void handle(Request event) {

			logger.log(event.getSource() + " requested to add " + event.getTitle());
			// If for some reason this node gets this request without being the leader,
			// just ignore it.
			if(leader != self) {
				return;
			}
			synchronized(sync){
				if(waiting.containsKey(event.getId())) {
					return;
				}

				int m = maxIndexEntry;

				try {
					addEntry(event.getTitle(), m);
					for(Address a : gradientBelow) {
						trigger(new Push.Offer(self, event.getSource(), m, event.getId()), networkPort);
					}
					waiting.put(event.getId(), event);
				} catch (IOException e) {
					java.util.logging.Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, e);
					throw new IllegalArgumentException(e.getMessage());
				}
			}
		}
	};

	Handler<Insert.Response> handleInsertResponse = new Handler<Insert.Response>(){
		@Override
		public void handle(Insert.Response event) {
			Request q = requests.get(event.getId());
			if(event.isSuccess()) {
				requests.remove(event.getId());
				logger.log("Finished adding " + q.getTitle());
			} else {
				logger.log("Failed inserting " + q.getTitle());
			}
		}
	};

	private void updateIndexPointers(int id) {
		if (id == lastMissingIndexEntry + 1) {
			lastMissingIndexEntry++;
		}
		if (id > maxIndexEntry) {
			maxIndexEntry = id;
		}
	}
}