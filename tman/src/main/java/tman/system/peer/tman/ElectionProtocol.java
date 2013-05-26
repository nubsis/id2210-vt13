package tman.system.peer.tman;

import java.util.Collection;
import java.util.HashMap;

import se.sics.kompics.Handler;
import se.sics.kompics.address.Address;
import tman.system.peer.tman.messages.LeaderAddress;
import tman.system.peer.tman.messages.LeaderElection;
import tman.system.peer.tman.messages.Ping;

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
	 * @param neighbours
	 *            The neighbours with whom to perform the election.
	 */
	synchronized public void start(Collection<Address> neighbours) {
	    answers.clear(); // Could be stop()?
	    if (neighbours.isEmpty()) {
		return;
	    }

	    tman.getLogger().log("starting an election: " + neighbours.size());
	    for (Address a : neighbours) {
		answers.put(a, false);
		// let's ask our neighbours if they agree for me to be the
		// leader
		tman.send(new LeaderElection.VoteRequest(tman.getSelf(), a));
	    }
	}

	/**
	 * Clears the status of the current election.
	 */
	synchronized public void stop() {
	    answers.clear();
	}

	synchronized public void answerReceived(Address address,
		boolean accepted) {
	    if (answers.containsKey(address)) {
		tman.getLogger().log(
			"answer received from " + address + " : " + accepted);
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
    private boolean pingReceived = false;
    private Address leader = null;

    public Address getLeader() {
	synchronized (sync) {
	    return leader;
	}
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

		boolean accepted = self.getId() < candidateId
			&& (top == null || top.getId() <= candidateId);

		tman.send(new LeaderElection.VoteResponse(self, e.getSource(),
			accepted));
	    }
	}
    };
    private final Handler<LeaderElection.VoteResponse> handlerVoteResponse = new Handler<LeaderElection.VoteResponse>() {
	@Override
	public void handle(LeaderElection.VoteResponse e) {
	    synchronized (sync) {
		if (!e.isAccepted()) {
		    election.stop();
		    return;
		}
		election.answerReceived(e.getSource(), e.isAccepted());
		if (election.isElected()) {
		    tman.getLogger().log("[NEW_LEADER][SELF]");
		    setLeader(tman.getSelf());
		    for (Address a : tman.getGradient().getLower()) {
			tman.send(new LeaderElection.Announcement(leader, a,
				leader));
		    }
		}
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

		// If a known node has an ID higher than the announcer
		// we should refute the announcement and give the announcer
		// a reference to the leader as we see it.
		if (leader != null && e.getLeader().getId() < leader.getId()) {
		    tman.send(new LeaderElection.Refute(e, leader));
		    return;
		}

		setLeader(e.getLeader());
		tman.getLogger().log("[NEW_LEADER][ANNOUNCEMENT]: " + leader);
		for (Address a : tman.getGradient().getAll()) {
		    tman.send(new LeaderElection.Announcement(tman.getSelf(),
			    a, leader));
		}
	    }
	}
    };

    private final Handler<LeaderElection.Refute> handlerRefute = new Handler<LeaderElection.Refute>() {
	@Override
	public void handle(LeaderElection.Refute event) {
	    // If we get this message, another node with higher ID has received
	    // our announcement and refutes is.
	    synchronized (sync) {


		// Ignore if he doesn't know what he's talking about
		if (leader == event.getLeader() || event.getLeader().getId() < tman.getSelf().getId()) {
		    tman.getLogger().log("Yeah, yeah");
		    return;
		}
		tman.getLogger().log("[NEW_LEADER][REFUTE] " + event.getSource());

		setLeader(event.getLeader());
		tman.getLogger().log("[NEW_LEADER][ANNOUNCEMENT]: " + leader);
		for (Address a : tman.getGradient().getAll()) {
		    tman.send(new LeaderElection.Announcement(tman.getSelf(),
			    a, leader));
		}
	    }
	}
    };
    private final Handler<Ping.Response> handlerPingResponse = new Handler<Ping.Response>() {
	@Override
	public void handle(Ping.Response e) {
	    synchronized (sync) {
		if (e.getSource() == leader) {
		    pingReceived = true;
		}
	    }
	}
    };
    private final Handler<LeaderAddress.Request> handlerLeaderAddressRequest = new Handler<LeaderAddress.Request>() {
	@Override
	public void handle(LeaderAddress.Request e) {
	    synchronized (sync) {
		if (leader != null) {
		    tman.send(new LeaderAddress.Response(tman.getSelf(), e
			    .getSource(), leader));
		}
	    }
	}
    };
    private final Handler<LeaderAddress.Response> handlerLeaderAddressResponse = new Handler<LeaderAddress.Response>() {
	@Override
	public void handle(LeaderAddress.Response e) {
	    synchronized (sync) {
		if (leader == null || e.getLeader().getId() > leader.getId()) {
		    tman.getLogger().log(
			    "[NEW_LEADER][RECEIVED] " + e.getLeader()
			    + " from " + e.getSource());
		    setLeader(e.getLeader());
		}
	    }
	}
    };

    private boolean checkLeadership() {
	synchronized (sync) {

	    // let's check if there is somebody with a higher ID
	    // if yes, let him decide what to do
	    if (tman.getGradient().getHighest() == null) {
		// right here we're assuming that we are the leader
		// because there's nobody with a higher ID (from the ones we
		// know about)
		// let's start an election
		election.start(tman.getGradient().getLower());
		return true;
	    } else {
		return false;
	    }
	}
    }

    private void checkLeader() {
	synchronized (sync) {
	    if (leader == tman.getSelf()) {
		return;
	    }

	    if (!pingReceived) {
		leader = null;
	    }

	    if (leader != null) {
		pingReceived = false;
		tman.send(new Ping.Request(tman.getSelf(), leader));
	    } else {

		Address t = tman.getGradient().getHighest();
		if(t!=null) {
		    tman.send(new LeaderAddress.Request(tman.getSelf(), t));
		}
	    }

	}
    }

    public void triggerActionRound() {
	synchronized (sync) {
	    checkLeader();

	    if (leader != null && leader.getId() >= tman.getSelf().getId()) {
		// we have nothing to worry about
		return;
	    }

	    if (election.isInProgress()) {
		// let's stop the election as we are not elected...
		election.stop();
	    } // no, let's check whether we can be the leader
	    else if (!checkLeadership()) {
		// no, we cannot be the leader... let's ask somebody
		// actually, let's ask everybody
		tman.getLogger().log("[LEADERSHIP][REQUEST] " + tman.getSelf());
		for (Address a : tman.getGradient().getAll()) {

		    tman.send(new LeaderAddress.Request(tman.getSelf(), a));
		}
	    }
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

    public Handler<Ping.Response> getHandlerPingResponse() {
	return handlerPingResponse;
    }

    public Handler<LeaderAddress.Request> getHandlerLeaderAddressRequest() {
	return handlerLeaderAddressRequest;
    }

    public Handler<LeaderAddress.Response> getHandlerLeaderAddressResponse() {
	return handlerLeaderAddressResponse;
    }

    private void setLeader(Address leader) {
	this.leader = leader;
	pingReceived = true;
    }

    public Handler<LeaderElection.Refute> getHandlerRefute() {
	return handlerRefute;
    }
}
