package tman.system.peer.tman;

import se.sics.kompics.Init;
import se.sics.kompics.address.Address;

public final class TManInit extends Init {

	private final Address peerSelf;

//-------------------------------------------------------------------
	public TManInit(Address peerSelf) {
		super();
		this.peerSelf = peerSelf;
	}

//-------------------------------------------------------------------
	public Address getSelf() {
		return this.peerSelf;
	}
}