package tman.system.peer.tman.election;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import se.sics.kompics.address.Address;
import tman.system.peer.tman.TMan;
import tman.system.peer.tman.TManHandler;
import tman.system.peer.tman.messages.LeaderPing;
import tman.system.peer.tman.messages.LeaderPing.LeaderStatus;
import tman.system.peer.tman.messages.LeaderPing.Request;
import tman.system.peer.tman.messages.LeaderPing.Response;

public class LeaderPingHandler {

	private RequestHandler requestHandler;
	private ResponseHandler responseHandler;
	TMan tman;

	public LeaderPingHandler(TMan tman) {
		setRequestHandler(new RequestHandler(tman));
		setResponseHandler(new ResponseHandler(tman));
	}

	public ResponseHandler getResponseHandler() {
		return responseHandler;
	}

	public void setResponseHandler(ResponseHandler responseHandler) {
		this.responseHandler = responseHandler;
	}

	public RequestHandler getRequestHandler() {
		return requestHandler;
	}

	public void setRequestHandler(RequestHandler requestHandler) {
		this.requestHandler = requestHandler;
	}

	public class RequestHandler extends TManHandler<LeaderPing.Request> {

		private final Map<UUID, Request> waitingRequests;

		public RequestHandler(TMan tman) {
			super(tman);
			waitingRequests = new HashMap<>();
		}

		@Override
		public synchronized void handle(Request event) {

			// TODO: Implement more logic to determine if we are actually the leader
			// Am I the leader?
			if(tman.getGradient().getUp().isEmpty())
			{
				// If I have no up nodes, and have been elected, then I am the leader.
				if(tman.isElected())
				{
					LeaderPing.Response r = new LeaderPing.Response(
							event,
							tman.getSelf(),
							LeaderPing.LeaderStatus.ONLINE);
					tman.send(r);
				}
				else
				{
					// If we have no greater nodes but have not been elected, then we don't know the status of the leader.
					LeaderPing.Response r = new LeaderPing.Response(event, tman.getLeader(), LeaderStatus.UNKNOWN);
				}
			} else
			{	
				// Broadcast the request to all nodes above in the gradient
				for(Address a : tman.getGradient().getUp())
				{
					LeaderPing.Request r = new LeaderPing.Request(event.getDestination(), a);
					getResponseHandler().register(r.getId());
					tman.send(r);
				}
			}
		}

		/** Get notified about a response coming in */
		public synchronized void onResponse(Response event) {
			Request r = waitingRequests.get(event.getId());

			// TODO: Actually handle the response
			// What do we do with the information?
		}
	}

	public class ResponseHandler extends TManHandler<LeaderPing.Response>{

		private final Set<UUID> waiting;

		public ResponseHandler(TMan tman) {
			super(tman);
			waiting = new HashSet<>();
		}

		@Override
		public synchronized void handle(LeaderPing.Response event) {
			if(waiting.contains(event.getId())) {
				getRequestHandler().onResponse(event);
				waiting.remove(event.getId());
			}
		}

		public synchronized void register(UUID id) {
			waiting.add(id);
		}
	}
}
