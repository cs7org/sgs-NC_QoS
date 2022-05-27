import org.networkcalculus.dnc.curves.ArrivalCurve;
import org.networkcalculus.dnc.curves.Curve;
import org.networkcalculus.dnc.curves.ServiceCurve;
import org.networkcalculus.dnc.network.server_graph.Flow;
import org.networkcalculus.dnc.network.server_graph.Server;
import org.networkcalculus.dnc.network.server_graph.ServerGraph;
import py4j.GatewayServer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class NCEntryPoint {

    private final List<Edge> edgeList = new ArrayList<>();
    private final List<SGService> sgServices = new ArrayList<>();
    private ServerGraph serverGraph;

    public NCEntryPoint() {
    }

    /**
     * Retrieves an edge out of a list, defined by its node pair.
     *
     * @param listEdge edge list to search in
     * @param nodes    node collection to use for comparison with every edge.
     * @return matching edge, null if none is found.
     */
    public static Edge findEdgebyNodes(Collection<Edge> listEdge, Collection<String> nodes) {
        return listEdge.stream().filter(edge -> edge.getNodes().equals(nodes)).findFirst().orElse(null);
    }

    /**
     * Retrieve all neighbors of a specific edge
     *
     * @param currEdge edge, for which the neighbors shall be found
     * @param edgeList list of edges to search in.
     * @return list of found neigbors.
     */
    private static List<Edge> getAllNeighborEdges(Edge currEdge, Collection<Edge> edgeList) {
        List<Edge> targetEdgeList;
        targetEdgeList = edgeList.stream().filter(edge -> !Collections.disjoint(currEdge.getNodes(), edge.getNodes())).collect(Collectors.toList());
        targetEdgeList.remove(currEdge);
        return targetEdgeList;
    }

    public static void main(String[] args) {
        GatewayServer gatewayServer = new GatewayServer(new NCEntryPoint());
        gatewayServer.start();
        System.out.println("Gateway Server Started");
    }

    /**
     * This function is called via the py4j Gateway from the python source code.
     * Every Edge is added to the network list one by one. (The node order is not yet defined)
     * Bitrate + latency will be later used for modeling the link as a rate-latency service curve
     *
     * @param node1   first node
     * @param node2   second node
     * @param bitrate link bitrate (the unit is to be defined by the user and is not checked by the program.)
     * @param latency link delay
     */
    public void addEdge(String node1, String node2, double bitrate, double latency) {
        Edge newEdge = new Edge(node1, node2, bitrate, latency);
        edgeList.add(newEdge);
    }

    /**
     * Add SGS to the list. To be called via Python.
     *
     * @param SGSName     SGService name
     * @param servername  server name where the service is running on
     * @param bucket_size bucket size for token bucket arrival curve modeling.
     * @param bitrate     bitrate for token bucket arrival curve modeling
     * @param multipath   all paths which are used for the flows
     */
    public void addSGService(String SGSName, String servername, int bucket_size, int bitrate, List<List<String>> multipath) {
        SGService service = new SGService(SGSName, servername, bucket_size, bitrate, multipath);
        sgServices.add(service);
    }

    /**
     * Reset all stored values (e.g. empty edgelist)
     */
    public void resetAll() {
        edgeList.clear();
        sgServices.clear();
    }

    //TODO: Check for better Exception handling (here "sg.addTurn()" throws exception if servers not present etc.)
    public void createNCNetwork() {
        // Create ServerGraph - aka network
        ServerGraph sg = new ServerGraph();

        // Add every edge as a server to the network
        for (Edge edge : edgeList) {
            // Create service curve for this server
            ServiceCurve service_curve = Curve.getFactory().createRateLatency(edge.getBitrate(), edge.getLatency());
            // Add server (edge) with service curve to network
            //TODO: Define Server multiplexing
            Server serv = sg.addServer(String.join(",", edge.getNodes()), service_curve);
            // Add server to edge for future references
            edge.setServer(serv);
        }

        // Add the turns (connections) between the edges to the network
        for (Edge currEdge : edgeList) {
            List<Edge> targetEdgeList = getAllNeighborEdges(currEdge, edgeList);
            for (Edge targetEdge : targetEdgeList) {
                try {
                    sg.addTurn(currEdge.getServer(), targetEdge.getServer());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        // Add all flows to the network
        for (SGService service : sgServices) {
            // Create arrival curve with specified details, TODO: Subject to discussion
            ArrivalCurve arrival_curve = Curve.getFactory().createTokenBucket(service.getBitrate(), service.getBucket_size());
            // Iterate over every field device - server combination (aka Path)
            for (int pathIdx = 0; pathIdx < service.getMultipath().size(); pathIdx++) {
                List<String> path = service.getMultipath().get(pathIdx);
                List<Server> dncPath = new ArrayList<>();
                List<String> edgeNodes = new ArrayList<>();
                // Find servers along path
                for (int i = 1; i < path.size(); i++) {  // Important: We start with the second item in the list!
                    edgeNodes.clear();
                    edgeNodes.add(path.get(i - 1));
                    edgeNodes.add(path.get(i));
                    Collections.sort(edgeNodes);    // Important for comparison
                    // Add the found edge to the dncPath
                    dncPath.add(findEdgebyNodes(edgeList, edgeNodes).getServer());
                }
                // Create flow and add it to the network
                try {
                    Flow flow = sg.addFlow(arrival_curve, dncPath);
                    service.addFlow(flow);
                } catch (Exception e) {
                    //TODO: Exception Handling
                    throw new RuntimeException(e);
                }

            }
        }
        // Safe the server graph
        this.serverGraph = sg;

    }

}
