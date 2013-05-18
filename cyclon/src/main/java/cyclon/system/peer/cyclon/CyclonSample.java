package cyclon.system.peer.cyclon;

import java.util.ArrayList;
import java.util.Collection;


import se.sics.kompics.Event;
import se.sics.kompics.address.Address;


public class CyclonSample extends Event {
	ArrayList<Address> nodes = new ArrayList<Address>();

//-------------------------------------------------------------------
	public CyclonSample(ArrayList<Address> nodes) {
		this.nodes = nodes;
	}
        
	public CyclonSample() {
	}

//-------------------------------------------------------------------
	public Collection<Address> getSample() {
		return this.nodes;
	}
}
