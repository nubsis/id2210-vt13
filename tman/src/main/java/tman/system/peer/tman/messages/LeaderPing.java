package tman.system.peer.tman.messages;

import se.sics.kompics.address.Address;

public class LeaderPing {

	public static enum LeaderStatus
	{
		ONLINE, OFFLINE, UNKNOWN
	}

	public static class Request extends TManMessage
	{
		private static final long serialVersionUID = -3427699928312735280L;

		public Request(Address src, Address dst) {
			super(src, dst);
		}
	}

	public static class Response extends TManMessage
	{
		private static final long serialVersionUID = -1872458142914438176L;
		private final Address leader;
		private final LeaderStatus status;

		public Response(Request request, Address leader, LeaderStatus status)
		{
			super(request.getDestination(), request.getSource(), request.getId());
			this.leader = leader;
			this.status = status;
		}

		public Address getLeader() {
			return leader;
		}

		public LeaderStatus getStatus() {
			return status;
		}
	}



}
