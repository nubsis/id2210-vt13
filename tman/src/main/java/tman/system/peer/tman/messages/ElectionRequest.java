package tman.system.peer.tman.messages;

import se.sics.kompics.address.Address;

public class ElectionRequest extends TManMessage {

	/**
	 * The peer that made this request
	 */
	private final Address elector;

	public ElectionRequest(Address src, Address dst)
	{
		super(src, dst);
		elector = src;
	}

	public Address getElector() {
		return elector;
	}
}
