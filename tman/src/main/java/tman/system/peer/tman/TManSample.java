package tman.system.peer.tman;

import com.sun.java.swing.plaf.windows.WindowsTreeUI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;


import se.sics.kompics.Event;
import se.sics.kompics.address.Address;

public class TManSample extends Event {

    private final Collection<Address> neighbours;
    private final Address leader;
//-------------------------------------------------------------------

    public TManSample(Collection<Address> neighbours, Address leader) {
        this.neighbours = Collections.unmodifiableCollection(neighbours);
        this.leader = leader;
    }

//-------------------------------------------------------------------
    public Collection<Address> getNeighbours() {
        return this.neighbours;
    }

    public Address getLeader() {
        return leader;
    }
    
}
