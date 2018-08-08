/*******************************************************************************
 * Copyright (c) 2015-2018 Skymind, Inc.
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

//
// Created by raver119 on 20.10.2017.
//

#include <graph/execution/LogicScope.h>


namespace nd4j {
    namespace graph {
        template <typename T>
        Nd4jStatus LogicScope<T>::processNode(Graph<T> *graph, Node<T> *node) {
            // this op is basically no-op
            // we just know it exists
            return ND4J_STATUS_OK;
        }

        template class ND4J_EXPORT LogicScope<float>;
        template class ND4J_EXPORT LogicScope<float16>;
        template class ND4J_EXPORT LogicScope<double>;
        template class ND4J_EXPORT LogicScope<int>;
        template class ND4J_EXPORT LogicScope<Nd4jLong>;
    }
}