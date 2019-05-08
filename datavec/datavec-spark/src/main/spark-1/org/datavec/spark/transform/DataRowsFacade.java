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

package org.datavec.spark.transform;

import org.apache.spark.sql.DataFrame;

/**
 * Dataframe facade to hide incompatibilities between Spark 1.x and Spark 2.x
 *
 * This class should be used instead of direct referral to DataFrame / Dataset
 *
 */
public class DataRowsFacade {

    private final DataFrame df;

    private DataRowsFacade(DataFrame df) {
        this.df = df;
    }

    public static DataRowsFacade dataRows(DataFrame df) {
        return new DataRowsFacade(df);
    }

    public DataFrame get() {
        return df;
    }
}
