import org.networkcalculus.dnc.AnalysisConfig;
import org.networkcalculus.dnc.tandem.TandemAnalysis;

import java.util.Arrays;
import java.util.List;

/**
 * Class used to store the experiment configurations.
 */
public class ExperimentConfig {
    /**
     * If the link delay parameter, given by the {@link NCEntryPoint#addEdge(String, String, double, double)}
     * shall be added into the service curve or not.
     */
    public final boolean useGivenLinkDelay = false;
    /**
     * If a packetizer shall be used in the system. Changes the definition of service curves.
     */
    public final boolean usePacketizer = true;
    /**
     * Value of the propagation delay. Is added onto the bound delay in the analysis phase.
     */
    public final double propagationDelay = 0.5E-6; // 0.5 us, but has to be defined in [s]
    /**
     * Maximum packet size in the system. Used for service curve modeling.
     */
    public final int maxPacketSize = 255; // [Byte]
    /**
     * Minimum packet size in the system. Used for the definition of WRR.
     */
    public final int minPacketSize = 255; // [Byte]  //TODO: Change me
    /**
     * How the arrival of the flows shall be modeled. Mainly Peak-rate vs TokenBucket.
     * Token-Bucket uses one l_max as bucket size.
     */
    public final ArrivalCurveTypes arrivalCurveType = ArrivalCurveTypes.TokenBucket;
    //NOTE: The multiplexing technique can be set per Server, when taking arbitrary, it will be forced globally
    // (in calculateNCDelays)
    /**
     * Multiplexing used in the NC servers.
     * Attention: Only TFA and SFA support FIFO, the others have to be ARBITRARY.
     */
    public AnalysisConfig.Multiplexing multiplexing = AnalysisConfig.Multiplexing.FIFO;
    /**
     * Method for the arrival bounding. Used in the analysis phase, has influence on the tightness of the delays.
     * Attention: Some Arrival Bounding methods do not support FIFO multiplexing (only ARBITRARY).
     */
    public AnalysisConfig.ArrivalBoundMethod arrivalBoundMethod = AnalysisConfig.ArrivalBoundMethod.AGGR_PBOO_CONCATENATION;
    /**
     * Analysis method to be used.
     */
    public TandemAnalysis.Analyses ncAnalysisType = TandemAnalysis.Analyses.SFA;
    /**
     * Scheduling policy to be used. Influences the definition of the service curves.
     */
    public SchedulingPolicy schedulingPolicy = SchedulingPolicy.DRR;
    /**
     * Flow weights, used for WFQ and WRR. Length of the array should correspond to number of flow priorities.
     * First value corresponds to the highest priority with decreasing order afterwards.
     */
    public int[] flowWeights = {1, 1, 1};   // Flow weights in the order [H, M, L] - used for WFQ & WRR
    /**
     * Flow quantils, used by DRR. Same notes as for {@link #flowWeights} apply.
     */
    public final int[] flowQuantils = {maxPacketSize, maxPacketSize, maxPacketSize}; // Flow quantils, used by DRR


    /**
     * Write the current experiment configuration onto the Console
     */
    public void outputConfig() {
        System.out.println("Use given link delay: " + useGivenLinkDelay);
        System.out.println("Packetizer included: " + usePacketizer + " (" + maxPacketSize + " Byte)");
        System.out.println("Propagation delay: " + propagationDelay);
        System.out.println("Arrival curve type: " + arrivalCurveType);
        System.out.println("Multiplexing: " + multiplexing);
        System.out.println("Scheduling policy: " + schedulingPolicy);
        System.out.println("Flow weights: " + Arrays.toString(flowWeights));
        System.out.println("Flow quantils: " + Arrays.toString(flowQuantils));
        System.out.println("Arrival bounding method: " + arrivalBoundMethod);
        System.out.println("NC Analysis type: " + ncAnalysisType);
    }

    /**
     * Write the current experiment configuration line by line into a buffer
     *
     * @param buffer Buffer to write the experiment configuration into
     */
    public void writeConfiginBuffer(List<String> buffer) {
        buffer.add(String.valueOf(useGivenLinkDelay));
        buffer.add(String.valueOf(usePacketizer));
        buffer.add(String.valueOf(maxPacketSize));
        buffer.add(String.valueOf(propagationDelay));
        buffer.add(String.valueOf(arrivalCurveType));
        buffer.add(String.valueOf(multiplexing));
        buffer.add(String.valueOf(schedulingPolicy));
        buffer.add(Arrays.toString(flowWeights));
        buffer.add(Arrays.toString(flowQuantils));
        buffer.add(String.valueOf(arrivalBoundMethod));
        buffer.add(String.valueOf(ncAnalysisType));
    }

    /**
     * Self defined enum for the arrival curve modeling choice.
     */
    enum ArrivalCurveTypes {
        TokenBucket, PeakArrivalRate
    }

    /**
     * Used scheduling policy for multiple priority case.
     * 'None' shall be used if no scheduler is desired.
     */
    enum SchedulingPolicy{
        None, SP, WFQ, DRR, WRR
    }
}
