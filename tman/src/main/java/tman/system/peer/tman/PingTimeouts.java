/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tman.system.peer.tman;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;
import se.sics.kompics.address.Address;

/**
 *
 * @author Andrew
 */
public class PingTimeouts {

    private final HashMap<Address, Boolean> neighbours = new HashMap<>();

    synchronized public void add(Address address) {
        if (address != null) {
            neighbours.put(address, Boolean.FALSE);
        }
    }

    synchronized public void initialize(Collection<Address> addresses) {
        neighbours.clear();
        for (Address a : addresses) {
            if (a != null) {
                neighbours.put(a, Boolean.FALSE);
            }
        }
    }

    synchronized public void replyReceived(Address address) {
        if (address != null) {
            neighbours.put(address, Boolean.TRUE);
        }
    }

    synchronized public Collection<Address> getTimedOut() {
        LinkedList<Address> toRemove = new LinkedList<>();

        for (Entry<Address, Boolean> kv : neighbours.entrySet()) {
            if (!kv.getValue()) {
                toRemove.add(kv.getKey());
            }
        }

        return toRemove;
    }
}
