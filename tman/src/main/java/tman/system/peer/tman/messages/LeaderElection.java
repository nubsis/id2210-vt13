/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tman.system.peer.tman.messages;

import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;

/**
 *
 * @author Andrew
 */
public class LeaderElection {

    public static class VoteRequest extends Message {

        private final Address candidate;

        public VoteRequest(Address src, Address dst) {
            super(src, dst);
            candidate = src;
        }

        public Address getCandidate() {
            return candidate;
        }
    };

    public static class VoteResponse extends Message {

        private final boolean accepted;

        public VoteResponse(Address src, Address dst, boolean accepted) {
            super(src, dst);
            this.accepted = accepted;
        }

        public boolean isAccepted() {
            return accepted;
        }
    }

    public static class Announcement extends Message {

        private final Address leader;

        public Announcement(Address src, Address dst, Address leader) {
            super(src, dst);
            this.leader = leader;
        }

        public Address getLeader() {
            return leader;
        }
    }
}
