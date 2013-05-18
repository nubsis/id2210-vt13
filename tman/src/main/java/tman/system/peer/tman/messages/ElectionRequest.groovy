package tman.system.peer.tman.messages

import se.sics.kompics.address.Address

class ElectionRequest extends TManMessage {

	/**
	 * The peer that made this request
	 */
	private final Address elector;
	private final Date electionTime;

	ElectionRequest(Address src, Address dst)
	{
		super(src, dst)
		this.elector = src;
	}

	public Address getElector() {
		return elector;
	}
}
