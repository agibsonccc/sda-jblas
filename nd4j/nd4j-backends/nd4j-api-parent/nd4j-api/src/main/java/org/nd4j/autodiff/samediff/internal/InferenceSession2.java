/*******************************************************************************
 * Copyright (c) 2015-2019 Skymind, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

package org.nd4j.autodiff.samediff.internal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.nd4j.autodiff.functions.DifferentialFunction;
import org.nd4j.autodiff.listeners.At;
import org.nd4j.autodiff.listeners.Listener;
import org.nd4j.autodiff.samediff.SDVariable;
import org.nd4j.autodiff.samediff.SameDiff;
import org.nd4j.autodiff.samediff.VariableType;
import org.nd4j.autodiff.samediff.internal.memory.SimpleSessionMemoryMgr;
import org.nd4j.base.Preconditions;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.memory.MemoryWorkspace;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.*;
import org.nd4j.linalg.api.ops.executioner.DefaultOpExecutioner;
import org.nd4j.linalg.api.ops.impl.controlflow.compat.*;
import org.nd4j.linalg.api.ops.impl.shape.tensorops.*;
import org.nd4j.linalg.api.ops.impl.transforms.gradient.GradientBackwardsMarker;
import org.nd4j.linalg.api.ops.impl.transforms.same.Identity;
import org.nd4j.linalg.api.shape.LongShapeDescriptor;
import org.nd4j.linalg.api.shape.Shape;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.exception.ND4JIllegalStateException;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.util.ArrayUtil;

import java.io.LineNumberReader;
import java.util.*;

/**
 * InferenceSession: Performs inference (forward pass) on a SameDiff instance to get the outputs of the requested nodes.
 * Dynamically (in AbstractSession) calculates the required subgraph to execute to get the required outputs.
 *
 * @author Alex Black
 */
@Slf4j
public class InferenceSession2 extends AbstractSession<INDArray,SameDiffOp> {
    private static final String SCOPE_PANIC_MSG = "If required, arrays in workspaces can be detached using INDArray.detach() before being passed to the SameDiff instance.\n" +
            "Alternatively, arrays defined in a workspace must be replaced after the workspace has been closed.";

    private SessionMemMrg mmgr;
    private DependencyTracker<Array,Dep> arrayUseTracker = new DependencyTracker<>();       //What needs to happen before the array can be closed?


    public InferenceSession2(@NonNull SameDiff sameDiff) {
        super(sameDiff);

        mmgr = new SimpleSessionMemoryMgr();
    }

    @Override
    protected Map<String,INDArray> preprocessPlaceholders(Map<String,INDArray> placeholders){
        //Handle casting of the input array automatically.
        //The idea here is to avoid unexpected errors if the user (for example) tries to perform inference with a double
        // array for a float placeholder
        if(placeholders == null || placeholders.isEmpty()){
            return placeholders;
        }

        Map<String,INDArray> out = new HashMap<>();
        for(Map.Entry<String,INDArray> e : placeholders.entrySet()){
            Preconditions.checkState(sameDiff.hasVariable(e.getKey()), "Invalid placeholder passed for execution: " +
                    "No variable/placeholder with name %s exists", e.getKey());
            INDArray arr = e.getValue();
            //First: check workspaces
            if(arr.isAttached()){
                MemoryWorkspace ws = arr.data() == null ? null : arr.data().getParentWorkspace();
                if (ws != null && ws.getWorkspaceType() != MemoryWorkspace.Type.CIRCULAR) {
                    if (!ws.isScopeActive()) {
                        throw new ND4JIllegalStateException("Placeholder \"" + e.getKey() + "\" array uses leaked workspace pointer from workspace ["
                                + ws.getId() + "]: Workspace the array was defined in is no longer open.\nAll open workspaces: " + DefaultOpExecutioner.allOpenWorkspaces()
                                + "\n" + SCOPE_PANIC_MSG);
                    }

                    if (ws.getGenerationId() != arr.data().getGenerationId())
                        throw new ND4JIllegalStateException("Placeholder \"" + e.getKey() + "\" array uses outdated workspace pointer from workspace ["
                                + ws.getId() + "]: Workspace array was defined in has been closed and reopened at least once since array creation. Array WS iteration: " +
                                arr.data().getGenerationId() + ". Workspace current iteration: " +
                                ws.getGenerationId() + "\nAll open workspaces: " + DefaultOpExecutioner.allOpenWorkspaces() + "\n" + SCOPE_PANIC_MSG);
                }
            }


            //Second: cast the input to the required type
            DataType dt = sameDiff.getVariable(e.getKey()).dataType();
            if(arr.dataType() != dt){
                arr = arr.castTo(dt);
            }
            out.put(e.getKey(), arr);
        }

        return out;
    }

    @Override
    protected Map<String,INDArray> postProcessOutput(Map<String,INDArray> output){
        /*
        Clear op array references. Why? Consider the following graph:
        X -> Y -> Z, where X is a placeholder, variable or constant
        On first call, user requests output Y, and stores it in a reference in user code.
        On second call, user requests Z. Y is deallocated internally (memory reuse), but the user still has a reference to it
        Then then tries to use Y and can't (exception - closed/released etc)
         */
        for(String s : output.keySet()){
            if(sameDiff.getVariable(s).getVariableType() == VariableType.ARRAY){
                clearOpReferencesFor(s);
            }
        }

        return output;
    }

