package org.nd4j.autodiff.listeners;

import org.nd4j.autodiff.functions.DifferentialFunction;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.autodiff.samediff.internal.SameDiffOp;
import org.nd4j.autodiff.samediff.internal.Variable;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;

public abstract class BaseListener implements Listener {

    @Override
    public void epochStart(SameDiff sd, At at) {
        //No op
    }

    @Override
    public void epochEnd(SameDiff sd, At at) {
        //No op
    }

    @Override
    public void iterationStart(SameDiff sd, At at, MultiDataSet data, long etlMs) {
        //No op
    }

    @Override
    public void iterationDone(SameDiff sd, At at, MultiDataSet dataSet, Loss loss) {
        //No op
    }

    @Override
    public void opExecution(SameDiff sd, At at, SameDiffOp op, DifferentialFunction df, INDArray[] outputs) {
        //No op
    }

    @Override
    public void postUpdate(SameDiff sd, At at, Variable v, INDArray update) {
        //No op
    }
}
