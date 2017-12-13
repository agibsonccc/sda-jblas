package org.deeplearning4j.nn.layers.feedforward.elementwise;


import org.deeplearning4j.nn.params.ElementWiseParamInitializer;
import org.deeplearning4j.exception.DL4JInvalidInputException;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.gradient.DefaultGradient;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.layers.BaseLayer;
import org.deeplearning4j.nn.params.DefaultParamInitializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.primitives.Pair;

import java.util.Arrays;

/**
 * created by jingshu
 */
public class ElementWiseMultiplicationLayer extends BaseLayer<org.deeplearning4j.nn.conf.layers.ElementWiseMultiplicationLayer> {

    public ElementWiseMultiplicationLayer(NeuralNetConfiguration conf){
        super(conf);
    }

    public ElementWiseMultiplicationLayer(NeuralNetConfiguration conf, INDArray input) {
        super(conf, input);
    }

    @Override
    public Pair<Gradient, INDArray> backpropGradient(INDArray epsilon) {
        //If this layer is layer L, then epsilon for this layer is ((w^(L+1)*(delta^(L+1))^T))^T (or equivalent)
        INDArray z = preOutput(true); //Note: using preOutput(INDArray) can't be used as this does a setInput(input) and resets the 'appliedDropout' flag
        INDArray delta = layerConf().getActivationFn().backprop(z, epsilon).getFirst(); //TODO handle activation function params

        if (maskArray != null) {
            applyMask(delta);
        }

        Gradient ret = new DefaultGradient();

        INDArray weightGrad =  gradientViews.get(ElementWiseParamInitializer.WEIGHT_KEY);
        weightGrad.subi(weightGrad);

        weightGrad.addi(input.mul(delta).sum(0));

        INDArray biasGrad = gradientViews.get(ElementWiseParamInitializer.BIAS_KEY);
        delta.sum(biasGrad, 0); //biasGrad is initialized/zeroed first

        ret.gradientForVariable().put(ElementWiseParamInitializer.WEIGHT_KEY, weightGrad);
        ret.gradientForVariable().put(ElementWiseParamInitializer.BIAS_KEY, biasGrad);

//      epsilonNext is a 2d matrix
        INDArray epsilonNext = delta.mulRowVector(params.get(ElementWiseParamInitializer.WEIGHT_KEY));

        return new Pair<>(ret, epsilonNext);
    }

    /**
     * @return The current iteration count (number of parameter updates) for the layer/network
     */
    @Override
    public int getIterationCount() {
        return 0;
    }

    /**
     * @return The current epoch count (number of training epochs passed) for the layer/network
     */
    @Override
    public int getEpochCount() {
        return 0;
    }

    /**
     * Set the current iteration count (number of parameter updates) for the layer/network
     *
     * @param iterationCount
     */
    @Override
    public void setIterationCount(int iterationCount) {

    }

    /**
     * Set the current epoch count (number of epochs passed ) for the layer/network
     *
     * @param epochCount
     */
    @Override
    public void setEpochCount(int epochCount) {

    }

    /**
     * Returns true if the layer can be trained in an unsupervised/pretrain manner (VAE, RBMs etc)
     *
     * @return true if the layer can be pretrained (using fit(INDArray), false otherwise
     */
    @Override
    public boolean isPretrainLayer() {
        return false;
    }

    public INDArray preOutput(boolean training) {
        INDArray b = getParam(DefaultParamInitializer.BIAS_KEY);
        INDArray W = getParam(DefaultParamInitializer.WEIGHT_KEY);

        if ( input.columns() != W.columns()) {
            throw new DL4JInvalidInputException(
                    "Input size (" + input.columns() + " columns; shape = " + Arrays.toString(input.shape())
                            + ") is invalid: does not match layer input size (layer # inputs = "
                            + W.shapeInfoToString() + ") " + layerId());
        }

        applyDropOutIfNecessary(training);

        INDArray ret = Nd4j.zeros(input.rows(),input.columns());

        for(int row = 0; row<input.rows();row++){
            ret.put(new INDArrayIndex[]{NDArrayIndex.point(row), NDArrayIndex.all()},input.getRow(row).mul(W).addRowVector(b));
        }

        if (maskArray != null) {
            applyMask(ret);
        }

        return ret;
    }

}