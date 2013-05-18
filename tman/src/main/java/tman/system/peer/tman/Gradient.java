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
	private final LinkedList<Address> up = new LinkedList<>();
	private final LinkedList<Address> down = new LinkedList<>();

	public Gradient(Address self) {
		this.self = self;
	}

	synchronized public void merge(Collection<Address> newPartners) {

		for (Address a : newPartners) {
			if (a.getId() > self.getId() && !getUp().contains(a)) {
				getUp().add(a);
			} else if (a.getId() < self.getId() && !down.contains(a)) {
				down.add(a);
			}
		}

		Collections.sort(getUp(), new GradientComparator(GradientComparator.CompareType.LowToHigh));
		Collections.sort(down, new GradientComparator(GradientComparator.CompareType.HighToLow));

		while (getUp().size() > TManConfiguration.MAX_NEIGHBOUR_COUNT) {
			getUp().removeLast();
		}

		while (down.size() > TManConfiguration.MAX_NEIGHBOUR_COUNT) {
			down.removeLast();
		}
	}

	synchronized public Collection<Address> getAll() {
		LinkedList<Address> neighbours = new LinkedList<>(down);
		neighbours.addAll(getUp());
		return neighbours;
	}

	synchronized public void remove(Address address) {
		getUp().remove(address);
		down.remove(address);
	}

	synchronized public void remove(Collection<Address> addresses) {
		getUp().removeAll(addresses);
		down.removeAll(addresses);
	}

	public LinkedList<Address> getUp() {
		return up;
	}
}
