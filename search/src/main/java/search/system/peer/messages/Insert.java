package search.system.peer.messages;

import java.util.UUID;

import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;

public class Insert {
	public static class Request extends Message
	{
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

		public UUID getId() {
			return id;
		}
	}

	public static class Response extends Message
	{
		private final UUID id;
		private final boolean success;
		public Response(Request request, boolean success)
		{
			super(request.getDestination(), request.getSource());
			id = request.id;		
			this.success = success;
		}

		public UUID getId() {
			return id;
		}

		public boolean isSuccess() {
			return success;
		}
	}
}
