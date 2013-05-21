package search.system.peer.search.messages;

import java.util.UUID;

import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;

public class Insert {
	public static class Request extends Message
	{
        private int entryId = 0;
		private final String title;
		private final UUID id;
		public Request(Address source, Address destination, String title)
		{
			super(source, destination);
			id = UUID.randomUUID();
			this.title = title;
		}

		public String getTitle() {
			return title;
		}
        
		public String getId() {
			return getSource() + "|" + id;
		}

        public int getEntryId() {
            return entryId;
        }

        public void setEntryId(int entryId) {
            this.entryId = entryId;
        }
        
        
	}

	public static class Response extends Message
	{
        private final int entryId;
		private final String requestId;
		private final boolean success;
		public Response(Request request, boolean success)
		{
			super(request.getDestination(), request.getSource());
            this.entryId = request.getEntryId();
			this.requestId = request.getId();
			this.success = success;
		}

		public String getRequestId() {
			return requestId;
		}

		public boolean isSuccess() {
			return success;
		}

        public int getEntryId() {
            return entryId;
        }
	}
}
