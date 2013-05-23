package search.system.peer.search.messages;

import java.util.Collection;
import java.util.UUID;

import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;
import search.system.peer.search.IndexEntry;

public class Find {
	public static class Request extends Message
	{
		private final String query;
		private final UUID id;
		public Request(Address source, Address destination, String query, UUID id)
		{
			super(source, destination);
			this.query = query;
			this.id = id;
		}

		public String getQuery() {
			return query;
		}

		public UUID getId() {
			return id;
		}
	}

	public static class Response extends Message
	{
		private final Collection<IndexEntry> result;
		private final String query;
		private final UUID id;

		public Response(Request request, Collection<IndexEntry> results)
		{
			super(request.getDestination(), request.getSource());
			query = request.query;
			id = request.id;
			result = results;
		}

		public Collection<IndexEntry> getResult() {
			return result;
		}

		public String getQuery() {
			return query;
		}

		public UUID getId() {
			return id;
		}
	}

}
