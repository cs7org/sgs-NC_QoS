import org.networkcalculus.dnc.AnalysisConfig;
import org.networkcalculus.dnc.curves.ArrivalCurve;
import org.networkcalculus.dnc.curves.Curve;
import org.networkcalculus.dnc.curves.ServiceCurve;
import org.networkcalculus.dnc.network.server_graph.Flow;
import org.networkcalculus.dnc.network.server_graph.Server;
import org.networkcalculus.dnc.network.server_graph.ServerGraph;
import org.networkcalculus.dnc.tandem.analyses.SeparateFlowAnalysis;
import py4j.GatewayServer;

import java.util.*;
import java.util.stream.Collectors;

public class NCEntryPoint {

    private final List<Edge> edgeList = new ArrayList<>();
    private List<SGService> sgServices = new ArrayList<>();
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
     * Retrieve all connected neigbors of a specific edge
     *
     * @param currEdge edge, for which the neighbors shall be found
     * @param edgeList list of edges to search in.
     * @return list of found neigbors.
     */
    private static List<Edge> getAllConnectingEdges(Edge currEdge, Collection<Edge> edgeList) {
        List<Edge> targetEdgeList;
        HashSet<String> currEdgeNodes = new HashSet<>(currEdge.getNodes());
        // Check if the edges are connected. They are connected if the last node in an edge is the first node in the second node
        // e.g. R10/R20 & R20/R30
        // The first two comparisons: Check for connecting node
        // The third line: Check that the two compared edges do not concern the same node pairs
        //                 (aka are the same edge but maybe different direction)
        targetEdgeList = edgeList.stream()
                .filter(edge -> (edge.getNodes().get(0).equals(currEdge.getNodes().get(1)) ||
                                 edge.getNodes().get(1).equals(currEdge.getNodes().get(0)))
                                && !currEdgeNodes.containsAll(edge.getNodes()))
                .collect(Collectors.toList());
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
     * @param bitrate link bitrate [Byte/s]
     * @param latency link delay [s]
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
     * @param deadline    deadline of the service (in ms)
     * @param multipath   all paths which are used for the flows
     */
    public void addSGService(String SGSName, String servername, int bucket_size, int bitrate, double deadline, List<List<String>> multipath) {
        SGService service = new SGService(SGSName, servername, bucket_size, bitrate, deadline, multipath);
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
//            ServiceCurve service_curve = Curve.getFactory().createRateLatency(edge.getBitrate(), edge.getLatency());
            ServiceCurve service_curve = Curve.getFactory().createRateLatency(edge.getBitrate(), 0);    // Constant bit rate element
            // Add server (edge) with service curve to network
            // (Important: Every "Edge"/"Server" in this Java code is unidirectional!)
            // --> For two-way /bidirectional but independent communication (e.g. switched Ethernet) use the "addEdge"
            // function twice with a switched order of nodes.
            // ASSUMPTION: We have FIFO as Multiplexing strategy - maybe different in the future!
            Server serv = sg.addServer(String.join(",", edge.getNodes()),
                    service_curve, AnalysisConfig.Multiplexing.FIFO);

            // Add server to edge for future references
            edge.setServer(serv);
        }

        // Add the turns (connections) between the edges to the network
        addTurnsToSG(sg);

        // Add all flows to the network
        addFlowsToSG(sg, sgServices, -1);
        this.serverGraph = sg;
        System.out.printf("%d Flows %n", sg.getFlows().size());
    }

