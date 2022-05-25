public class Edge {
    private String node1, node2;
    private double bitrate;
    private double latency;

    public Edge(String node1, String node2, double bitrate, double latency) {
        this.node1 = node1;
        this.node2 = node2;
        this.bitrate = bitrate;
        this.latency = latency;
    }
}
