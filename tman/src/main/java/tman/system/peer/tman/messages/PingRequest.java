/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tman.system.peer.tman.messages;

import java.util.UUID;

import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;

/**
 *
 * @author Andrew
 */
public class PingRequest extends Message {

	private final UUID id = UUID.randomUUID();
	public PingRequest(Address src, Address dst) {
		super(src, dst);
	}

	public UUID getId() {
		return id;
	}

}
