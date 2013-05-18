package tman.simulator.snapshot;

import java.util.ArrayList;
import java.util.Collection;
import se.sics.kompics.address.Address;


public class PeerInfo {
	private ArrayList<Address> tmanPartners;
	private ArrayList<Address> cyclonPartners;

//-------------------------------------------------------------------
	public PeerInfo() {
		this.tmanPartners = new ArrayList<Address>();
		this.cyclonPartners = new ArrayList<Address>();
	}

//-------------------------------------------------------------------
	public void updateTManPartners(Collection<Address> partners) {
		this.tmanPartners.clear();
        this.tmanPartners.addAll(partners);
	}

//-------------------------------------------------------------------
	public void updateCyclonPartners(ArrayList<Address> partners) {
		this.cyclonPartners = partners;
	}

//-------------------------------------------------------------------
	public ArrayList<Address> getTManPartners() {
		return this.tmanPartners;
	}

//-------------------------------------------------------------------
	public ArrayList<Address> getCyclonPartners() {
		return this.cyclonPartners;
	}
}