    @Override
    public INDArray[] getOutputs(SameDiffOp op, FrameIter outputFrameIter, Set<VarId> opInputs, Set<VarId> allIterInputs,
                                 Set<String> constAndPhInputs, List<Listener> listeners, At at, MultiDataSet batch, Set<String> allReqVariables) {
        if(listeners != null && listeners.size() > 0){
            SameDiffOp sdOp = sameDiff.getOps().get(op.getOp().getOwnName());
            for(Listener l : listeners){
                if(l.isActive(at.operation()))
                    l.preOpExecution(sameDiff, at, sdOp);
            }
        }

        INDArray[] out = getOutputsHelper(op.getOp(), outputFrameIter, opInputs, allIterInputs, constAndPhInputs);

        //Call listeners
        if(listeners != null && listeners.size() > 0){
            Map<String, INDArray> namedOuts = null;

            for(Listener l : listeners){
                if(l.isActive(at.operation())) {
                    //Lazily create map, only if required
                    if(namedOuts == null){
                        Map<String, INDArray> namedOutsBuilder = new HashMap<>();

                        for(int i = 0 ; i < out.length ; i++)
                            namedOutsBuilder.put(op.outputsOfOp.get(i), out[i]);
                        namedOuts = Collections.unmodifiableMap(namedOutsBuilder);
                    }


                    l.opExecution(sameDiff, at, batch, op, out);

                    for(String varName : namedOuts.keySet()){
                        l.activationAvailable(sameDiff, at, batch, op, varName, namedOuts.get(varName));
                    }
                }
            }
        }

        /*
        Check if any arrays can be deallocated.
        We use array dependency tracking for this
         */

        //Step 1: Update dependencies - add new dependencies
        List<String> opOutputs = op.getOutputsOfOp();
        for( int i=0; i<opOutputs.size(); i++ ){
            //Add the dependencies: What has to be executed, before we can close this just calculated array?
            String outVarName = opOutputs.get(i);
            List<String> thisOutputUsedForOps = sameDiff.getVariables().get(outVarName).getInputsForOp();
            if(thisOutputUsedForOps != null){
                Array arr = new Array(outVarName, outputFrameIter.getFrame(), outputFrameIter.getIteration(), outputFrameIter.getParentFrame());
                for( String opName : thisOutputUsedForOps ){
                    SameDiffOp o = sameDiff.getOps().get(opName);
                    DifferentialFunction df = o.getOp();

                    Dep opDep;
                    if(df instanceof Enter) {
                        //Enter op: forwards input to specified frame, iteration 0
                        //This is a zero-copy operation
                        //Note that the enter value should be available for ALL iterations within the new frame - which means
                        // we need a frame/iter dependency, not a standard variable dependency
                        //Also because it's a zero-copy operation, we need a dependent alias for the input array

                        String inName = op.getInputsToOp().get(0);
                        VarId inVid = lookup(inName, opInputs, allIterInputs, false);
                        Array inArr = new Array(inName, inVid.getFrame(), inVid.getIteration(), inVid.getParentFrame());
                        arrayUseTracker.addDependentAlias(inArr, arr);

                        //Frame dependency - Enter output (and input, via alias) required for all iterations
                        opDep = new FrameDep(outputFrameIter.getFrame(), outputFrameIter.getParentFrame());
                    } else if(df instanceof Exit) {
                        //Exit op: forwards input to parent frame (at parent current iteration)
                        //This is a zero-copy operation, hence we need a dependent alias
                        String inName = op.getInputsToOp().get(0);
                        VarId inVid = lookup(inName, opInputs, allIterInputs, false);
                        Array inArr = new Array(inName, inVid.getFrame(), inVid.getIteration(), inVid.getParentFrame());
                        arrayUseTracker.addDependentAlias(inArr, arr);
                        //TODO not 100% sure on the opDep here...
                        opDep = new OpDep(opName, outputFrameIter.getFrame(), outputFrameIter.getIteration(), outputFrameIter.getParentFrame());
                    } else if(df instanceof NextIteration){
                        //NextIteration: same frame, increments iteration
                        //As per enter/exit etc - NextIteration is a zero-copy op, so we need a dependent alias for this

                        String inName = op.getInputsToOp().get(0);
                        VarId inVid = lookup(inName, opInputs, allIterInputs, false);
                        Array inArr = new Array(inName, inVid.getFrame(), inVid.getIteration(), inVid.getParentFrame());
                        arrayUseTracker.addDependentAlias(inArr, arr);

                        opDep = new OpDep(opName, outputFrameIter.getFrame(), outputFrameIter.getIteration(), outputFrameIter.getParentFrame());
                    } else {
                        //Normal case (standard ops) - and switch/merge cases
                        opDep = new OpDep(opName, outputFrameIter.getFrame(), outputFrameIter.getIteration(), outputFrameIter.getParentFrame());

                        if( df instanceof Identity ) {
                            //Identity: array is passed through unchanged (same array, zero copy)
                            //This is fine, but we need a dependent alias. So, for arrays X -> (identity) -> Y then Y is an
                            // alias of X

                            String inName = op.getInputsToOp().get(0);
                            Array inArr = new Array(inName, arr.getFrame(), arr.getIter(), arr.getParentFrame());
                            arrayUseTracker.addDependentAlias(inArr, arr);
                        } else if(df instanceof Switch) {
                            //Switch: Input to switch op is passed through unchanged (same array, zero copy) to ONE of two possible outputs
                            //Regardless of which branch the array is forwarded on to, the input array can be closed when the switch op is executed
                            //However, we need to add a dependee alias: so if we have opX -> switch -> opZ and opX has output array x
                            // then instead of (x -> switch) dependency, we want (x -> opZ). Otherwise, we would (incorrectly)
                            // say that "switch is executed, x can be deallocated"

                            //However, we need to add a dependent alias so if opX -> switch -> opZ, and opX has output x,
                            // and switch has output s1 or s2, then we should mark s1 and s2 as an alias of x
                            //Mark it for both branches, because we don't necessarily know which will be executed at this point
                            Array alias0 = new Array(o.getOutputsOfOp().get(0), arr.getFrame(), arr.getIter(), arr.getParentFrame());
                            arrayUseTracker.addDependentAlias(arr, alias0);

                            Array alias1 = new Array(o.getOutputsOfOp().get(1), arr.getFrame(), arr.getIter(), arr.getParentFrame());
                            arrayUseTracker.addDependentAlias(arr, alias1);
                        } else if(df instanceof Merge){
                            //Merge: 2 op inputs, but only one will (usually) be available when merge is executed
                            //The array for whichever is available is passed through unchanged (same array, zero copy)
                            //As per switch, we need a dependent alias - so if we have (X, Y) -> Merge -> Z
                            // then Z is just an alias for whichever of X or Y actually got executer

                            //First: work out which is actually available...
                            String availableIn = null;
                            for(String s : op.getInputsToOp()){
                                if(constAndPhInputs != null && constAndPhInputs.contains(s)){
                                    availableIn = s;
                                    break;
                                }
                                VarId vid = lookup(s, opInputs, allIterInputs, false);
                                if(vid != null) {
                                    availableIn = s;
                                    break;
                                }
                            }

                            Preconditions.checkState(availableIn != null, "Could not find any inputs for merge op %s, %s", op.getName(), outputFrameIter);

                            Array in = new Array(availableIn, arr.getFrame(), arr.getIter(), arr.getParentFrame());
                            arrayUseTracker.addDependentAlias(in, arr);
                        }
                    }

                    //Add the dependency. This means that "the specified array requires this operation to be executed, before it can be closed/deallocated"
                    arrayUseTracker.addDependency(arr, opDep);
                    log.info("Added dependency: {} -> {}", opDep, arr);
                }
            }
        }

        //Step 2: Update dependencies - remove old dependencies (for just calculated op at given frame/iter)
        List<String> opInVarNames = op.getInputsToOp();
        if(opInVarNames != null){
            //Remove any old dependencies, now that this op has been executed
            OpDep opDep = new OpDep(op.getName(), outputFrameIter.getFrame(), outputFrameIter.getIteration(), outputFrameIter.getParentFrame());

            DifferentialFunction df = op.getOp();
            /*
            Other control ops here:
            - Switch: nothing special about the inputs, these can be updated as per normal ops
            - Merge: only one was ever added as a dependency, but we can remove both (one just isn't present to be removed)
            - Enter/Exit/NextIteration: input/output do have different frame/iter, but this should be handled by lookup method already
             */

            boolean isMerge = df instanceof Merge;


            for( int i=0; i<opInVarNames.size(); i++ ) {
                String n = opInVarNames.get(i);
                VarId inVid;
                if(constAndPhInputs != null && constAndPhInputs.contains(n)){
                    inVid = newVarId(n, OUTER_FRAME, 0, null);
                } else {
                    inVid = lookup(n, opInputs, allIterInputs, !isMerge);
                }

                if(isMerge && inVid == null){
                    //Merge op only has 1 of 2 inputs available usually
                    continue;
                }


                Array arr = new Array(n, inVid.getFrame(), inVid.getIteration(), inVid.getParentFrame());

                //This input array no longer depends on the just executed op - it's one step closer to being able to be deallocated
                arrayUseTracker.removeDependency(arr, opDep);
                log.info("Removed dependency: {} -> {}", opDep, arr);
            }
        }

        //Step 3: Check if we can deallocate any arrays
        if(arrayUseTracker.hasZeroDependencyItem()){
            List<Array> canDealloc = arrayUseTracker.removeAllZeroDependencyItems();
            for(Array a : canDealloc){

                boolean isAlias = arrayUseTracker.isDependentAlias(a);
                SDVariable v = sameDiff.getVariable(a.getVarName());

                Variable var = sameDiff.getVariables().get(v.getVarName());
                SameDiffOp sdop = var.getOutputOfOp() == null ? null : sameDiff.getOps().get(var.getOutputOfOp());

                if(v.getVariableType() == VariableType.ARRAY && !allReqVariables.contains(a.getVarName())){
                    //Can't deallocate placeholders, constants, variables or arrays that the user has requested to be returned
                    log.info("Determined safe to deallocate: {}", a);
                    VarId vid = a.toVarId();
                    INDArray arr = nodeOutputs.get(vid);
                    mmgr.release(arr);
                    clearOpReferencesFor(v.getVarName());
                }
            }
        }

        return out;
    }

