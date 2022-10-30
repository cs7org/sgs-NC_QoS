import org.networkcalculus.dnc.AnalysisConfig;
import org.networkcalculus.dnc.tandem.TandemAnalysis;

import java.util.Arrays;
import java.util.List;

/**
 * Class used to store the experiment configurations.
 */
public class ExperimentConfig {
    public final ServiceCurveTypes serviceCurveType = ServiceCurveTypes.CBR;
    public final boolean usePacketizer = true;
    public final double propagationDelay = 0.5E-6; // 0.5 us, but has to be defined in [s]
    public final int maxPacketSize = 255; // [Byte]
    public final int minPacketSize = 255; // [Byte]  //TODO: Change me
    public final ArrivalCurveTypes arrivalCurveType = ArrivalCurveTypes.TokenBucket;
    //NOTE: The multiplexing technique can be set per Server, when taking arbitrary, it will be forced globally
    // (in calculateNCDelays)
    public AnalysisConfig.Multiplexing multiplexing = AnalysisConfig.Multiplexing.FIFO;
    public AnalysisConfig.ArrivalBoundMethod arrivalBoundMethod = AnalysisConfig.ArrivalBoundMethod.AGGR_PBOO_CONCATENATION;
    public TandemAnalysis.Analyses ncAnalysisType = TandemAnalysis.Analyses.SFA;
    public SchedulingPolicy schedulingPolicy = SchedulingPolicy.DRR;
    public int[] flowWeights = {1, 1, 1};   // Flow weights in the order [H, M, L] - used for WFQ & WRR
    public final int[] flowQuantils = {maxPacketSize, maxPacketSize, maxPacketSize}; // Flow quantils, used by DRR


    /**
     * Write the current experiment configuration onto the Console
     */
    public void outputConfig() {
        System.out.println("Service curve type: " + serviceCurveType);
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
        buffer.add(String.valueOf(serviceCurveType));
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
        None, SP, WFQ, DRR, WRR
    }
}
