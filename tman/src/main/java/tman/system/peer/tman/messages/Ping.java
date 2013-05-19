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
public class Ping {

    public static class Request extends Message {

        public Request(Address src, Address dst) {
            super(src, dst);
        }
    }

    public static class Response extends Message {

        public Response(Request req) {
            super(req.getDestination(), req.getSource());
        }
    }
}