    public INDArray[] getOutputsHelper(DifferentialFunction op, FrameIter outputFrameIter, Set<VarId> opInputs, Set<VarId> allIterInputs,
                                       Set<String> constAndPhInputs){

        int totalInputs = (opInputs == null ? 0 : opInputs.size()) + (constAndPhInputs == null ? 0 : constAndPhInputs.size())
                + (allIterInputs == null ? 0 : allIterInputs.size());

        boolean constPhInput = (opInputs == null || opInputs.size() == 0) && (allIterInputs == null || allIterInputs.size() == 0);

        if(op instanceof Identity ) {
            Identity i = (Identity) op;
            String[] argNames = i.argNames();
            Preconditions.checkState(argNames.length == 1, "Expected only 1 arg name in identity op, got %s", argNames);
            VarId vid = newVarId(argNames[0], outputFrameIter);

            INDArray orig = nodeOutputs.get(vid);
            return new INDArray[]{orig};
        } else if(op instanceof Switch) {
            Switch s = (Switch) op;
            String[] argNames = s.argNames();       //Order: input, boolean array
            VarId vidPredicate = newVarId(argNames[1], outputFrameIter);
            INDArray predicate = this.nodeOutputs.get(vidPredicate);
            Preconditions.checkState(predicate.isScalar() && predicate.dataType() == DataType.BOOL, "Expected boolean predicate: got %ndSInfo", predicate);
            VarId vid = newVarId(argNames[0], outputFrameIter);
            if (predicate.getDouble(0) == 0.0) {
                return new INDArray[]{this.nodeOutputs.get(vid), null};
            } else {
                return new INDArray[]{null, this.nodeOutputs.get(vid)};
            }
        } else if(op instanceof Enter) {
            //Enter op: forwards input to specified execution frame
            Enter e = (Enter)op;
            String[] input = e.argNames();
            Preconditions.checkState(input.length == 1, "Expected only 1 arg name for enter op: got %s", input);
            Preconditions.checkState(totalInputs == 1, "Expected exactly 1 op input for Enter op \"%s\", got %s+%s", e.getOwnName(), opInputs, constAndPhInputs);

            VarId inputVarId;
            if(constPhInput) {
                //Constant or placeholder
                inputVarId = new VarId(constAndPhInputs.iterator().next(), OUTER_FRAME, 0, null);
            } else if(allIterInputs != null && allIterInputs.size() > 0){
                inputVarId = allIterInputs.iterator().next();
            } else {
                inputVarId = opInputs.iterator().next();
            }
            INDArray enterInput = this.nodeOutputs.get(inputVarId);

            Preconditions.checkNotNull(enterInput, "Could not get enter op \"%s\" input: output variable %s - %s", e.getOwnName(), e.outputVariablesNames(), outputFrameIter);
            return new INDArray[]{enterInput};
        } else if(op instanceof Exit) {
            //Exit node forwards input to parent frame

            VarId inputVarId;
            if(constPhInput){
                //Constant or placeholder
                inputVarId = new VarId(constAndPhInputs.iterator().next(), OUTER_FRAME, 0, null);
            } else if(allIterInputs != null && allIterInputs.size() > 0){
                inputVarId = allIterInputs.iterator().next();
            } else {
                inputVarId = opInputs.iterator().next();
            }
            INDArray exitInput = this.nodeOutputs.get(inputVarId);
            return new INDArray[]{exitInput};
        } else if(op instanceof NextIteration){
            //NextIteration op: forwards its single input to the output of the current frame, but increments the iteration number
            Preconditions.checkState(totalInputs == 1, "Expected exactly 1 op input for NextIteration: got %s+%s", opInputs, constAndPhInputs);
            VarId in = (allIterInputs != null && !allIterInputs.isEmpty() ? allIterInputs.iterator().next() : opInputs.iterator().next());
            Preconditions.checkState(outputFrameIter.getFrame().equals(in.getFrame()), "Expected same frame for NextIteration input vs. output:" +
                    " got input %s, output %s", in, outputFrameIter);
            Preconditions.checkState(outputFrameIter.getIteration() == in.getIteration()+1, "Expected output iteration for NextIteration output to" +
                    " be 1 larger than the input iteration. Input: %s, output %s", in, outputFrameIter);

            INDArray inArr = this.nodeOutputs.get(in);
            if(inArr == null) {
                Preconditions.throwStateEx("Could not find array for NextIteration operation %s with output %s (frame=%s, iteration=%s)",
                        op.getOwnName(), sameDiff.getOps().get(op.getOwnName()).getOutputsOfOp().get(0), outputFrameIter.getFrame(), outputFrameIter.getIteration());
            }
            return new INDArray[]{inArr};
        } else if(op instanceof Merge) {
            //Merge avairable for forward pass when any of its inputs are available. When multiple are available, behaviour
            // is undefined
            Merge m = (Merge) op;
            String[] in = sameDiff.getInputsForOp(op);
            for (String s : in) {
                VarId vid = newVarId(s, outputFrameIter);
                if (nodeOutputs.containsKey(vid)) {
                    log.trace("Returning input \"{}\" for merge node \"{}\"", m.getOwnName(), s);
                    return new INDArray[]{nodeOutputs.get(vid)};
                }
            }
            throw new IllegalStateException("Merge node " + m.getOwnName() + " has no available inputs (all inputs: " + Arrays.toString(in) +
                    ") - should not be executed at this point");
        } else if(op instanceof LoopCond) {
            //LoopCond just forwards scalar boolean to output
            LoopCond lc = (LoopCond) op;
            String[] argNames = lc.argNames();
            Preconditions.checkState(argNames.length == 1, "Expected only 1 arg name in LoopCond op, got %s", argNames);
            VarId vid = newVarId(argNames[0], outputFrameIter);
            INDArray arr = nodeOutputs.get(vid);
            Preconditions.checkNotNull(arr, "Input to LoopCond op must not be null");
            Preconditions.checkState(arr.isScalar() && arr.dataType() == DataType.BOOL, "LoopCond input must be a scalar boolean, got %ndShape");
            return new INDArray[]{arr};
        } else if(op instanceof BaseTensorOp) {
            //TensorOps - special cases...
            return getOutputsHelperTensorArrayOps(op, outputFrameIter, opInputs, allIterInputs);
        } else if(op instanceof GradientBackwardsMarker){
            return new INDArray[]{Nd4j.scalar(1.0f)};
        } else if(op instanceof CustomOp){
            CustomOp c = (CustomOp)op;
            Nd4j.getExecutioner().exec(c);
            return c.outputArguments();
        } else if(op instanceof Op) {
            Op o = (Op) op;
            Nd4j.getExecutioner().exec(o);
            return new INDArray[]{o.z()};
        } else {
            throw new UnsupportedOperationException("Execution not yet implemented for: " + op.getClass().getName());
        }
    }

