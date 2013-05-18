package tman.system.peer.tman;

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
import tman.system.peer.tman.election.ElectionProtocol.ElectionRequestHandler;
import tman.system.peer.tman.election.LeaderPingHandler;
import tman.system.peer.tman.messages.ElectionRequest;
import tman.system.peer.tman.messages.PingRequest;
import tman.system.peer.tman.messages.PingResponse;
import tman.system.peer.tman.messages.TManMessage;

import common.configuration.TManConfiguration;

import cyclon.system.peer.cyclon.CyclonSample;
import cyclon.system.peer.cyclon.CyclonSamplePort;

public final class TMan extends ComponentDefinition {

	private common.Logger.Instance logger;
	Negative<TManSamplePort> tmanPort = negative(TManSamplePort.class);
	Positive<CyclonSamplePort> cyclonSamplePort = positive(CyclonSamplePort.class);
	Positive<Network> networkPort = positive(Network.class);
	Positive<Timer> timerPort = positive(Timer.class);
	private long period;
	private Address self;
	private final Address leader = null;
	private final Collection<Address> tmanPartners = new LinkedList<>();
	private Gradient gradient;
	private final PingTimeouts timeouts = new PingTimeouts();


	private TManConfiguration tmanConfiguration;
	private Random r;

	public class TManSchedule extends Timeout {

		public TManSchedule(SchedulePeriodicTimeout request) {
			super(request);
		}

		public TManSchedule(ScheduleTimeout request) {
			super(request);
		}
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

		subscribe(new ElectionRequestHandler(this), networkPort);

		LeaderPingHandler lph = new LeaderPingHandler(this);
		subscribe(lph.getResponseHandler(), networkPort);
		subscribe(lph.getRequestHandler(), networkPort);
	}

	// This exposes some functionality. Might be ugly but it sure is better than making this 
	// a god-object.
	/**
	 * Send a TManMessage over the network
	 * @param message
	 */
	public void send(TManMessage message)
	{
		trigger(message, networkPort);
	}

	//-------------------------------------------------------------------	
	Handler<TManInit> handleInit = new Handler<TManInit>() {
		@Override
		public void handle(TManInit init) {

			setSelf(init.getSelf());
			logger = new common.Logger.Instance(getSelf().toString());

			setGradient(new Gradient(getSelf()));

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

			Snapshot.updateTManPartners(getSelf(), tmanPartners);

			Collection<Address> timedOut = timeouts.getTimedOut();
			if (timedOut.size() > 0) {
				String s = timedOut.size() + " neighbours timed out from " + getGradient().getAll().size() + "\n";
				for(Address a : timedOut) {
					s += "\t" + a;
				}
				logger.log(s);
			}

			getGradient().remove(timedOut);

			Collection<Address> gradientNeighbours = getGradient().getAll();
			timeouts.initialize(gradientNeighbours);

			String s = "neighbours:\n";

			for (Address a : gradientNeighbours) {
				s += "\t" + a + "\n";
				trigger(new PingRequest(getSelf(), a), networkPort);
				trigger(new ElectionRequest(getSelf(), a), networkPort);
			}

			logger.log(s);

			// Publish sample to connected components
			trigger(new TManSample(tmanPartners), tmanPort);
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
	private boolean elected;

	// TODO - if you call this method with a list of entries, it will
	// return a single node, weighted towards the 'best' node (as defined by
	// ComparatorById) with the temperature controlling the weighting.
	// A temperature of '1.0' will be greedy and always return the best node.
	// A temperature of '0.000001' will return a random node.
	// A temperature of '0.0' will throw a divide by zero exception :)
	// Reference:
	// http://webdocs.cs.ualberta.ca/~sutton/book/2/node4.html
	public Address getSoftMaxAddress(List<Address> entries) {
		Collections.sort(entries, new ComparatorById(getSelf()));

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

	public Gradient getGradient() {
		return gradient;
	}

	public void setGradient(Gradient gradient) {
		this.gradient = gradient;
	}

	public boolean isElected() {
		return elected;
	}

	public void setElected(boolean elected) {
		this.elected = elected;
	}

	public Address getLeader() {
		return leader;
	}

	public Address getSelf() {
		return self;
	}

	public void setSelf(Address self) {
		this.self = self;
	}
}
