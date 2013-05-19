package common.simulation.scenarios;

import se.sics.kompics.p2p.experiment.dsl.SimulationScenario;

@SuppressWarnings("serial")
public class Scenario1 extends Scenario {
	private static SimulationScenario scenario = new SimulationScenario() {{
                
		StochasticProcess process0 = new StochasticProcess() {{
			eventInterArrivalTime(constant(100));
			raise(20, Operations.peerJoin(), 
                                uniform(0, Integer.MAX_VALUE)
                             );
		}};
                
		StochasticProcess process1 = new StochasticProcess() {{
			eventInterArrivalTime(constant(100));
			raise(20, Operations.peerJoin(), 
                                uniform(0, Integer.MAX_VALUE)
                                );
		}};
		
		StochasticProcess process2 = new StochasticProcess() {{
			eventInterArrivalTime(constant(100));
			raise(100, Operations.addIndexEntry(), 
                                uniform(0, Integer.MAX_VALUE)
                                );
		}};
        
        StochasticProcess process3 = new StochasticProcess() {{
           eventInterArrivalTime(constant(100));
           raise(1, Operations.peerFail, uniform(1, 1));
        }};

		process0.start();
		process1.startAfterTerminationOf(25000, process0);
		process2.startAfterTerminationOf(2000, process1);
        process3.startAfterTerminationOf(6000, process2);
	}};

	// -------------------------------------------------------------------
	public Scenario1() {
		super(scenario);
	}
}
