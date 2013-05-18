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

	private final UUID id;

	public PingResponse(PingRequest req) {
		super(req.getDestination(), req.getSource());
		id = req.getId();
	}

	public UUID getId() {
		return id;
	}
}