    public INDArray[] getOutputsHelperTensorArrayOps(DifferentialFunction op, FrameIter outputFrameIter, Set<VarId> opInputs, Set<VarId> allIterInputs){
        if (op instanceof TensorArray) {
            //Create a TensorArray
            VarId vid = newVarId(op.outputVariable().getVarName(), outputFrameIter);
            Preconditions.checkState(!tensorArrays.containsKey(vid), "TensorArray already exists for %s when executing TensorArrayV3", vid);
            tensorArrays.put(vid, new ArrayList<INDArray>());

            // Note that TensorArray has 2 outputs - a 'dummy' SDVariable that represents it, and a second output (return a scalar 0.0)
            try(MemoryWorkspace ws = Nd4j.getMemoryManager().scopeOutOfWorkspaces()) {
                //TODO Proper workspace support will be added to SameDiff later
                return new INDArray[]{Nd4j.scalar(true), Nd4j.scalar(0.0f)};
            }
        } else if (op instanceof TensorArrayRead) {
            //Do lookup and return
            //Input 0 is the TensorArray (or dummy variable that represents it). Sometimes (for import) this can be like (TensorArray -> Enter -> TensorArrayRead)
            //Input 1 is the index
            SDVariable idxSDV = op.arg(1);
            INDArray idxArr = getArray(idxSDV, opInputs, allIterInputs);
            Preconditions.checkState(idxArr.isScalar(), "TensorArrayRead input argument 1 should be scalar - has shape %ndShape", idxArr);
            int i = idxArr.getInt(0);

            SDVariable inTensorArray = op.arg(0);   //Dummy variable representing the tensor array

            //Work out the frame/iteration:
            VarId v = (opInputs == null ? null : lookup(inTensorArray.getVarName(), opInputs, false));
            if(v == null && allIterInputs != null){
                v = lookup(inTensorArray.getVarName(), allIterInputs, false);
            }

            Preconditions.checkState(v != null, "Could not find input %s", inTensorArray.getVarName());

            while(sameDiff.getVariableOutputOp(inTensorArray.getVarName()) instanceof Enter){
                //Handle the Enter case: this is like TensorArray -> Enter -> TensorArrayRead
                //TODO also TensorArrayWrite, scatter, etc??
                inTensorArray = sameDiff.getVariableOutputOp(inTensorArray.getVarName()).arg();
                v = newVarId(inTensorArray.getVarName(), v.getParentFrame());
            }

            List<INDArray> list = getTensorArrays().get(v);
            Preconditions.checkState(list != null, "Could not find TensorList for %s", v);
            Preconditions.checkState(list.size() > i, "Cannot get index %s from TensorList of size %s (array not present?) - VarId=%s", i, list.size(), v);

            INDArray out = list.get(i);
            return new INDArray[]{out};
        } else if (op instanceof TensorArrayWrite) {
            //TensorArrayWrite - also has a scalar 0.0 that it returns...

            SDVariable inTensorArray = op.arg(0);   //Dummy variable representing the tensor array
            //Work out the varid (frame/iteration) of the tensor array:
            VarId tArr = (opInputs == null ? null : lookup(inTensorArray.getVarName(), opInputs, false));
            if(tArr == null && allIterInputs != null){
                tArr = lookup(inTensorArray.getVarName(), allIterInputs, false);
            }

            Preconditions.checkState(tArr != null, "Could not find input %s", inTensorArray.getVarName());

            while(sameDiff.getVariableOutputOp(inTensorArray.getVarName()) instanceof Enter){
                //Handle the Enter case: this is like TensorArray -> Enter -> TensorArrayWrite
                //TODO also TensorArrayScatter, etc??
                inTensorArray = sameDiff.getVariableOutputOp(inTensorArray.getVarName()).arg();
                tArr = newVarId(inTensorArray.getVarName(), tArr.getParentFrame());
            }

            //Input 0 is the TensorArray (or dummy variable that represents it) - but sometimes Enter, in TensorArray -> Enter -> TensorARrayRead
            //Input 1 is the index
            //Input 2 is the value to write

            String idxName = op.arg(1).getVarName();
            SDVariable idxSDV = sameDiff.getVariable(idxName);
            INDArray idxArr = getArray(idxSDV, opInputs, allIterInputs);
            Preconditions.checkState(idxArr.isScalar(), "Index variable ID for TensorArrayWrite should be a scalar, got %ndShape", idxArr);
            int idx = idxArr.getInt(0);

            String inName = op.arg(2).getVarName();
            SDVariable inSDV = sameDiff.getVariable(inName);
            INDArray arr = getArray(inSDV, opInputs, allIterInputs);
            Preconditions.checkState(arr != null, "Could not find array for %s", inName);

            Preconditions.checkState(tensorArrays.containsKey(tArr), "Tensor array does not exist for %s", tArr);
            //TODO is this always safe to insert by index for all execution orders?
            List<INDArray> l = tensorArrays.get(tArr); //.set(idx, arr);
            while (l.size() <= idx) {
                //Can't use set(int, E) if index >= size
                l.add(null);
            }
            l.set(idx, arr);

            //Return dummy array
            try(MemoryWorkspace ws = Nd4j.getMemoryManager().scopeOutOfWorkspaces()) {
                //TODO Proper workspace support will be added to SameDiff later
                return new INDArray[]{Nd4j.scalar(0.0f)};
            }
        } else if (op instanceof TensorArraySize) {
            //Index 0 is the TensorArray (or dummy variable that represents it)
            SDVariable inTensorArray = op.arg(0);   //Dummy variable representing the tensor array
            //Work out the varid (frame/iteration) of the tensor array:
            VarId tArr = (opInputs == null ? null : lookup(inTensorArray.getVarName(), opInputs, false));
            if(tArr == null && allIterInputs != null){
                tArr = lookup(inTensorArray.getVarName(), allIterInputs, false);
            }
            List<INDArray> l = tensorArrays.get(tArr);
            Preconditions.checkState(l != null, "Could not find TensorArray: %s", tArr);
            try(MemoryWorkspace ws = Nd4j.getMemoryManager().scopeOutOfWorkspaces()) {
                //TODO Proper workspace support will be added to SameDiff later
                return new INDArray[]{Nd4j.scalar(DataType.INT, l.size())};
            }
        } else if (op instanceof TensorArrayConcat) {
            SDVariable inTensorArray = op.arg(0);   //Dummy variable representing the tensor array
            VarId tArr = (opInputs == null ? null : lookup(inTensorArray.getVarName(), opInputs, false));
            if(tArr == null && allIterInputs != null){
                tArr = lookup(inTensorArray.getVarName(), allIterInputs, false);
            }
            List<INDArray> l = tensorArrays.get(tArr);
            //TODO - empty checks. But is size 0 OK?
            try(MemoryWorkspace ws = Nd4j.getMemoryManager().scopeOutOfWorkspaces()) {
                //TODO Proper workspace support will be added to SameDiff later
                INDArray concat = Nd4j.concat(0, l.toArray(new INDArray[l.size()]));
                return new INDArray[]{concat};
            }
        } else if (op instanceof TensorArrayGather) {
            //Input 0: the TensorArray
            //Input 1: the indices (1d integer vector)

            SDVariable inTensorArray = op.arg(0);   //Dummy variable representing the tensor array
            VarId tArr = (opInputs == null ? null : lookup(inTensorArray.getVarName(), opInputs, false));
            if(tArr == null && allIterInputs != null){
                tArr = lookup(inTensorArray.getVarName(), allIterInputs, false);
            }
            List<INDArray> l = tensorArrays.get(tArr);
            Preconditions.checkState(l != null, "Could not find TensorArray: %s", tArr);

            String indicesName = op.arg(1).getVarName();
            SDVariable indicesSDV = sameDiff.getVariable(indicesName);
            INDArray idxArr = getArray(indicesSDV, opInputs, allIterInputs);
            Preconditions.checkState(idxArr.isVector(), "Indices variable for TensorArrayGather should be a vector, got %ndShape for %s", idxArr, indicesName);
            Preconditions.checkState(idxArr.dataType().isIntType(), "Indices variable for TensorArrayGather should be an integer type, got %s for array %s", idxArr.dataType(), indicesName);

            int[] idxArrInt = idxArr.toIntVector();

            //Edge case: -1 means "all"
            ArrayList<INDArray> newList = new ArrayList<>();
            if(idxArrInt.length == 1 && idxArrInt[0] == -1){
                newList.addAll(l);
            } else {
                for (int id : idxArrInt) {
                    Preconditions.checkState(id >=0,"Index for TensorArrayGather must be >= 0, got %s", id);
                    newList.add(l.get(id));
                }
            }
            try(MemoryWorkspace ws = Nd4j.getMemoryManager().scopeOutOfWorkspaces()) {
                //TODO Proper workspace support will be added to SameDiff later
                INDArray out = Nd4j.pile(newList);
                return new INDArray[]{out};
            }
        } else if (op instanceof TensorArrayScatter) {
            //Scatter values from a rank (N+1)d tensor into specific indices of the TensorArray
            //Input 0: the TensorArray
            //Input 1: the indices (1d integer vector)
            //Input 2: The values to scatter

            SDVariable inTensorArray = op.arg(0);   //Dummy variable representing the tensor array
            TensorArray ta = (TensorArray) sameDiff.getVariableOutputOp(inTensorArray.getVarName());
            VarId tArr = (opInputs == null ? null : lookup(inTensorArray.getVarName(), opInputs, false));
            if(tArr == null && allIterInputs != null){
                tArr = lookup(inTensorArray.getVarName(), allIterInputs, false);
            }
            List<INDArray> l = tensorArrays.get(tArr);
            Preconditions.checkState(l != null, "Could not find TensorArray: %s", tArr);

            String indicesName = op.arg(1).getVarName();
            SDVariable indicesSDV = sameDiff.getVariable(indicesName);
            INDArray idxArr = getArray(indicesSDV, opInputs, allIterInputs);
            Preconditions.checkState(idxArr.isVector(), "Indices variable for TensorArrayScatter should be a vector, got %ndShape for %s", idxArr, indicesName);
            Preconditions.checkState(idxArr.dataType().isIntType(), "Indices variable for TensorArrayScatter should be an integer type, got %s for array %s", idxArr.dataType(), indicesName);
            int[] idxs = idxArr.toIntVector();

            String valuesName = op.arg(2).getVarName();
            SDVariable valuesSDV = sameDiff.getVariable(valuesName);
            INDArray valuesArr = getArray(valuesSDV, opInputs, allIterInputs);

            while (l.size() <= idxs.length) { //Can't use set(int, E) if index >= size
                l.add(null);
            }

            //Edge case: idxs being [-1] means "all sub arrays" (i.e., "unstack" case)
            if(idxs.length == 1 && idxs[0] == -1){
                idxs = ArrayUtil.range(0, (int)valuesArr.size(0));
            }

            INDArrayIndex[] idx = ArrayUtil.nTimes(valuesArr.rank(), NDArrayIndex.all(), INDArrayIndex.class);
            for (int i = 0; i < idxs.length; i++) {
                idx[0] = NDArrayIndex.point(i);
                INDArray get = valuesArr.get(idx).dup();
                int outIdx = idxs[i];
                if(valuesArr.rank() == 1 && get.rank() > 0){
                    get = get.reshape();
                }
                l.set(outIdx, get);
            }

            //Return dummy array
            try(MemoryWorkspace ws = Nd4j.getMemoryManager().scopeOutOfWorkspaces()) {
                //TODO Proper workspace support will be added to SameDiff later
                return new INDArray[]{Nd4j.scalar(0.0f)};
            }
        } else if (op instanceof TensorArraySplit) {
            //Split values from a rank (N+1)d tensor into sequential indices of the TensorArray
            //For example, orig=[8,2] sizearray with split (4,4) means TensorArray[0] = orig[0:4,:] and TensorArray[1] = orig[4:8,:]
            //Input 0: the TensorArray
            //Input 1: The values to split
            //Input 2: the size of each split (1d integer vector)

            SDVariable inTensorArray = op.arg(0);   //Dummy variable representing the tensor array
            VarId tArr = (opInputs == null ? null : lookup(inTensorArray.getVarName(), opInputs, false));
            if(tArr == null && allIterInputs != null){
                tArr = lookup(inTensorArray.getVarName(), allIterInputs, false);
            }
            List<INDArray> l = tensorArrays.get(tArr);
            Preconditions.checkState(l != null, "Could not find TensorArray: %s", tArr);

            String splitName = op.arg(1).getVarName();
            INDArray splitArr = getArray(sameDiff.getVariable(splitName), opInputs, allIterInputs);


            String sizeName = op.arg(2).getVarName();
            SDVariable sizeSDV = sameDiff.getVariable(sizeName);
            INDArray sizeArr = getArray(sizeSDV, opInputs, allIterInputs);
            Preconditions.checkState(sizeArr.isVector(), "Indices variable for TensorArraySplit should be a vector, got %ndShape for %s", sizeArr, sizeName);
            Preconditions.checkState(sizeArr.dataType().isIntType(), "Indices variable for TensorArraySplit should be an integer type, got %s for array %s", sizeArr.dataType(), sizeName);
            int[] sizes = sizeArr.toIntVector();

            while (l.size() <= sizes.length) { //Can't use set(int, E) if index >= size
                l.add(null);
            }

            INDArrayIndex[] idx = ArrayUtil.nTimes(splitArr.rank(), NDArrayIndex.all(), INDArrayIndex.class);
            int soFar = 0;
            for (int i = 0; i < sizes.length; i++) {
                idx[0] = NDArrayIndex.interval(soFar, soFar + sizes[i]);
                INDArray sub = splitArr.get(idx).dup();
                l.set(i, sub);
                soFar += sizes[i];
            }
            //Return dummy array
            try(MemoryWorkspace ws = Nd4j.getMemoryManager().scopeOutOfWorkspaces()) {
                //TODO Proper workspace support will be added to SameDiff later
                return new INDArray[]{Nd4j.scalar(0.0f)};
            }
        } else {
            throw new IllegalStateException("Execution support not yet implemented for: " + op.getClass().getName());
        }
    }


