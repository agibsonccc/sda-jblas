/*-
 *
 *  * Copyright 2015 Skymind,Inc.
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */

package org.deeplearning4j.clustering.strategy;

import lombok.*;
import org.deeplearning4j.clustering.condition.ClusteringAlgorithmCondition;
import org.deeplearning4j.clustering.condition.ConvergenceCondition;
import org.deeplearning4j.clustering.condition.FixedIterationCountCondition;

import java.io.Serializable;
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseClusteringStrategy implements ClusteringStrategy, Serializable {
    @Getter(AccessLevel.PUBLIC)
    @Setter(AccessLevel.PROTECTED)
    protected ClusteringStrategyType type;
    @Getter(AccessLevel.PUBLIC)
    @Setter(AccessLevel.PROTECTED)
    protected Integer initialClusterCount;
    @Getter(AccessLevel.PUBLIC)
    @Setter(AccessLevel.PROTECTED)
    protected ClusteringAlgorithmCondition optimizationPhaseCondition;
    @Getter(AccessLevel.PUBLIC)
    @Setter(AccessLevel.PROTECTED)
    protected ClusteringAlgorithmCondition terminationCondition;
    @Getter(AccessLevel.PUBLIC)
    @Setter(AccessLevel.PROTECTED)
    protected  boolean inverse;
    @Getter(AccessLevel.PUBLIC)
    @Setter(AccessLevel.PROTECTED)
    protected String distanceFunction;
    @Getter(AccessLevel.PUBLIC)
    @Setter(AccessLevel.PROTECTED)
    protected boolean allowEmptyClusters;

    public BaseClusteringStrategy(ClusteringStrategyType type,
                                  Integer initialClusterCount,
                                  String distanceFunction,
                                  boolean allowEmptyClusters,
                                  boolean inverse) {
        this.type = type;
        this.initialClusterCount = initialClusterCount;
        this.distanceFunction = distanceFunction;
        this.allowEmptyClusters = allowEmptyClusters;
        this.inverse = inverse;
    }


    /**
     *
     * @param maxIterationCount
     * @return
     */
    public BaseClusteringStrategy endWhenIterationCountEquals(int maxIterationCount) {
        setTerminationCondition(FixedIterationCountCondition.iterationCountGreaterThan(maxIterationCount));
        return this;
    }

    /**
     *
     * @param rate
     * @return
     */
    public BaseClusteringStrategy endWhenDistributionVariationRateLessThan(double rate) {
        setTerminationCondition(ConvergenceCondition.distributionVariationRateLessThan(rate));
        return this;
    }

    /**
     *
     * @param type
     * @return
     */
    public boolean isStrategyOfType(ClusteringStrategyType type) {
        return type.equals(this.type);
    }

    /**
     *
     * @return
     */
    public Integer getInitialClusterCount() {
        return initialClusterCount;
    }


}
