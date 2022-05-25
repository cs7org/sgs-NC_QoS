import py4j.GatewayServer;

import java.util.LinkedList;
import java.util.List;

public class NCEntryPoint {

    private final List<Edge> edgeList = new LinkedList<Edge>();

    public NCEntryPoint() {
    }

    public void addEdge(String node1, String node2, double bitrate, double latency){
        Edge newEdge = new Edge(node1, node2, bitrate, latency);
        edgeList.add(newEdge);
    }


    public static void main(String[] args) {
        GatewayServer gatewayServer = new GatewayServer(new NCEntryPoint());
        gatewayServer.start();
        System.out.println("Gateway Server Started");
    }

}