    @Override
    public INDArray getConstantOrVariable(String variableName) {
        SDVariable v = sameDiff.getVariable(variableName);
        Preconditions.checkState(sameDiff.getVariable(variableName).isConstant() || v.getVariableType() == VariableType.VARIABLE,
                "Variable %s is not a constant", variableName);
        return sameDiff.getArrForVarName(variableName);
    }

    @Override
    public SameDiffOp getAndParameterizeOp(String opName, FrameIter frameIter, Set<VarId> opInputs, Set<VarId> allIterInputs,
                                                     Set<String> constAndPhInputs, Map<String,INDArray> placeholderValues, Set<String> allReqVariables) {
        SameDiffOp sdo = sameDiff.getOps().get(opName);
        DifferentialFunction df = sdo.getOp();

        //TODO We should clone these ops - probably - as we don't want them shared between threads/sessions!
        //But let's only clone them *once* and cache in inference session - not on every exec

        Preconditions.checkNotNull(df, "No differential function found with name \"%s\"", opName);

        if(df instanceof LoopCond || df instanceof Enter || df instanceof Exit || df instanceof NextIteration ||
                df instanceof Merge || df instanceof Switch || df instanceof BaseTensorOp){
            //Control dependencies and tensor ops (like TensorArray, TensorArrayRead etc) don't need inputs set, execution is a special case
            return sdo;
        }

        //Infer the args based on the inputs (variable + frame + iteration)
        String[] argNames = df.argNames();
        int numArgs = (argNames == null ? 0 : argNames.length);
        int numNonConstIns = (opInputs == null ? 0 : opInputs.size());
        int numNonConstInsAllIters = (allIterInputs == null ? 0 : allIterInputs.size());
        int numConstPhIns = (constAndPhInputs == null ? 0 : constAndPhInputs.size());

        Set<String> constEnterInputs = null;
        if(numArgs != (numNonConstIns + numConstPhIns + numNonConstInsAllIters)){
            boolean anyConstEnterInputs = false;
            SDVariable[] args = df.args();
            for(SDVariable v : args){
                Variable var = sameDiff.getVariables().get(v.getVarName());
                //Nested enter case:
                DifferentialFunction inputVarFn = (var.getOutputOfOp() == null ? null : sameDiff.getOps().get(var.getOutputOfOp()).getOp());
                if(inputVarFn instanceof Enter && ((Enter)inputVarFn).isConstant()){
                    anyConstEnterInputs = true;
                    if(constEnterInputs == null)
                        constEnterInputs = new HashSet<>();
                    constEnterInputs.add(v.getVarName());
                }
            }

            int constEnterInputCount = 0;
            if(anyConstEnterInputs){
                /*
                2019/01/26: AB
                Resolve nested enter inputs (constants 2+ enters in)
                Why this hack is necessary: consider the following (sub) graph:     constX -> Enter(a) -> Enter(b) -> opY
                On iterations (a=0, b=0) all is well, opY gets triggered as normal.
                On iterations (a>0, b=*) the "opY is available for exec" won't be triggered.
                This is because Enter(a) is only executed once, on iteration 0 of the outer loop.
                Consequently, Enter(b) is not triggered as available on iteration 1+.
                When we do the lookup for the actual array to use for op execution (i.e., get inputs for opY(a=1,b=0))
                it won't be found.
                This is a bit of an ugly hack, though I've yet to find a cleaner solution.
                It should only be required with the combination of: constants, 2 levels of enters, and more than 1 iteration in each loop.
                 */

                //For example, const -> Enter(a) -> Enter(b) -> op; in this case, the input to Op (at any frame/iteration) should should
                // be the constant value - which is recorded as (frame="a",iter=0,parent=(frame="b",iter=0))
                for(String s : constEnterInputs){
                    //First: check if this has already been provided
                    if(constAndPhInputs != null && constAndPhInputs.contains(s)){
                        //already resolved/provided
                        continue;
                    }
                    boolean found = false;
                    if(allIterInputs != null) {
                        for (VarId vid : allIterInputs) {
                            if (s.equals(vid.getVariable())) {
                                //Already resolved/provided
                                found = true;
                                break;
                            }
                        }
                    }
                    if(found)
                        continue;

                    constEnterInputCount++;
                }
            }

            if(numArgs > 1){
                //Might be due to repeated inputs
                Set<String> uniqueArgNames = new HashSet<>();
                Collections.addAll(uniqueArgNames, argNames);
                Preconditions.checkState(uniqueArgNames.size() == (numNonConstIns + numConstPhIns + numNonConstInsAllIters + constEnterInputCount),
                        "Different number of arg names as op inputs for op %s (%s): arg names %s vs. op inputs %s+%s", df.getClass().getSimpleName(),
                        opName, uniqueArgNames, opInputs, constAndPhInputs);
            } else {
                Preconditions.checkState(numArgs == (numNonConstIns + numConstPhIns + constEnterInputCount),
                        "Different number of arg names as op inputs for op %s (%s): arg names %s vs. op inputs %s+%s", df.getClass().getSimpleName(),
                        opName, argNames, opInputs, constAndPhInputs);
            }
        }

        INDArray[] args = null;
        if(argNames != null && argNames.length > 0) {
            args = new INDArray[argNames.length];
            int i = 0;
            for(String s : argNames){
                SDVariable v = sameDiff.getVariable(s);
                if(v.isConstant()) {
                    args[i] = v.getArr();
                } else if(v.getVariableType() == VariableType.VARIABLE){
                    args[i] = v.getArr();
                } else if(v.isPlaceHolder()) {
                    Preconditions.checkState(placeholderValues != null && placeholderValues.containsKey(s), "No array provided for placeholder %s", s);
                    args[i] = placeholderValues.get(s);
                } else if(constEnterInputs != null && constEnterInputs.contains(s)){
                    //For enter nodes that are constants, we want iteration 0 in all frames in the heirarchy
                    //For example, const -> Enter(a) -> Enter(b) -> op; in this case, the input to Op (at any frame/iteration) should should
                    // be the constant value - which is recorded as (frame="a",iter=0,parent=(frame="b",iter=0))
                    VarId vid = newVarId(s, frameIter.clone());
                    vid.setIteration(0);
                    FrameIter toZero = vid.getParentFrame();
                    while(toZero != null){
                        toZero.setIteration(0);
                        toZero = toZero.getParentFrame();
                    }
                    INDArray arr = this.nodeOutputs.get(vid);
                    args[i] = arr;
                } else {
                    VarId vid = lookup(s, opInputs, allIterInputs, true);
                    args[i] = nodeOutputs.get(vid);
                }
                Preconditions.checkNotNull(args[i], "Could not parameterize op %s: array %s (variable %s) is null", opName, i, v.getVarName());
                i++;
            }

        }

        //Set the op inputs and output arguments
        //Note that when we are in a loop (and non-first iteration), we want to allocate new arrays even if shapes are
        // ok: this is because we need the values in past iterations for backprop (potentially)
        //TODO let's find a way to use in-place modification for loops where possible to reduce memory requirements
        boolean isLoop = !frameIter.getFrame().equals(OUTER_FRAME) && frameIter.getIteration() > 0;

        if(df instanceof CustomOp){
            DynamicCustomOp customOp = (DynamicCustomOp) df;
            if(args != null) {
                customOp.setInputArguments(args);
            }

            df.resolvePropertiesFromSameDiffBeforeExecution();
            List<LongShapeDescriptor> outShape = customOp.calculateOutputShape();
            Preconditions.checkState(outShape != null && outShape.size() > 0, "Failed to calculate output shapes for op %s (%s) - no shapes were returned by calculateOutputShape()", customOp.opName(), customOp.getOwnName());
            String[] outNames = df.outputVariablesNames();
            Preconditions.checkState(outNames.length == outShape.size(), "Error in operation shape calculation for op \"%s\": Got %s op output shapes for an operation" +
                    " with %s outputs (number of shapes and outputs must be equal)", df.opName(), outShape.size(), outNames.length);
            for( int i=0; i<outShape.size(); i++ ){
                INDArray currOutput = (customOp.numOutputArguments() <= i ? null : customOp.getOutputArgument(i));
                LongShapeDescriptor reqShape = outShape.get(i);

                //Issue: many ops have multiple valid output datatypes, and output shape calc can't at present know which: https://github.com/deeplearning4j/deeplearning4j/issues/6872
                //As a workaround, we'll use the output variable datatype instead.
                DataType dt = sameDiff.getVariable(outNames[i]).dataType();
                DataType currDT = reqShape.dataType();
                if(dt != currDT){
                    reqShape = reqShape.asDataType(dt);
                }

                if(currOutput == null || currOutput.wasClosed() || !currOutput.shapeDescriptor().equals(reqShape) || currOutput.isEmpty() != reqShape.isEmpty() || isLoop){
                    boolean isOutput = allReqVariables.contains(outNames[i]);
                    INDArray out = mmgr.allocate(isOutput, reqShape);
                    customOp.setOutputArgument(i, out);
                }
            }

        } else if(df instanceof Op){
            Op op = (Op) df;

            boolean axisArg = false;
            boolean emptyReduce = false;
            if(op instanceof ReduceOp && ((ReduceOp) op).getOpType() != Op.Type.REDUCE3 && df.argNames().length == 2){
                //2nd input should be treated as integer axis arg...
                SDVariable axisArgVar = df.arg(1);
                Preconditions.checkState(axisArgVar.dataType().isIntType(), "Legacy op %s input 1 (axis) was expected to be an integer type, is %s", df.getClass(), axisArgVar.dataType());

                INDArray arr = getArray(axisArgVar, opInputs, allIterInputs);
                Preconditions.checkState(arr != null, "Could not get axis argument for op %s: %s", df.getOwnName(), df.getClass());
                if(!arr.isEmpty()){
                    int[] axis = arr.toIntVector();
                    int rank = args[0].rank();
                    axis = Shape.normalizeAxis(rank, axis);
                    df.setDimensions(axis);
                    ((BaseReduceOp)op).setEmptyReduce(false);
                } else {
                    df.setDimensions(null);
                    emptyReduce = true;
                    //Note: edge case: [x,y].sum(empty) = [x,y] for TF import compatibility.
                    //Note also that empty is not the same as int[0] as in INDArray.sum(new int[0])
                    ((BaseReduceOp)op).setEmptyReduce(true);
                }
                axisArg = true;
            } else if(op instanceof ScalarOp && df.argNames().length == 2){
                //Scalar ops: 2nd input should be treated as scalar...
                SDVariable scalarVar = df.arg(1);
                INDArray scalar = getArray(scalarVar, opInputs, allIterInputs);
                Preconditions.checkState(scalar != null, "Could not get scalar argument for op %s: %s", df.getOwnName(), df.getClass());
                Preconditions.checkState(scalar.isScalar(), "Scalar argument for op %s (%s) is not a scalar: has shape %ndShape", df.getOwnName(), df.getClass(), scalar );
                ((ScalarOp) op).setScalar(scalar);
            }

            if(args != null && args.length > 0){
                op.setX(args[0]);
                if (args.length == 2 && !axisArg)
                    op.setY(args[1]);
            }


            //Check output shape; allocate a new Z if required
            //For example, if minibatch size has changed since last op execution
            if(emptyReduce){
                INDArray z = op.z();
                if (z == null || !op.x().equalShapes(z) || isLoop) {
                    //Note: edge case: [x,y].sum(empty) = [x,y] for TF import compatibility.
                    op.setZ(op.x().ulike());
                }
            } else {
                List<LongShapeDescriptor> outputShape = ((BaseOp) op).calculateOutputShape();
                Preconditions.checkState(outputShape != null && outputShape.size() == 1, "Could not calculate output shape for op: %s", op.getClass());
                INDArray z = op.z();
                if (z == null || !outputShape.get(0).equals(z.shapeDescriptor()) || isLoop) {
                    if (log.isTraceEnabled()) {
                        log.trace("Existing op result (z) array shape for op {} was {}, allocating new array of shape {}",
                                op.getClass().getSimpleName(), (z == null ? null : Arrays.toString(z.shape())), outputShape.get(0).toString());
                    }

                    LongShapeDescriptor lsd = outputShape.get(0);

                    boolean isOutput = allReqVariables.contains(((BaseOp) op).outputVariablesNames()[0]);
                    z = mmgr.allocate(isOutput, lsd);
                    op.setZ(z);
                }
            }
            df.resolvePropertiesFromSameDiffBeforeExecution();
        }

        return sdo;
    }


