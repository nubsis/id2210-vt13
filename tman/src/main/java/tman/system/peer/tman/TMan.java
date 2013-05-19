package tman.system.peer.tman;

import common.Logger;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import tman.simulator.snapshot.Snapshot;
import tman.system.peer.tman.messages.ElectionStartRequest;
import tman.system.peer.tman.messages.PingRequest;
import tman.system.peer.tman.messages.PingResponse;
import tman.system.peer.tman.messages.TManMessage;

import common.configuration.TManConfiguration;

import cyclon.system.peer.cyclon.CyclonSample;
import cyclon.system.peer.cyclon.CyclonSamplePort;
import se.sics.kompics.network.Message;
import tman.system.peer.tman.election.ElectionProtocol;

public final class TMan extends ComponentDefinition {

    public class TManSchedule extends Timeout {

        public TManSchedule(SchedulePeriodicTimeout request) {
            super(request);
        }

        public TManSchedule(ScheduleTimeout request) {
            super(request);
        }
    }
    private common.Logger.Instance logger;
    private final ElectionProtocol election = new ElectionProtocol(this);
    Negative<TManSamplePort> tmanPort = negative(TManSamplePort.class);
    Positive<CyclonSamplePort> cyclonSamplePort = positive(CyclonSamplePort.class);
    Positive<Network> networkPort = positive(Network.class);
    Positive<Timer> timerPort = positive(Timer.class);
    private long period;
    private Address self;
    private final Collection<Address> tmanPartners = new LinkedList<>();
    private Gradient gradient;
    private final PingTimeouts timeouts = new PingTimeouts();
    private TManConfiguration tmanConfiguration;
    private Random r;

    public Gradient getGradient() {
        return gradient;
    }

    public Logger.Instance getLogger() {
        return logger;
    }

    public Address getSelf() {
        return self;
    }

    //-------------------------------------------------------------------	
    public TMan() {

        subscribe(handleInit, control);
        subscribe(handleRound, timerPort);
        subscribe(handleCyclonSample, cyclonSamplePort);
        subscribe(handleTManPartnersResponse, networkPort);
        subscribe(handleTManPartnersRequest, networkPort);

        subscribe(handlePingRequest, networkPort);
        subscribe(handlePingResponse, networkPort);

        subscribe(election.getHandlerVoteRequest(), networkPort);
        subscribe(election.getHandlerVoteResponse(), networkPort);
        subscribe(election.getHandlerAnnouncement(), networkPort);
        //LeaderPingHandler lph = new LeaderPingHandler(this);
        //subscribe(lph.getResponseHandler(), networkPort);
        //subscribe(lph.getRequestHandler(), networkPort);
    }

    // This exposes some functionality. Might be ugly but it sure is better than making this 
    // a god-object.
    /**
     * Send a TManMessage over the network
     *
     * @param message
     */
    public void send(Message message) {
        if (message.getSource() != message.getDestination()) {
            trigger(message, networkPort);
        }
    }
    //-------------------------------------------------------------------	
    Handler<TManInit> handleInit = new Handler<TManInit>() {
        @Override
        public void handle(TManInit init) {

            self = init.getSelf();
            gradient = new Gradient(self);
            logger = new common.Logger.Instance(self.toString());

            tmanConfiguration = init.getConfiguration();
            period = tmanConfiguration.getPeriod();
            r = new Random(tmanConfiguration.getSeed());
            SchedulePeriodicTimeout rst = new SchedulePeriodicTimeout(5000, 5000);

            rst.setTimeoutEvent(new TManSchedule(rst));
            trigger(rst, timerPort);
        }
    };
    //-------------------------------------------------------------------	
    Handler<TManSchedule> handleRound = new Handler<TManSchedule>() {
        @Override
        public void handle(TManSchedule event) {
            // Snapshot.updateTManPartners(self, tmanPartners);

            Collection<Address> timedOut = timeouts.getTimedOut();
            getGradient().remove(timedOut);

            Address leader = election.getLeader();
            if (leader != null && leader != self && timedOut.contains(leader)) {
                election.leaderTimedOut();
            }
            election.triggerActionRound();

            Collection<Address> neighbours = getGradient().getAll();

            timeouts.initialize(neighbours);
            if (leader != null && leader != self) {
                timeouts.add(leader);
                send(new PingRequest(self, leader));
            }
            for (Address a : neighbours) {
                send(new PingRequest(self, a));
            }

            trigger(new TManSample(neighbours, leader), tmanPort);
        }
    };
    //-------------------------------------------------------------------	
    Handler<CyclonSample> handleCyclonSample = new Handler<CyclonSample>() {
        @Override
        public void handle(CyclonSample event) {
            getGradient().merge(event.getSample());
        }
    };
    Handler<PingRequest> handlePingRequest = new Handler<PingRequest>() {
        @Override
        public void handle(PingRequest request) {
            trigger(new PingResponse(request), networkPort);
        }
    };
    Handler<PingResponse> handlePingResponse = new Handler<PingResponse>() {
        @Override
        public void handle(PingResponse response) {
            timeouts.replyReceived(response.getSource());
        }
    };
    //-------------------------------------------------------------------	
    Handler<ExchangeMsg.Request> handleTManPartnersRequest = new Handler<ExchangeMsg.Request>() {
        @Override
        public void handle(ExchangeMsg.Request event) {
        }
    };
    Handler<ExchangeMsg.Response> handleTManPartnersResponse = new Handler<ExchangeMsg.Response>() {
        @Override
        public void handle(ExchangeMsg.Response event) {

            event.getSelectedBuffer().getDescriptors();
        }
    };

    // TODO - if you call this method with a list of entries, it will
    // return a single node, weighted towards the 'best' node (as defined by
    // ComparatorById) with the temperature controlling the weighting.
    // A temperature of '1.0' will be greedy and always return the best node.
    // A temperature of '0.000001' will return a random node.
    // A temperature of '0.0' will throw a divide by zero exception :)
    // Reference:
    // http://webdocs.cs.ualberta.ca/~sutton/book/2/node4.html
    public Address getSoftMaxAddress(List<Address> entries) {
        Collections.sort(entries, new ComparatorById(self));

        double rnd = r.nextDouble();
        double total = 0.0d;
        double[] values = new double[entries.size()];
        int j = entries.size() + 1;
        for (int i = 0; i < entries.size(); i++) {
            // get inverse of values - lowest have highest value.
            double val = j;
            j--;
            values[i] = Math.exp(val / tmanConfiguration.getTemperature());
            total += values[i];
        }

        for (int i = 0; i < values.length; i++) {
            if (i != 0) {
                values[i] += values[i - 1];
            }
            // normalise the probability for this entry
            double normalisedUtility = values[i] / total;
            if (normalisedUtility >= rnd) {
                return entries.get(i);
            }
        }
        return entries.get(entries.size() - 1);
    }
}
