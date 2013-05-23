package search.system.peer.search;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import se.sics.kompics.address.Address;
import se.sics.kompics.web.WebRequest;

public class SearchRequestInfo {
	private final WebRequest web;
	private final Map<Address, Collection<IndexEntry>> results;

	public SearchRequestInfo(WebRequest req)
	{
		web = req;
		results = new ConcurrentHashMap<>();
	}

	public void addPartitionRequestId(Address address)
	{
		results.put(address, null);
	}

	public void addResult(Address address, Collection<IndexEntry> moar)
	{
		if(results.containsKey(address))
		{
			results.put(address, moar);
		}
	}

	public boolean receivedAll()
	{
		return !results.containsValue(null);
	}

	public Collection<IndexEntry> getAllResults() {
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
