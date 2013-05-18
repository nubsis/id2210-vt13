package tman.system.peer.tman;

import com.sun.java.swing.plaf.windows.WindowsTreeUI;
import java.util.ArrayList;
import java.util.Collection;


import se.sics.kompics.Event;
import se.sics.kompics.address.Address;


public class TManSample extends Event {
	ArrayList<Address> partners = new ArrayList<Address>();

//-------------------------------------------------------------------
	public TManSample(Collection<Address> partners) {
		this.partners.clear();
        this.partners.addAll(partners);
	}
        
	public TManSample() {
	}

//-------------------------------------------------------------------
	public ArrayList<Address> getSample() {
		return this.partners;
	}
}
