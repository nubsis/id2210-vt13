package search.system.peer.search.messages;

import java.util.List;
import java.util.UUID;

import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;
import search.system.peer.search.IndexEntry;

public class Push {
	public static class Offer extends Message
	{
		private final int maxIndexNumber;
		private final UUID id;
		public Offer(Address src, Address dst, int maxIndexNumber, UUID id)
		{
			super(src, dst);
			this.maxIndexNumber = maxIndexNumber;
			this.id = id;
		}

		public int getMaxIndexNumber() {
			return maxIndexNumber;
		}

		public UUID getId() {
			return id;
		}
	}

	public static class Accept extends Message
	{
		private final int maxIndexNumber;
		private final UUID id;

		public Accept(Offer offer, int maxIndexNumber)
		{
			super(offer.getDestination(), offer.getSource());
			id = offer.id;
			this.maxIndexNumber = maxIndexNumber;
		}

		public int getMaxIndexNumber() {
			return maxIndexNumber;
		}

		public UUID getId() {
			return id;
		}
	}

	public static class Payload extends Message
	{
		private final List<IndexEntry> entries;
		private final UUID id;

		public Payload(Accept accept, List<IndexEntry> entries)
		{
			super(accept.getDestination(), accept.getSource());
			id = accept.id;
			this.entries = entries;
		}

		public List<IndexEntry> getEntries() {
			return entries;
		}

		public UUID getId() {
			return id;
		}
	}
}
