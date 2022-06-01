import org.networkcalculus.dnc.network.server_graph.Server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Edge {
    // Implementing the nodes as a list rather than two separate strings enables easier matching later on.
    private final List<String> nodes = new ArrayList<>(2);
    private final double bitrate;
    private final double latency;

    private Server server;

    public Edge(String node1, String node2, double bitrate, double latency) {
        this.nodes.add(node1);
        this.nodes.add(node2);
        this.bitrate = bitrate;
        this.latency = latency;
    }

    public List<String> getNodes() {
        return nodes;
    }

    public double getBitrate() {
        return bitrate;
    }

    public double getLatency() {
        return latency;
    }

    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }
}
