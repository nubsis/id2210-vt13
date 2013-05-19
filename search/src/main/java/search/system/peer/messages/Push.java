package search.system.peer.messages;

import java.util.List;

import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;
import search.system.peer.search.IndexEntry;

public class Push {
	public static class Offer extends Message
	{
		private final int maxIndexNumber;
		public Offer(Address src, Address dst, int maxIndexNumber)
		{
			super(src, dst);
			this.maxIndexNumber = maxIndexNumber;
		}

		public int getMaxIndexNumber() {
			return maxIndexNumber;
		}
	}

	public static class Accept extends Message
	{
		private final int maxIndexNumber;
		public Accept(Address src, Address dst, int maxIndexNumber)
		{
			super(src, dst);
			this.maxIndexNumber = maxIndexNumber;
		}

		public int getMaxIndexNumber() {
			return maxIndexNumber;
		}
	}

	public static class Payload extends Message
	{
		private final List<IndexEntry> entries;

		public Payload(Address src, Address dst, List<IndexEntry> entries)
		{
			super(src, dst);
			this.entries = entries;
		}

		public List<IndexEntry> getEntries() {
			return entries;
		}
	}
}
