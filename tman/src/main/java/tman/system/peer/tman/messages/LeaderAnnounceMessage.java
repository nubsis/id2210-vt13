package tman.system.peer.tman.messages;

import se.sics.kompics.address.Address;

/** Yo dawg, I'm your leader now */
public class LeaderAnnounceMessage extends TManMessage {
	public LeaderAnnounceMessage(Address source, Address destination) {
		super(source, destination);
	}
}
