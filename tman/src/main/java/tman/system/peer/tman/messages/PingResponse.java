/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tman.system.peer.tman.messages;

import java.util.UUID;

import se.sics.kompics.network.Message;

/**
 *
 * @author Andrew
 */
public class PingResponse extends Message {

    public PingResponse(PingRequest req) {
        super(req.getDestination(), req.getSource());
    }
}
