import org.networkcalculus.dnc.network.server_graph.Flow;

import java.util.ArrayList;
import java.util.List;

/**
 * Class used to store informations about a smart grid service
 */
public class SGService {
    private final String name;
    private final String server;
    private final int bucket_size;
    private final int bitrate;
    private final double deadline;
    private final List<List<String>> multipath;
    private final List<Flow> flows = new ArrayList<>();

    public SGService(String name, String servername, int bucket_size, int bitrate, double deadline, List<List<String>> multipath) {
        this.name = name;
        this.server = servername;
        this.bucket_size = bucket_size;
        this.bitrate = bitrate;
        this.deadline = deadline;
        this.multipath = multipath;
    }

    public List<Flow> getFlows() {
        return flows;
    }

    public String getName() {
        return name;
    }

    public String getServer() {
        return server;
    }

    public int getBucket_size() {
        return bucket_size;
    }

    public int getBitrate() {
        return bitrate;
    }

    public List<List<String>> getMultipath() {
        return multipath;
    }

    public void addFlow(Flow flow) {
        flows.add(flow);
    }

    public double getDeadline() {
        return deadline;
    }
}
