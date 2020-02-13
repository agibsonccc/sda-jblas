package org.nd4j.linalg.api.ops.impl.layers.recurrent.weights;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.layers.recurrent.GRUCell;

/**
 * The weight configuration of a GRU cell.  For {@link GRUCell}.
 *
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
public class GRUWeights extends RNNWeights {

    /**
     * Reset and Update gate weights, with a shape of [inSize + numUnits, 2*numUnits].
     *
     * The reset weights are the [:, 0:numUnits] subset and the update weights are the [:, numUnits:2*numUnits] subset.
     */
    private SDVariable ruWeight;

    /**
     * Cell gate weights, with a shape of [inSize + numUnits, numUnits]
     */
    private SDVariable cWeight;

    /**
     * Reset and Update gate bias, with a shape of [2*numUnits].  May be null.
     *
     * The reset bias is the [0:numUnits] subset and the update bias is the [numUnits:2*numUnits] subset.
     */
    private SDVariable ruBias;

    /**
     * Cell gate bias, with a shape of [numUnits].  May be null.
     */
    private SDVariable cBias;

    private INDArray ndarrayRuWeight;
    private INDArray ndarrayCWeight;
    private INDArray ndarrayRuBias;
    private INDArray ndarrayCBias;


    @Override
    public SDVariable[] args() {
        return filterNonNull(ruWeight, cWeight, ruBias, cBias);
    }
    @Override
    public INDArray[] ndarrayArgs() {
        return filterNonNull(ndarrayRuWeight, ndarrayCWeight, ndarrayRuBias, ndarrayCBias);
    }
}
