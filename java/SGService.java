import org.networkcalculus.dnc.network.server_graph.Flow;

import java.util.ArrayList;
import java.util.List;

public class SGService {
    private final String name;
    private final String server;
    private final int bucket_size;
    private final int bitrate;
    private final double deadline;
    private final List<List<String>> multipath;
    private final List<Flow> flows = new ArrayList<>();
    private final FlowPriority priority;

    public SGService(String name, String servername, int bucket_size, int bitrate, double deadline, List<List<String>> multipath, FlowPriority priority) {
        this.name = name;
        this.server = servername;
        this.bucket_size = bucket_size;
        this.bitrate = bitrate;
        this.deadline = deadline;
        this.multipath = multipath;
        this.priority = priority;
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

    public FlowPriority getPriority() {
        return priority;
    }
}
