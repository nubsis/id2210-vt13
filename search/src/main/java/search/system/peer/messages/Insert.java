package search.system.peer.messages;

import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;
import search.system.peer.search.IndexEntry;

public class Insert {
	public static class Request extends Message
	{
		private final String title;
		public Request(Address source, Address destination, String title)
		{
			super(source, destination);
			this.title = title;
		}

		public String getTitle() {
			return title;
		}
	}
	public static class Accept extends Message
	{
		private final IndexEntry entry;
		public Accept(Address source, Address destination, IndexEntry entry)
		{
			super(source, destination);
			this.entry = entry;			
		}

		public IndexEntry getEntry() {
			return entry;
		}
	}
}
