package tman.system.peer.tman.election;

import se.sics.kompics.address.Address;
import tman.system.peer.tman.Gradient;
import tman.system.peer.tman.TMan;
import tman.system.peer.tman.TManHandler;
import tman.system.peer.tman.messages.ElectionRequest;
import tman.system.peer.tman.messages.LeaderAnnounceMessage;

public class ElectionProtocol
{
	public static class ElectionRequestHandler extends TManHandler<ElectionRequest> {
		public ElectionRequestHandler(TMan tman) {
			super(tman);
		}

		@Override
		public void handle(ElectionRequest event) {
			Gradient g = tman.getGradient();
			for (Address a : g.getUp()) {
				//tman.send(new LeaderPing.Request(event.getDestination(), a));
			}
		}
	}

	public static class LeaderAnnounceHandler extends TManHandler<LeaderAnnounceMessage>
	{
		public LeaderAnnounceHandler(TMan tman) {
			super(tman);
		}

		@Override
		public void handle(LeaderAnnounceMessage event) {
		}
	}

}
