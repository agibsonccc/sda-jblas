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
// @author Yurii Shyrma (iuriish@yahoo.com), created on 14.03.2019
//

#ifndef LIBND4J_LOOPS_H
#define LIBND4J_LOOPS_H

#include <functional>
#include <pointercast.h>

namespace nd4j   {

class Loops {

    private:

        //////////////////////////////////////////////////////////////////////////////
        static std::string deduceKindOfLoopXYZ(const Nd4jLong* xShapeInfo, const Nd4jLong* yShapeInfo, const Nd4jLong* zShapeInfo);

        //////////////////////////////////////////////////////////////////////////////
        template<typename X, typename Y, typename Z> 
        static void loopXYZ(const std::string& info, 
                            const X* x, const Nd4jLong* xShapeInfo,
                            const Y* y, const Nd4jLong* yShapeInfo,
                            const Z* z, const Nd4jLong* zShapeInfo,
                            const Z* extraParams,
                            std::function<Z(X,Y,Z*)> op);

    public:

        //////////////////////////////////////////////////////////////////////////////
        template<typename X, typename Y, typename Z> 
        static void runLoopXYZ(const X* x, const Nd4jLong* xShapeInfo,
                               const Y* y, const Nd4jLong* yShapeInfo,
                               const Z* z, const Nd4jLong* zShapeInfo,
                               const Z* extraParams,
                               std::function<Z(X,Y,Z*)> op);



};
}


#endif //LIBND4J_LOOPS_H
