/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tman.system.peer.tman;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import se.sics.kompics.address.Address;

/**
 *
 * @author Andrew
 */
public class TManHelper {

    static public Collection<Address> merge(Collection<Address> first, Collection<Address> second, int id, int count) {
        LinkedList<Address> merged = new LinkedList<Address>();
        HashSet<Integer> ids = new HashSet<Integer>();
        for (Address a : first) {
            if (a.getId() > id) {
                ids.add(a.getId());
                merged.add(a);
            }
        }
        
        for(Address a: second){
            if(a.getId() > id && !ids.contains(a.getId())) {
                merged.add(a);
            }
        }
        
        Collections.sort(merged, new Comparator<Address>(){
            @Override
            public int compare(Address a1, Address a2) {
                return Integer.compare(a1.getId(), a2.getId());
            }
        });
        
        return merged.subList(0, Math.min(count, merged.size()));
    }
}
