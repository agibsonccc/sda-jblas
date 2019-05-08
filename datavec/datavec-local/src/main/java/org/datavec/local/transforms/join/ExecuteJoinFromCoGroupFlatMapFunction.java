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

package org.datavec.local.transforms.join;

import org.datavec.api.transform.join.Join;
import org.datavec.api.writable.Writable;
import org.datavec.local.transforms.BaseFlatMapFunctionAdaptee;
import org.nd4j.linalg.primitives.Pair;

import java.util.List;

/**
 * Execute a join
 *
 * @author Alex Black
 */
public class ExecuteJoinFromCoGroupFlatMapFunction extends
        BaseFlatMapFunctionAdaptee<Pair<List<Writable>, Pair<List<List<Writable>>, List<List<Writable>>>>, List<Writable>> {

    public ExecuteJoinFromCoGroupFlatMapFunction(Join join) {
        super(new ExecuteJoinFromCoGroupFlatMapFunctionAdapter(join));
    }
}
