package tman.system.peer.tman.election;

import java.util.Collection;
import java.util.HashMap;

import se.sics.kompics.Handler;
import se.sics.kompics.address.Address;
import tman.system.peer.tman.TMan;
import tman.system.peer.tman.messages.LeaderElection;

public class ElectionProtocol {

	class Election {

		/**
		 * Mapping of which neighbours have answered to our vote request
		 */
		private final HashMap<Address, Boolean> answers = new HashMap<>();

		/**
		 * Initiate an election.
		 * 
		 * Sends out a vote initialization request to a set of neighbours.
		 *
		 * @param neighbours The neighbours with whom to perform the election.
		 */
		synchronized public void start(Collection<Address> neighbours) {
			answers.clear(); // Could be stop()?
			if (neighbours.isEmpty()) {
				return;
			}

			tman.getLogger().log("starting an election: " + neighbours.size());
			for (Address a : neighbours) {
				answers.put(a, false);
				// let's ask our neighbours if they agree for me to be the leader
				tman.send(new LeaderElection.VoteRequest(tman.getSelf(), a));
			}
		}

		/**
		 * Clears the status of the current election.
		 */
		synchronized public void stop() {
			answers.clear();
		}

		synchronized public void answerReceived(Address address, boolean accepted) {
			if (answers.containsKey(address)) {
				tman.getLogger().log("answer received from " + address + " : " + accepted);
				answers.put(address, accepted);
			}
		}

		synchronized public boolean isElected() {
			return !answers.isEmpty() && !answers.values().contains(false);
		}

		synchronized public boolean isInProgress() {
			return !answers.isEmpty();
		}
	}
	private final Object sync = new Object();
	private final TMan tman;
	private final Election election = new Election();
	private Address leader = null;

	public Address getLeader() {
		synchronized (sync) {
			return leader;
		}
	}

	public Handler<LeaderElection.VoteRequest> getHandlerVoteRequest() {
		return handlerVoteRequest;
	}

	public Handler<LeaderElection.VoteResponse> getHandlerVoteResponse() {
		return handlerVoteResponse;
	}

	public Handler<LeaderElection.Announcement> getHandlerAnnouncement() {
		return handlerAnnouncement;
	}

	public ElectionProtocol(TMan tman) {
		this.tman = tman;
	}
	private final Handler<LeaderElection.VoteRequest> handlerVoteRequest = new Handler<LeaderElection.VoteRequest>() {
		@Override
		public void handle(LeaderElection.VoteRequest e) {
			synchronized (sync) {
				Address top = tman.getGradient().getHighest();
				Address self = tman.getSelf();
				int candidateId = e.getCandidate().getId();

				boolean accepted =
						self.getId() < candidateId
						&& (top == null || top.getId() <= candidateId);

				tman.send(new LeaderElection.VoteResponse(self, e.getSource(), accepted));
			}
		}
	};
	private final Handler<LeaderElection.VoteResponse> handlerVoteResponse = new Handler<LeaderElection.VoteResponse>() {
		@Override
		public void handle(LeaderElection.VoteResponse e) {
			synchronized (sync) {
				election.answerReceived(e.getSource(), e.isAccepted());
			}
		}
	};
	private final Handler<LeaderElection.Announcement> handlerAnnouncement = new Handler<LeaderElection.Announcement>() {
		@Override
		public void handle(LeaderElection.Announcement e) {
			synchronized (sync) {

				if (leader == e.getLeader()) {
					return;
				}

				// TODO: Safety check, there could be conflicting elections
				// If a known node has an ID higher than the announcer
				// we should refute the announcement and give the announcer
				// a reference to the leader as we see it.

				leader = e.getLeader();
				tman.getLogger().log("New_leader: " + leader);
				for (Address a : tman.getGradient().getAll()) {
					tman.send(new LeaderElection.Announcement(tman.getSelf(), a, leader));
				}
			}
		}
	};

	private void checkElection() {
		synchronized (sync) {
			if (!election.isInProgress()) {
				return;
			}
			// yep, let's check if we are elected
			if (election.isElected()) {
				// seems that everyone agrees
				// let's tell everyone that I'm the leader!
				leader = tman.getSelf();
				for (Address a : tman.getGradient().getLower()) {
					tman.send(new LeaderElection.Announcement(leader, a, leader));
				}
			} else {
				// that's a pity...
			}
			election.stop();
		}
	}

	private boolean checkLeadership() {
		synchronized (sync) {

			// let's check if there is somebody with a higher ID
			// if yes, let him decide what to do
			if (tman.getGradient().getHighest() == null) {
				// right here we're assuming that we are the leader
				// because there's nobody with a higher ID (from the ones we know about)
				// let's start an election
				election.start(tman.getGradient().getLower());
				return true;
			} else {
				return false;
			}
		}
	}

	public void triggerActionRound() {
		synchronized (sync) {
			if (leader != null && leader.getId() >= tman.getSelf().getId()) {
				// we have nothing to worry about
				return;
			}

			if (election.isInProgress()) {
				// let's check that election
				checkElection();
			} else {
				// no, let's check whether we can be the leader
				checkLeadership();
			}
		}
	}

	public void leaderTimedOut() {
		synchronized (sync) {
			leader = null;
		}
	}
}
