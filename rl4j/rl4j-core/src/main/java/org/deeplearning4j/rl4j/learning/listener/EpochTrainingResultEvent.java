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

package org.deeplearning4j.rl4j.learning.listener;

import lombok.*;
import org.deeplearning4j.rl4j.util.IDataManager;

/**
 * This event is raised when an epoch has been completed and contains informations about the epoch.
 *
 * @author Alexandre Boulanger
 */
public class EpochTrainingResultEvent extends EpochTrainingEvent implements IEpochTrainingResultEvent {
    @Getter
    private IDataManager.StatEntry statEntry;

    public EpochTrainingResultEvent(int epochCount, int stepNum, IDataManager.StatEntry statEntry) {
        super(epochCount, stepNum);
        this.statEntry = statEntry;
    }
}
