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

package org.nd4j.linalg.indexing.functions;


import com.google.common.base.Function;
import org.nd4j.linalg.factory.Nd4j;

/**
 * Returns a stable number based on infinity
 * or nan
 */
public class StableNumber implements Function<Number, Number> {
    private Type type;

    public enum Type {
        DOUBLE, FLOAT
    }

    public StableNumber(Type type) {
        this.type = type;
    }

    @Override
    public Number apply(Number number) {
        switch (type) {
            case DOUBLE:
                if (Double.isInfinite(number.doubleValue()))
                    return -Double.MAX_VALUE;
                if (Double.isNaN(number.doubleValue()))
                    return Nd4j.EPS_THRESHOLD;
            case FLOAT:
                if (Float.isInfinite(number.floatValue()))
                    return -Float.MAX_VALUE;
                if (Float.isNaN(number.floatValue()))
                    return Nd4j.EPS_THRESHOLD;
            default:
                throw new IllegalStateException("Illegal opType");

        }

    }
}