    protected INDArray getArray(SDVariable sdv, Collection<VarId> opInputs, Collection<VarId> allIterInputs){
        String n = sdv.getVarName();
        if(sdv.getVariableType() == VariableType.CONSTANT || sdv.getVariableType() == VariableType.VARIABLE){
            return getConstantOrVariable(n);
        } else {
            VarId inVarId = lookup(n, opInputs, allIterInputs, false);
            Preconditions.checkState(inVarId != null,"Could not find array for variable %s", sdv.getVarName());
            return nodeOutputs.get(inVarId);
        }
    }

    protected void clearOpReferencesFor(@NonNull String varName){
        Variable v = sameDiff.getVariables().get(varName);

        //Clear op outputs
        String outOfOp = v.getOutputOfOp();
        SameDiffOp op = sameDiff.getOps().get(outOfOp);
        int idx = op.getOutputsOfOp().indexOf(varName);
        if(op.getOp() instanceof DynamicCustomOp){
            DynamicCustomOp dco = (DynamicCustomOp)op.getOp();
            dco.setOutputArgument(idx, null);
        } else {
            Op o = (Op)op.getOp();
            o.setZ(null);
        }

        //Clear op inputs
        List<String> inTo = v.getInputsForOp();
        if(inTo != null && !inTo.isEmpty()){
            for(String opName : inTo){
                SameDiffOp o = sameDiff.getOps().get(opName);
                int inIdx = o.getInputsToOp().indexOf(varName);
                if(o.getOp() instanceof DynamicCustomOp){
                    DynamicCustomOp dco = (DynamicCustomOp)o.getOp();
                    dco.setInputArgument(inIdx, null);
                } else {
                    Op op2 = (Op)o.getOp();
                    if(inIdx == 0){
                        op2.setX(null);
                    } else {
                        op2.setY(null);
                    }
                }
            }
        }
    }

