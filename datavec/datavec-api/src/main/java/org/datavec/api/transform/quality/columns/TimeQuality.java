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

package org.datavec.api.transform.quality.columns;

import lombok.Data;

/**
 * TimeQuality: quality of a time column
 *
 * @author Alex Black
 */
@Data
public class TimeQuality extends ColumnQuality {


    public TimeQuality(long countValid, long countInvalid, long countMissing, long countTotal) {
        super(countValid, countInvalid, countMissing, countTotal);
    }

    public TimeQuality() {
        this(0, 0, 0, 0);
    }

    @Override
    public String toString() {
        return "TimeQuality(" + super.toString() + ")";
    }

    public TimeQuality add(TimeQuality other) {
        return new TimeQuality(countValid + other.countValid, countInvalid + other.countInvalid,
                        countMissing + other.countMissing, countTotal + other.countTotal);
    }
}
