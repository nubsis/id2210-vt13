package tman.system.peer.tman;

import com.sun.java.swing.plaf.windows.WindowsTreeUI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;


import se.sics.kompics.Event;
import se.sics.kompics.address.Address;

public class TManSample extends Event {

    private final Map<Integer, Address> routes;
    private final Collection<Address> lowerNeighbours;
    private final Collection<Address> higherNeighbours;
    private final Address leader;
//-------------------------------------------------------------------

    public TManSample(
            Collection<Address> lowerNeighbours,
            Collection<Address> higherNeighbours,
            Map<Integer, Address> routes,
            Address leader) {
        this.lowerNeighbours = Collections.unmodifiableCollection(lowerNeighbours);
        this.higherNeighbours = Collections.unmodifiableCollection(higherNeighbours);
        this.routes = routes;
        this.leader = leader;
    }


    //-------------------------------------------------------------------
    public Collection<Address> getLowerNeighbours() {
        return lowerNeighbours;
    }

    public Collection<Address> getHigherNeighbours() {
        return higherNeighbours;
    }

    public Collection<Address> getAllNeighbours() {
        Collection<Address> all = new LinkedList<>();
        all.addAll(lowerNeighbours);
        all.addAll(higherNeighbours);
        return Collections.unmodifiableCollection(all);
    }
    
    public Address getLeader() {
        return leader;
    }

    public Map<Integer, Address> getRoutes() {
        return routes;
    }
}