    @Override
    protected void onFrameIterTransition(String from, FrameIter parentFrom, String to, FrameIter parentTo){
        log.info("InferenceSession2: Transition from {} (parent={}) to {} (parent={})", from, parentFrom, to, parentTo);
        //Remove any frame dependencies...
        //TODO
    }

    @Data
    protected static class Array {
        private String varName;
        private String frame;
        private int iter;
        private FrameIter parentFrame;

        public Array(@NonNull String varName, @NonNull String frame, int iter, FrameIter parentFrame) {
            this.varName = varName;
            this.frame = frame;
            this.iter = iter;
            this.parentFrame = parentFrame;
        }

        protected VarId toVarId(){
            return new VarId(varName, frame, iter, parentFrame);
        }
    }

    @Data
    protected abstract static class Dep {
        protected String frame;
        protected FrameIter parentFrame;
    }

    @AllArgsConstructor
    @Data
    @EqualsAndHashCode(callSuper = true)
    protected static class OpDep extends Dep {
        protected String opName;
        protected int iter;

        protected OpDep(@NonNull String opName, @NonNull String frame, int iter, FrameIter parentFrame){
            this.opName = opName;
            this.frame = frame;
            this.iter = iter;
            this.parentFrame = parentFrame;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    protected static class FrameDep extends Dep {
        public FrameDep(@NonNull String frame, FrameIter parentFrame){
            this.frame = frame;
            this.parentFrame = parentFrame;
        }
    }
}
