package tman.system.peer.tman.election;

import se.sics.kompics.Handler;
import se.sics.kompics.address.Address;
import tman.system.peer.tman.Gradient;
import tman.system.peer.tman.TMan;
import tman.system.peer.tman.TManHandler;
import tman.system.peer.tman.messages.ElectionRequest;
import tman.system.peer.tman.messages.LeaderAnnounceMessage;

public class ElectionProtocol {

    private final Handler<ElectionRequest> handlerElectionRequest = new Handler<ElectionRequest>() {
        @Override
        public void handle(ElectionRequest e) {
            System.err.println("handlerElectionRequest");
        }
    };

    public Handler<ElectionRequest> getHandlerElectionRequest() {
        return handlerElectionRequest;
    }
    /*
     public static class LeaderAnnounceHandler extends TManHandler<LeaderAnnounceMessage>
     {
     public LeaderAnnounceHandler(TMan tman) {
     super(tman);
     }

     @Override
     public void handle(LeaderAnnounceMessage event) {
     }
     }
     */
}
