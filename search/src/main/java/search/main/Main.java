package search.main;

import search.simulator.core.SearchExecutionMain;
import common.configuration.Configuration;
import common.simulation.scenarios.Scenario;
import common.simulation.scenarios.Scenario1;

public class Main {

    public static void main(String[] args) throws Throwable {
        long seed = System.currentTimeMillis();
        Configuration configuration = new Configuration(seed);
        
        GroovyTest gt = new GroovyTest();
        gt.test1();
        NewGroovyClass ngc = new NewGroovyClass();
        ngc.test();

        Scenario scenario = new Scenario1();
        scenario.setSeed(seed);
        scenario.getScenario().execute(SearchExecutionMain.class);
    }
}
