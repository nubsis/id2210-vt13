/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tman.system.peer.tman.messages;

import java.util.Collection;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;

/**
 *
 * @author Andrew
 */
public class LeaderAddress {

    public static class Request extends Message {

        public Request(Address src, Address dst) {
            super(src, dst);
        }
    }

    public static class Response extends Message {

        private final Address leader;

        public Response(Address source, Address destination, Address leader) {
            super(source, destination);
            this.leader = leader;
        }

        public Address getLeader() {
            return leader;
        }

    }
}
