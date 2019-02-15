package org.nd4j.autodiff.samediff.ops;

import org.nd4j.autodiff.functions.DifferentialFunctionFactory;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;

public abstract class SDOps {

    protected final SameDiff sd;

    public SDOps(SameDiff sameDiff) {
        this.sd = sameDiff;
    }

    protected DifferentialFunctionFactory f() {
        return sd.f();
    }

    protected SDVariable updateVariableNameAndReference(SDVariable varToUpdate, String newVarName) {
        return sd.updateVariableNameAndReference(varToUpdate, newVarName);
    }

}
