package search.system.peer.search.messages;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;
import search.system.peer.search.IndexEntry;

public class Push {

    public static class Offer extends Message {

        private final int entryId;
        private final String id;

        public Offer(Address src, Address dst, int entryId, String id) {
            super(src, dst);
            this.entryId = entryId;
            this.id = id;
        }

        public Offer(Address src, Address dst, int entryId) {
            super(src, dst);
            this.entryId = entryId;
            this.id = src + "|" + UUID.randomUUID();
        }

        public int getEntryId() {
            return entryId;
        }

        public String getId() {
            return id;
        }
    }

    public static class Accept extends Message {

        private final Collection<Integer> missingIds;
        private final String id;

        public Accept(Offer offer, Collection<Integer> missingIds) {
            super(offer.getDestination(), offer.getSource());
            id = offer.id;
            this.missingIds = missingIds;
        }

        public Collection<Integer> getMissingIds() {
            return missingIds;
        }

        public String getId() {
            return id;
        }
    }

    public static class Payload extends Message {

        private final List<IndexEntry> entries;
        private final String id;

        public Payload(Accept accept, List<IndexEntry> entries) {
            super(accept.getDestination(), accept.getSource());
            id = accept.id;
            this.entries = entries;
        }

        public List<IndexEntry> getEntries() {
            return entries;
        }

        public String getId() {
            return id;
        }
    }
}
