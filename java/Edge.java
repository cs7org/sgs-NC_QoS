import org.networkcalculus.dnc.network.server_graph.Server;

import java.util.ArrayList;
import java.util.List;

public class Edge {
    // Implementing the nodes as a list rather than two separate strings enables easier matching later on.
    private final List<String> nodes = new ArrayList<>(2);
    private final double bitrate;
    private final double latency;

    private final List<Server> prio_servers;

    public Edge(String node1, String node2, double bitrate, double latency) {
        this.nodes.add(node1);
        this.nodes.add(node2);
        this.bitrate = bitrate;
        this.latency = latency;
        prio_servers = new ArrayList<>(FlowPriority.values().length);
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
        return prio_servers.get(0);
    }
    public Server getServer(FlowPriority prio) {return prio_servers.get(prio.ordinal());}

    public void setServer(Server server) {
        if(prio_servers.isEmpty()){
            prio_servers.add(server);
        }
        else {
            this.prio_servers.set(0, server);
        }
    }
    public void setServer(FlowPriority prio, Server server) {
        if (prio_servers.size() <= prio.ordinal()){
            this.prio_servers.add(server);
        }
        else {
            this.prio_servers.set(prio.ordinal(), server);
        }
    }
}
