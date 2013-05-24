package tman.system.peer.tman;

import common.Logger;
import common.configuration.TManConfiguration;
import cyclon.system.peer.cyclon.CyclonSample;

import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.Message;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;

import cyclon.system.peer.cyclon.CyclonSamplePort;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tman.system.peer.tman.messages.Ping;

public final class TMan extends ComponentDefinition {

    public class TManSchedule extends Timeout {

        public TManSchedule(SchedulePeriodicTimeout request) {
            super(request);
        }

        public TManSchedule(ScheduleTimeout request) {
            super(request);
        }
    }
    Negative<TManSamplePort> tmanPort = negative(TManSamplePort.class);
    Positive<CyclonSamplePort> cyclonSamplePort = positive(CyclonSamplePort.class);
    Positive<Network> networkPort = positive(Network.class);
    Positive<Timer> timerPort = positive(Timer.class);
    private common.Logger.Instance logger;
    private final Map<Integer, Address> partitionRoutes = new ConcurrentHashMap<>();
    private final ElectionProtocol election = new ElectionProtocol(this);
    private final Gradient gradient = new Gradient(this);
    private Address self;
    private int partitionId;

    public Gradient getGradient() {
        return gradient;
    }

    public Logger.Instance getLogger() {
        return logger;
    }

    public Address getSelf() {
        return self;
    }

    public int getPartitionId() {
        return partitionId;
    }

    //-------------------------------------------------------------------	
    public TMan() {

        subscribe(handlerInit, control);
        subscribe(handlerRound, timerPort);

        subscribe(handlerPingRequest, networkPort);
        subscribe(handlerCyclonSample, cyclonSamplePort);

        subscribe(gradient.getHandleCyclonSample(), cyclonSamplePort);
        subscribe(gradient.getHandleExchangeRequest(), networkPort);
        subscribe(gradient.getHandleExchangeResponse(), networkPort);
        subscribe(gradient.getHandlerPingResponse(), networkPort);

        subscribe(election.getHandlerVoteRequest(), networkPort);
        subscribe(election.getHandlerVoteResponse(), networkPort);
        subscribe(election.getHandlerAnnouncement(), networkPort);
        subscribe(election.getHandlerPingResponse(), networkPort);
        subscribe(election.getHandlerLeaderAddressRequest(), networkPort);
        subscribe(election.getHandlerLeaderAddressResponse(), networkPort);
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
    private Handler<TManInit> handlerInit = new Handler<TManInit>() {
        @Override
        public void handle(TManInit init) {

            self = init.getSelf();
            partitionId = self.getId() % TManConfiguration.PARTITION_COUNT;
            logger = new common.Logger.Instance(self + "[" + partitionId + "]");

            SchedulePeriodicTimeout rst = new SchedulePeriodicTimeout(
                    TManConfiguration.SCHEDULE_PERIOD * 2,
                    TManConfiguration.SCHEDULE_PERIOD);

            rst.setTimeoutEvent(new TManSchedule(rst));
            trigger(rst, timerPort);
        }
    };
    //-------------------------------------------------------------------	
    private Handler<TManSchedule> handlerRound = new Handler<TManSchedule>() {
        @Override
        public void handle(TManSchedule event) {

            gradient.triggerActionRound();
            election.triggerActionRound();

            trigger(new TManSample(
                    gradient.getLower(),
                    gradient.getHigher(),
                    partitionRoutes,
                    election.getLeader()),
                    tmanPort);
            
        }
    };
    //-------------------------------------------------------------------	
    private Handler<Ping.Request> handlerPingRequest = new Handler<Ping.Request>() {
        @Override
        public void handle(Ping.Request request) {
            trigger(new Ping.Response(request), networkPort);
        }
    };
    //------------------------------------------------------------------
    private Handler<CyclonSample> handlerCyclonSample = new Handler<CyclonSample>() {
        @Override
        public void handle(CyclonSample event) {

            for (Address address : event.getSample()) {
                int addressPartition = address.getId() % TManConfiguration.PARTITION_COUNT;
                if (partitionId != addressPartition) {
                    partitionRoutes.put(addressPartition, address);
                }
            }
        }
    };
}
