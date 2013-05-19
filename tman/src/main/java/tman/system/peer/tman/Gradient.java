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
import cyclon.system.peer.cyclon.CyclonSample;
import java.util.List;
import java.util.Random;
import se.sics.kompics.Handler;
import tman.system.peer.tman.messages.NeighbourExchange;
import tman.system.peer.tman.messages.Ping;

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
    private final static Random random = new Random();
    private final TMan tman;
    private final LinkedList<Address> higher = new LinkedList<>();
    private final LinkedList<Address> lower = new LinkedList<>();
    private final PingTimeoutMap timeouts = new PingTimeoutMap();

    public Gradient(TMan tman) {
        this.tman = tman;
    }
    //-------------------------------------------------------------------	
    private Handler<CyclonSample> handleCyclonSample = new Handler<CyclonSample>() {
        @Override
        public void handle(CyclonSample event) {
            merge(event.getSample());
        }
    };
    private Handler<NeighbourExchange.Request> handleExchangeRequest = new Handler<NeighbourExchange.Request>() {
        @Override
        public void handle(NeighbourExchange.Request event) {
            tman.send(new NeighbourExchange.Response(tman.getSelf(), event.getSource(), getAll()));
        }
    };
    private Handler<NeighbourExchange.Response> handleExchangeResponse = new Handler<NeighbourExchange.Response>() {
        @Override
        public void handle(NeighbourExchange.Response event) {
            merge(event.getNeighbours());
        }
    };
    private Handler<Ping.Response> handlerPingResponse = new Handler<Ping.Response>() {
        @Override
        public void handle(Ping.Response e) {
            timeouts.replyReceived(e.getSource());
        }
    };

    public Handler<CyclonSample> getHandleCyclonSample() {
        return handleCyclonSample;
    }

    public Handler<NeighbourExchange.Request> getHandleExchangeRequest() {
        return handleExchangeRequest;
    }

    public Handler<NeighbourExchange.Response> getHandleExchangeResponse() {
        return handleExchangeResponse;
    }

    public Handler<Ping.Response> getHandlerPingResponse() {
        return handlerPingResponse;
    }

    synchronized private void merge(Collection<Address> newPartners) {

        for (Address a : newPartners) {
            if (a == null || higher.contains(a) || lower.contains(a)) {
                continue;
            }

            if (a.getId() > tman.getSelf().getId()) {
                higher.add(a);
            } else if (a.getId() < tman.getSelf().getId()) {
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

    synchronized private void remove(Collection<Address> addresses) {
        higher.removeAll(addresses);
        lower.removeAll(addresses);
    }

    synchronized public void triggerActionRound() {
        remove(timeouts.getTimedOut());
        Collection<Address> all = getAll();
        timeouts.initialize(all);

        for (Address a : all) {
            tman.send(new Ping.Request(tman.getSelf(), a));
        }

        sendExchangeMessage(lower);
        sendExchangeMessage(higher);
    }

    private void sendExchangeMessage(Collection<Address> collection) {
        if (collection.isEmpty()) {
            return;
        }

        tman.send(new NeighbourExchange.Request(
                tman.getSelf(),
                new LinkedList<>(collection).get(random.nextInt(collection.size()))));
    }

    synchronized public Collection<Address> getAll() {
        LinkedList<Address> neighbours = new LinkedList<>(lower);
        neighbours.addAll(higher);
        return neighbours;
    }

    synchronized public Collection<Address> getHigher() {
        return new LinkedList<>(higher);
    }

    synchronized public Collection<Address> getLower() {
        return new LinkedList<>(lower);
    }

    synchronized public Address getHighest() {
        return higher.isEmpty() ? null : higher.getLast();
    }

    synchronized public Address getLowest() {
        return lower.isEmpty() ? null : lower.getLast();
    }
}
