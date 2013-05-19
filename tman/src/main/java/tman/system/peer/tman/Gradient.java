/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tman.system.peer.tman;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;

import se.sics.kompics.address.Address;

import common.configuration.TManConfiguration;

/**
 *
 * @author Andrew
 */
public class Gradient {

    static class GradientComparator implements Comparator<Address> {

        public enum CompareType {

            LowToHigh,
            HighToLow
        }
        private final CompareType type;

        public GradientComparator(CompareType type) {
            this.type = type;
        }

        @Override
        public int compare(Address a1, Address a2) {
            return Integer.compare(a1.getId(), a2.getId()) * (type == CompareType.LowToHigh ? 1 : -1);
        }
    }
    private final Address self;
    private final LinkedList<Address> higher = new LinkedList<>();
    private final LinkedList<Address> lower = new LinkedList<>();

    public Gradient(Address self) {
        this.self = self;
    }

    synchronized public void merge(Collection<Address> newPartners) {

        for (Address a : newPartners) {
            if (a == null || higher.contains(a) || lower.contains(a)) {
                continue;
            }

            if (a.getId() > self.getId()) {
                higher.add(a);
            } else if (a.getId() < self.getId()) {
                lower.add(a);
            }
        }

        Collections.sort(higher, new GradientComparator(GradientComparator.CompareType.LowToHigh));
        Collections.sort(lower, new GradientComparator(GradientComparator.CompareType.HighToLow));

        while (higher.size() > TManConfiguration.GRADIENT_DEPTH) {
            higher.removeLast();
        }

        while (lower.size() > TManConfiguration.GRADIENT_DEPTH) {
            lower.removeLast();
        }
    }

    synchronized public Collection<Address> getAll() {
        LinkedList<Address> neighbours = new LinkedList<>(lower);
        neighbours.addAll(higher);
        return neighbours;
    }

    synchronized public void remove(Address address) {
        higher.remove(address);
        lower.remove(address);
    }

    synchronized public void remove(Collection<Address> addresses) {
        higher.removeAll(addresses);
        lower.removeAll(addresses);
    }
    
    synchronized public Address getHighest() {
        return higher.isEmpty() ? null : higher.getLast();
    }
    
    synchronized public Address getLowest() {
        return lower.isEmpty() ? null : lower.getLast();
    }

    public Collection<Address> getHigher() {
        return new LinkedList<>(higher);
    }

    public Collection<Address> getLower() {
        return new LinkedList<>(lower);
    }
    
    
}
