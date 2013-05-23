package search.system.peer.search;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import se.sics.kompics.address.Address;
import se.sics.kompics.web.WebRequest;

public class SearchRequestInfo {
	private final WebRequest web;
	private final Map<Address, Collection<IndexEntry>> results;
	private final Set<Address> pending;

	public SearchRequestInfo(WebRequest req)
	{
		web = req;
		results = new ConcurrentHashMap<>();
		pending = new HashSet<>();
	}

	public synchronized void addPartitionRequestId(Address address)
	{
		results.put(address, new LinkedList<IndexEntry>());
		pending.add(address);
	}

	public synchronized void addResult(Address address, Collection<IndexEntry> moar)
	{
		if(results.containsKey(address))
		{
			results.put(address, moar);
			pending.remove(address);
		}
	}

	public synchronized boolean receivedAll()
	{
		return pending.isEmpty();
	}

	public synchronized Collection<IndexEntry> getAllResults() {
		LinkedList<IndexEntry> result = new LinkedList<>();
		for(Collection<IndexEntry> entries : results.values())
		{
			if(entries!=null)
			{
				result.addAll(entries);
			}
		}
		return result;
	}

	public WebRequest getWeb() {
		return web;
	}
}
