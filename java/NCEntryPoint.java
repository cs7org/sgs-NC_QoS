import org.networkcalculus.dnc.AnalysisConfig;
import org.networkcalculus.dnc.curves.ArrivalCurve;
import org.networkcalculus.dnc.curves.Curve;
import org.networkcalculus.dnc.curves.ServiceCurve;
import org.networkcalculus.dnc.network.server_graph.Flow;
import org.networkcalculus.dnc.network.server_graph.Server;
import org.networkcalculus.dnc.network.server_graph.ServerGraph;
import org.networkcalculus.dnc.tandem.TandemAnalysis;
import org.networkcalculus.dnc.tandem.analyses.TandemMatchingAnalysis;
import py4j.GatewayServer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Order of calls to make for a fully functioning NC calculation:
 * 1. addEdge() [multiple times] - Add your edges of the network to the Servergraph (Edge = Output-link of one hop)
 * 2. addSGService() [multiple times] - Add all the SGS that will use the network. Flows are aggregated in here.
 * 3. createNCNetwork() [once] - Create the final Servergraph with every connection in it.
 * 4. calculateNCDelays() - Call this function for calculating the final delays per flow.
 */

public class NCEntryPoint {
    private final List<Edge> edgeList = new ArrayList<>();
    private final ExperimentConfig experimentConfig = new ExperimentConfig();
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
     * Retrieve all connected neighbors of a specific edge
     *
     * @param currEdge edge, for which the neighbors shall be found
     * @param edgeList list of edges to search in.
     * @return list of found neighbors.
     */
    private static List<Edge> getAllConnectingEdges(Edge currEdge, Collection<Edge> edgeList) {
        List<Edge> targetEdgeList;
        HashSet<String> currEdgeNodes = new HashSet<>(currEdge.getNodes());
        /*
         * Check if the edges are connected. They are connected if the last node in an edge is the first node in the second node
         * e.g. R10/R20 & R20/R30
         * The first two comparisons: Check for connecting node
         * The third line: Check that the two compared edges do not concern the same node pairs
         *                 (aka are the same edge but maybe different direction --> R10/R20 & R20/R10)
         */
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
     * Bitrate + latency will be later used for modeling the link as a service curve
     *
     * @param node1   first node
     * @param node2   second node
     * @param bitrate link bitrate [Byte/s]
     * @param latency link delay [s]
     */
    @SuppressWarnings("unused")
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
    @SuppressWarnings("unused")
    public void addSGService(String SGSName, String servername, int bucket_size, int bitrate, double deadline, List<List<String>> multipath, int priority) {
        // 0 is always the highest priority
        if (priority < 0){
            priority = 0;
        }
        if (priority >= FlowPriority.values().length){
            priority = FlowPriority.values().length - 1;    // lowest possible priority
        }
        FlowPriority priority_enum = FlowPriority.values()[priority];
        SGService service = new SGService(SGSName, servername, bucket_size, bitrate, deadline, multipath, priority_enum);
        sgServices.add(service);
    }

    /**
     * Reset all stored values (e.g. empty edgelist)
     */
    @SuppressWarnings("unused")
    public void resetAll() {
        edgeList.clear();
        sgServices.clear();
    }

    //TODO: Check for better Exception handling (here "sg.addTurn()" throws exception if servers not present etc.)

    /**
     * This function creates the final ServerGraph aka combines all the network elements in one network.
     * Has to be called last, AFTER calling addEdge & addSGService for adding the network elements.
     */
    @SuppressWarnings("unused")
    public void createNCNetwork() {
        // Create ServerGraph - aka network
        ServerGraph sg = new ServerGraph();

        // Add every edge as a server to the network
        for (Edge edge : edgeList) {
            // Create service curve for this server
            double packetizerBurst = 0;
            if (experimentConfig.usePacketizer) {
                packetizerBurst = experimentConfig.maxPacketSize / edge.getBitrate();
            }
            ServiceCurve service_curve = switch (experimentConfig.serviceCurveType) {
                case CBR ->
                        Curve.getFactory().createRateLatency(edge.getBitrate(), packetizerBurst);    // Constant bit rate element
                case RateLatency ->
                        Curve.getFactory().createRateLatency(edge.getBitrate(), edge.getLatency() + packetizerBurst); // Rate-latency
            };
            // Add server (edge) with service curve to network
            // (Important: Every "Edge"/"Server" in this Java code is unidirectional - not bidirectional!)
            // --> For two-way /bidirectional but independent communication (e.g. switched Ethernet) use the "addEdge"
            // function twice with a switched order of nodes.
            for (FlowPriority prio : FlowPriority.values()){
                String servername = String.join(",", edge.getNodes()) + prio;
                Server serv = sg.addServer(servername, service_curve, experimentConfig.multiplexing);
                // Add server to edge for future references
                // IMPORTANT: The servers have to be added in ascending priority order (HIGH before MEDIUM or LOW)!
                edge.setServer(prio, serv);
            }
        }
        // Add the turns (connections) between the edges to the network
        addTurnsToSG(sg);

        // Add all flows to the network
        addFlowsToSG(sg, sgServices, -1);
        this.serverGraph = sg;
        System.out.printf("%d Flows %n", sg.getFlows().size());
    }

    /**
     * Helper function for adding the turn connections between the edges into a given SererGraph
     *
     * @param sg SerGraph to add all the connections to.
     */
    private void addTurnsToSG(ServerGraph sg) {
        for (Edge currEdge : edgeList) {
            List<Edge> targetEdgeList = getAllConnectingEdges(currEdge, edgeList);
            for (Edge targetEdge : targetEdgeList) {
                // We can just freely add one turn twice, duplicates get omitted by DiscoDNC
                try {
                    // Connect the NC servers according to their priorities --> No priority hoping possible!
                    for (FlowPriority prio : FlowPriority.values()) {
                        sg.addTurn(currEdge.getServer(prio), targetEdge.getServer(prio));
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * This function adds {@code nmbFlow} number of flows to the server graph (sg is modified in place).
     *
     * @param sg            Servergraph to add the flows to.
     * @param sgServiceList List of all available SGServices from which the flows shall be derived.
     * @param nmbFlow       number of flows which should be added. Use "-1" for all available flows.
     */
    private void addFlowsToSG(ServerGraph sg, List<SGService> sgServiceList, int nmbFlow) {
        // nmbFlow = -1 is used to add all available flows.
        if (nmbFlow == -1) {
            nmbFlow = Integer.MAX_VALUE;
        }
        // Add nmbFlow flows to the network (at most the available ones)
        int counter = 0;
        for (SGService service : sgServiceList) {
            // Create arrival curve with specified details
            ArrivalCurve arrival_curve = switch (experimentConfig.arrivalCurveType) {
                case TokenBucket ->
                        Curve.getFactory().createTokenBucket(service.getBitrate(), service.getBucket_size());
                case PeakArrivalRate -> Curve.getFactory().createPeakArrivalRate(service.getBitrate());
            };
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
                    dncPath.add(findEdgebyNodes(edgeList, edgeNodes).getServer(service.getPriority()));
                }
                // Create flow and add it to the network
                try {
                    Flow flow = sg.addFlow(arrival_curve, dncPath);
                    service.addFlow(flow);
                    if (++counter >= nmbFlow) {
                        // Abort adding more flows
                        return;
                    }
                } catch (Exception e) {
                    //TODO: Exception Handling
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Function called by Python part.
     * For details see calculateNCDelays(List<String>)
     *
     * @return boolean if one of the delay constraints is torn
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean calculateNCDelays() {
        List<String> dummyExperimentLog = new ArrayList<>();
        return calculateNCDelays(dummyExperimentLog);
    }

    /**
     * Function to calculate the performance bounds for every flow and output the result onto the Console and into a
     * given experimentLog
     *
     * @param experimentLog List<String> to add the output results into. Intended for CSV usage.
     * @return boolean if one of the delay constraints is torn
     */
    public boolean calculateNCDelays(List<String> experimentLog) {
        // The AnalysisConfig can be used to modify different analysis parameters, e.g. the used arrival bounding method
        // or to enforce Multiplexing strategies on the servers.
        AnalysisConfig configuration = new AnalysisConfig();
        configuration.setArrivalBoundMethod(experimentConfig.arrivalBoundMethod);
        // Current work-around for the experiment run
        if (experimentConfig.multiplexing == AnalysisConfig.Multiplexing.ARBITRARY) {
            configuration.enforceMultiplexing(AnalysisConfig.MultiplexingEnforcement.GLOBAL_ARBITRARY);
        } else {
            configuration.enforceMultiplexing(AnalysisConfig.MultiplexingEnforcement.SERVER_LOCAL);
        }
        experimentConfig.outputConfig();
        experimentConfig.writeConfiginBuffer(experimentLog);
        boolean delayTorn = false;
        try {
            System.out.printf("------ Starting NC Analysis using " + experimentConfig.ncAnalysisType + " ------%n");
            for (SGService sgs : sgServices) {
                double maxDelay = 0;
                System.out.printf("--- Analyzing SGS \"%s\" ---%n", sgs.getName());
                experimentLog.add(sgs.getName());
                for (Flow foi : sgs.getFlows()) {
                    System.out.printf("- Analyzing flow \"%s\" -%n", foi);
                    try {
                        TandemAnalysis ncanalysis = switch (experimentConfig.ncAnalysisType) {
                            case TFA -> TandemAnalysis.performTfaEnd2End(this.serverGraph, configuration, foi);
                            case SFA -> TandemAnalysis.performSfaEnd2End(this.serverGraph, configuration, foi);
                            case PMOO -> TandemAnalysis.performPmooEnd2End(this.serverGraph, configuration, foi);
                            case TMA -> new TandemMatchingAnalysis(this.serverGraph, configuration);
                        };
                        // TMA doesn't have the convenience function
                        if (experimentConfig.ncAnalysisType == TandemAnalysis.Analyses.TMA) {
                            ncanalysis.performAnalysis(foi);
                        }
                        // Get the foi delay
                        double foi_delay = ncanalysis.getDelayBound().doubleValue(); // delay is in s
                        // Calculate propagation delay (if no propagation delay is desired, configuration value is set to 0)
                        double prop_delay = experimentConfig.propagationDelay * foi.getPath().numServers();
                        // Add propagation delay to delay bound
                        foi_delay += prop_delay;
                        // Print the end-to-end delay bound
                        System.out.printf("delay bound     : %.2fms %n", foi_delay * 1000);     // Convert s to ms
//                        System.out.printf("backlog bound   : %.2f %n", sfa.getBacklogBound().doubleValue());
                        experimentLog.add(String.valueOf(foi_delay * 1000));
                        // compute service max flow delay
                        maxDelay = Math.max(foi_delay, maxDelay);
                    } catch (Exception e) {
                        // Here we land e.g. when we have PMOO & FIFO!
                        System.out.println(experimentConfig.ncAnalysisType + " analysis failed");
                        e.printStackTrace();
                        experimentLog.add("-1");
                    }
                }
                System.out.printf("Max service delay for %s is %.2fms (deadline: %.2fms) %n", sgs.getName(), maxDelay * 1000, sgs.getDeadline() * 1000);
                if (sgs.getDeadline() < maxDelay) {
                    System.err.printf("Service %s deadline not met (%.2fms/%.2fms) %n", sgs.getName(), maxDelay * 1000, sgs.getDeadline() * 1000);
                    delayTorn = true;
                }
            }
            return delayTorn;
        } catch (StackOverflowError e) {
            System.err.println("Stackoverflow error detected! Possible reason: Cyclic dependency in network.");
            return true;
        }
    }

    /**
     * This function tries every network analysis method, combined with every arrival bounding technique
     * The result will be exported to the newly created folder "experiments" as a CSV file.
     */
    @SuppressWarnings("unused")
    public void experimentAllCombinations() {
        List<String> experimentLog = new ArrayList<>();
        for (TandemAnalysis.Analyses anaType : TandemAnalysis.Analyses.values()) {
            if ((anaType == TandemAnalysis.Analyses.PMOO) || (anaType == TandemAnalysis.Analyses.TMA)) {
                // PMOO & TMA don't support FIFO multiplexing
                experimentConfig.multiplexing = AnalysisConfig.Multiplexing.ARBITRARY;
            }
            experimentConfig.ncAnalysisType = anaType;
            for (var arrBoundType : AnalysisConfig.ArrivalBoundMethod.values()) {
                List<String> buffer = new ArrayList<>();
                experimentConfig.arrivalBoundMethod = arrBoundType;
                // conduct the experiment with the newly defined configurations
                calculateNCDelays(buffer);
                if (experimentLog.isEmpty()) {
                    experimentLog = buffer;
                } else {
                    // Concat the new results to form one big CSV
                    for (int i = 0; i < experimentLog.size(); i++) {
                        experimentLog.set(i, experimentLog.get(i) + ',' + buffer.get(i));
                    }
                }
            }
        }
        // Export experimentLog to a file
        try {
            String fileSuffix = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            // Create the experiments' subfolder, if not present
            File directory = new File("experiments");
            if (!directory.exists()) {
                boolean success = directory.mkdir();
            }
            FileWriter writer = new FileWriter("experiments/experimentLog_" + fileSuffix + ".csv");
            for (String str : experimentLog) {
                writer.write(str + System.lineSeparator());
            }
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Test case which does a network calculus analysis after adding each flow.
     *
     * @param sg            ServerGraph which includes the servers & turns already
     * @param sgServiceList List of all available SGServices from which the flows shall be derived.
     */
    @SuppressWarnings("unused")
    private void testFlowAfterFlow(ServerGraph sg, List<SGService> sgServiceList) {
        // Get the total number of flows first
        int maxFlow = 0;
        for (SGService service : sgServiceList) {
            maxFlow += service.getMultipath().size();
        }

        for (int nmbFlow = 1; nmbFlow <= maxFlow; nmbFlow++) {
            addFlowsToSG(sg, sgServiceList, nmbFlow);
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
            for (SGService service : sgServiceList) {
                service.getFlows().clear();
            }
        }
    }

    /**
     * Test case which does a network calculus analysis after adding each flow.
     *
     * @param sg            ServerGraph which includes the servers & turns already
     * @param sgServiceList List of all available SGServices from which the flows shall be derived.
     */
    @SuppressWarnings("unused")
    private void testFlowPairs(ServerGraph sg, List<SGService> sgServiceList) {
        // Get the total number of flows first
        int maxFlow = 0;
        for (SGService service : sgServiceList) {
            maxFlow += service.getMultipath().size();
        }

        List<SGService> sgServiceListPre = new ArrayList<>();
        // Modify "max_depth" according to the test case you want to simulate
        recursiveCallFnc(sg, sgServiceList, sgServiceListPre, 1, 3);
    }

    /**
     * This function is used to test different combinations of flows. The function is meant as a recursive call, initialize the {@code curr_depth} with 1.
     *
     * @param sg                Disco server graph to use
     * @param sgServiceList     total list of SGS
     * @param servicesCumulated List of services already accumulated by previous recursive calls. Call with empty list as initial call.
     * @param curr_depth        current recursion depth. Initialize with 1 in initial call.
     * @param max_depth         maximal recursion depth aka number of flows per combination.
     */
    private void recursiveCallFnc(ServerGraph sg, List<SGService> sgServiceList, List<SGService> servicesCumulated, int curr_depth, int max_depth) {
        for (int serviceCntInner = 0; serviceCntInner < sgServiceList.size(); serviceCntInner++) {
            SGService serviceInner = sgServiceList.get(serviceCntInner);
            // Iterate over every flow in this service in outer loop
            for (int flowCntInner = 0; flowCntInner < serviceInner.getMultipath().size(); flowCntInner++) {
                List<String> pathInner = serviceInner.getMultipath().get(flowCntInner);
                // Add those two to the network and calculate
                List<List<String>> newPathListInner = new ArrayList<>();
                newPathListInner.add(pathInner);
                SGService serviceNewInner = new SGService(serviceInner.getName(), serviceInner.getServer(), serviceInner.getBucket_size(), serviceInner.getBitrate(), serviceInner.getDeadline(), newPathListInner, serviceInner.getPriority());
                // Add the two flows to the network
                List<SGService> sgServicesCompare = new ArrayList<>(servicesCumulated);
                sgServicesCompare.add(serviceNewInner);

                if (curr_depth >= max_depth) {
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
     *
     * @param sg ServerGraph which includes the servers & turns already
     */
    @SuppressWarnings("unused")
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
        for (Flow flow : sg.getFlows()) {
            try {
                sg.removeFlow(flow);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        for (SGService service : sgServices) {
            service.getFlows().clear();
        }
    }

    enum ServiceCurveTypes {
        CBR, RateLatency
    }

    enum ArrivalCurveTypes {
        TokenBucket, PeakArrivalRate
    }

    /**
     * Used scheduling policy for multiple priority case
     */
    enum SchedulingPolicy{
        None, SP, DRR, WRR
    }

    /**
     * Class used to store the experiment configurations.
     */
    private static class ExperimentConfig {
        public final ServiceCurveTypes serviceCurveType = ServiceCurveTypes.CBR;
        public final boolean usePacketizer = true;
        public final double propagationDelay = 0.5E-6; // 0.5 us, but has to be defined in [s]
        public final int maxPacketSize = 255; // [Byte]
        public final ArrivalCurveTypes arrivalCurveType = ArrivalCurveTypes.TokenBucket;
        //NOTE: The multiplexing technique can be set per Server, when taking arbitrary, it will be forced globally
        // (in calculateNCDelays)
        public AnalysisConfig.Multiplexing multiplexing = AnalysisConfig.Multiplexing.FIFO;
        public AnalysisConfig.ArrivalBoundMethod arrivalBoundMethod = AnalysisConfig.ArrivalBoundMethod.AGGR_PBOO_CONCATENATION;
        public TandemAnalysis.Analyses ncAnalysisType = TandemAnalysis.Analyses.SFA;
        public SchedulingPolicy schedulingPolicy = SchedulingPolicy.None;

        /**
         * Write the current experiment configuration onto the Console
         */
        public void outputConfig() {
            System.out.println("Service curve type: " + serviceCurveType);
            System.out.println("Packetizer included: " + usePacketizer + " (" + maxPacketSize + " Byte)");
            System.out.println("Propagation delay: " + propagationDelay);
            System.out.println("Arrival curve type: " + arrivalCurveType);
            System.out.println("Multiplexing: " + multiplexing);
            System.out.println("Arrival bounding method: " + arrivalBoundMethod);
            System.out.println("NC Analysis type: " + ncAnalysisType);
        }

        /**
         * Write the current experiment configuration line by line into a buffer
         *
         * @param buffer Buffer to write the experiment configuration into
         */
        public void writeConfiginBuffer(List<String> buffer) {
            buffer.add(String.valueOf(serviceCurveType));
            buffer.add(String.valueOf(usePacketizer));
            buffer.add(String.valueOf(maxPacketSize));
            buffer.add(String.valueOf(propagationDelay));
            buffer.add(String.valueOf(arrivalCurveType));
            buffer.add(String.valueOf(multiplexing));
            buffer.add(String.valueOf(arrivalBoundMethod));
            buffer.add(String.valueOf(ncAnalysisType));
        }
    }
}
