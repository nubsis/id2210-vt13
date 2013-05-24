package common.configuration;

import common.simulation.scenarios.Scenario;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Properties;

public final class SearchConfiguration {

    public static final int SEARCH_REQUEST_TIMEOUT = 3000;
    public static final int SEARCH_SCHEDULE_TIMEOUT = 5000;
    
    private final long period;
    private final int numPartitions;
    private final int maxNumRoutingEntries;
    private final long seed;

    public SearchConfiguration(long seed) {
        this.period = 2*1000;
        this.numPartitions = 10;
        this.maxNumRoutingEntries = 20;
        this.seed = seed;
    }
    
    public SearchConfiguration(long period, int numPartitions, int maxNumRoutingEntries, long seed) {
        this.period = period;
        this.numPartitions = numPartitions;
        this.maxNumRoutingEntries = maxNumRoutingEntries;
        this.seed = seed;
    }

    public long getPeriod() {
        return this.period;
    }

    public int getNumPartitions() {
        return numPartitions;
    }

    public int getMaxNumRoutingEntries() {
        return maxNumRoutingEntries;
    }

    public long getSeed() {
        return seed;
    }
    
    public void store(String file) throws IOException {
        Properties p = new Properties();
        p.setProperty("period", "" + period);
        p.setProperty("numPartitions", "" + numPartitions);
        p.setProperty("maxNumRoutingEntries", "" + maxNumRoutingEntries);
        p.setProperty("seed", "" + seed);

        Writer writer = new FileWriter(file);
        p.store(writer, "se.sics.kompics.p2p.overlay.application");
    }

    public static SearchConfiguration load(String file) throws IOException {
        Properties p = new Properties();
        Reader reader = new FileReader(file);
        p.load(reader);

        long period = Long.parseLong(p.getProperty("period"));
        int numPartitions = Integer.parseInt(p.getProperty("numPartitions"));
        int maxNumRoutingEntries = Integer.parseInt(p.getProperty("maxNumRoutingEntries"));
        long seed = Long.parseLong(p.getProperty("seed"));

        return new SearchConfiguration(period, numPartitions, maxNumRoutingEntries, seed);
    }
}