    private void addTurnsToSG(ServerGraph sg) {
        for (Edge currEdge : edgeList) {
            List<Edge> targetEdgeList = getAllConnectingEdges(currEdge, edgeList);
            for (Edge targetEdge : targetEdgeList) {
                // We can just freely add one turn twice, duplicates get omitted by DiscoDNC
                try {
                    sg.addTurn(currEdge.getServer(), targetEdge.getServer());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * This function adds {@code nmbFlow} number of flows to the server graph (sg is modified in place).
     * @param sg Servergraph to add the flows to.
     * @param sgServiceList List of all available SGServices from which the flows shall be derived.
     * @param nmbFlow number of flows which should be added. Use "-1" for all available flows.
     */
    private void addFlowsToSG(ServerGraph sg, List<SGService> sgServiceList, int nmbFlow) {
        // Use nmbFlow = -1 to add all available flows.
        if (nmbFlow == -1){
            nmbFlow = Integer.MAX_VALUE;
        }
        // Add nmbFlow flows to the network (at most the available ones)
        int counter = 0;
        outerloop:
        for (SGService service : sgServiceList) {
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
                    // Add the found edge to the dncPath
                    dncPath.add(findEdgebyNodes(edgeList, edgeNodes).getServer());
                }
                // Create flow and add it to the network
                try {
                    Flow flow = sg.addFlow(arrival_curve, dncPath);
                    service.addFlow(flow);
                    if (++counter >= nmbFlow) {
                        break outerloop;
                    }
                } catch (Exception e) {
                    //TODO: Exception Handling
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public boolean calculateNCDelays(){
        // The AnalysisConfig can be used to modify different analysis parameters, e.g. the used arrival bounding method
        // or to enforce Multiplexing strategies on the servers.
        AnalysisConfig configuration = new AnalysisConfig();
        configuration.setArrivalBoundMethod(AnalysisConfig.ArrivalBoundMethod.AGGR_PBOO_CONCATENATION);
        boolean delayTorn = false;
        try {
            System.out.printf("------ Starting NC Analysis ------%n");
            for (SGService sgs : sgServices) {
                double maxDelay = 0;
                System.out.printf("--- Analyzing SGS \"%s\" ---%n", sgs.getName());
                for (Flow flow : sgs.getFlows()) {
                    System.out.printf("- Analyzing flow \"%s\" -%n", flow);
                    try {
                        SeparateFlowAnalysis sfa = new SeparateFlowAnalysis(this.serverGraph, configuration);    //TODO: Check if we need to modify the TFA configuration
                        sfa.performAnalysis(flow);
//                        System.out.println("e2e SFA SCs     : " + sfa.getLeftOverServiceCurves());
//                        System.out.println("     per server : " + sfa.getServerLeftOverBetasMapString());
//                        System.out.println("xtx per server  : " + sfa.getServerAlphasMapString());
                        System.out.printf("delay bound     : %.2fms %n", sfa.getDelayBound().doubleValue() * 1000);     // Convert s to ms
//                        System.out.printf("backlog bound   : %.2f %n", sfa.getBacklogBound().doubleValue());
                        // compute service max flow delay
                        maxDelay = Math.max(sfa.getDelayBound().doubleValue(), maxDelay);
                    } catch (Exception e) {
                        System.out.println("SFA analysis failed");
                        e.printStackTrace();
                    }
                }
                System.out.printf("Max service delay for %s is %.2fms (deadline: %.2fms) %n", sgs.getName(), maxDelay * 1000, sgs.getDeadline() * 1000);
                if (sgs.getDeadline() < maxDelay){
                    System.err.printf("Service %s deadline not met (%.2fms/%.2fms) %n", sgs.getName(), maxDelay * 1000, sgs.getDeadline() * 1000);
                    delayTorn = true;
                }
            }
            return delayTorn;
        }
        catch (StackOverflowError e){
            System.err.println("Stackoverflow error detected! Possible reason: Cyclic dependency in network.");
            return true;
        }
    }

    /**
     * Test case which does a network calculus analysis after adding each flow.
     * @param sg ServerGraph which includes the servers & turns already
     * @param sgServiceList List of all available SGServices from which the flows shall be derived.
     */
    private void testFlowAfterFlow(ServerGraph sg, List<SGService> sgServiceList) {
        // Get the total number of flows first
        int maxFlow = 0;
        for (SGService service : sgServiceList){
            maxFlow += service.getMultipath().size();
        }

        for (int nmbFlow = 1; nmbFlow <= maxFlow; nmbFlow++) {
            addFlowsToSG(sg, sgServiceList, nmbFlow);
            // Safe the server graph
            this.serverGraph = sg;
            System.out.printf("%d Flows %n", sg.getFlows().size());

            calculateNCDelays();

            // Delete the flows
            for(Flow flow : sg.getFlows()){
                try {
                    sg.removeFlow(flow);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            for (SGService service : sgServiceList){
                service.getFlows().clear();
            }
        }
    }

    /**
     * Test case which does a network calculus analysis after adding each flow.
     * @param sg ServerGraph which includes the servers & turns already
     * @param sgServiceList List of all available SGServices from which the flows shall be derived.
     */
    private void testFlowPairs(ServerGraph sg, List<SGService> sgServiceList) {
        // Get the total number of flows first
        int maxFlow = 0;
        for (SGService service : sgServiceList){
            maxFlow += service.getMultipath().size();
        }

        List<SGService> sgServiceListPre  = new ArrayList<>();
        // Modify "max_depth" according to the test case you want to simulate
        recursiveCallFnc(sg, sgServiceList, sgServiceListPre, 1, 3);
    }

    /**
     * This function is used to test different combinations of flows. The function is meant as a recursive call, initialize the {@code curr_depth} with 1.
     * @param sg Disco server graph to use
     * @param sgServiceList total list of SGS
     * @param servicesCumulated List of services already accumulated by previous recursive calls. Call with empty list as initial call.
     * @param curr_depth current recursion depth. Initialize with 1 in initial call.
     * @param max_depth maximal recursion depth aka number of flows per combination.
     */
    private void recursiveCallFnc(ServerGraph sg, List<SGService> sgServiceList, List<SGService> servicesCumulated, int curr_depth, int max_depth) {
        for (int serviceCntInner = 0; serviceCntInner < sgServiceList.size(); serviceCntInner++){
            SGService serviceInner = sgServiceList.get(serviceCntInner);
            // Iterate over every flow in this service in outer loop
            for (int flowCntInner = 0; flowCntInner < serviceInner.getMultipath().size(); flowCntInner++){
                List<String> pathInner = serviceInner.getMultipath().get(flowCntInner);
                // Add those two to the network and calculate
                List<List<String>> newPathListInner = new ArrayList<>();
                newPathListInner.add(pathInner);
                SGService serviceNewInner = new SGService(serviceInner.getName(), serviceInner.getServer(), serviceInner.getBucket_size(), serviceInner.getBitrate(), serviceInner.getDeadline(), newPathListInner);
                // Add the two flows to the network
                List<SGService> sgServicesCompare = new ArrayList<>(servicesCumulated);
                sgServicesCompare.add(serviceNewInner);

                if(curr_depth >= max_depth) {
                    // Do the final computation
                    this.sgServices = sgServicesCompare;
                    addFlowsToSG(sg, sgServicesCompare, -1);
                    // Safe the server graph
                    this.serverGraph = sg;
                    System.out.printf("%d Flows %n", sg.getFlows().size());

                    calculateNCDelays();

                    // Delete the flows
                    for (Flow flow : sg.getFlows()) {
                        try {
                            sg.removeFlow(flow);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                    for (SGService service : this.sgServices) {
                        service.getFlows().clear();
                    }
                } else {
                    recursiveCallFnc(sg, sgServiceList, sgServicesCompare, curr_depth + 1, max_depth);
                }

            }
        }
    }

    /**
     * Special test case for the presentation scenario, using the "SE" service, path "F23 - S1" and
     * the "LM" service, path "F12 - S2". Only adding those two flows, results in a stackoverflow.
     * @param sg ServerGraph which includes the servers & turns already
     */
    private void testBidirectionalFlow(ServerGraph sg) {
        {
            SGService service = sgServices.get(0);  // "SE" service
            // Create arrival curve with specified details
            ArrivalCurve arrival_curve = Curve.getFactory().createTokenBucket(service.getBitrate(), service.getBucket_size());

            int pathIdx = 4; // path "F23 - S1"
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
                throw new RuntimeException(e);
            }
        }
        {
            SGService service = sgServices.get(3);  // "LM" service
            // Create arrival curve with specified details
            ArrivalCurve arrival_curve = Curve.getFactory().createTokenBucket(service.getBitrate(), service.getBucket_size());

            int pathIdx = 0; // Path "F23 - S1"
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
                throw new RuntimeException(e);
            }
        }
        // Safe the server graph
        this.serverGraph = sg;
        System.out.printf("%d Flows %n", sg.getFlows().size());

        calculateNCDelays();

        // Delete the flows
        for(Flow flow : sg.getFlows()){
            try {
                sg.removeFlow(flow);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        for (SGService service : sgServices){
            service.getFlows().clear();
        }
    }
}
