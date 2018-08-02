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

package org.datavec.api.transform;

import org.datavec.api.transform.schema.Schema;


/**
 * ColumnOp
 * is a transform meant
 * to run over 1 or more columns
 *
 * @author Adam Gibson
 */
public interface ColumnOp {
    /** Get the output schema for this transformation, given an input schema */
    Schema transform(Schema inputSchema);


    /** Set the input schema.
     */
    void setInputSchema(Schema inputSchema);

    /**
     * Getter for input schema
     * @return
     */
    Schema getInputSchema();

    /**
     * The output column name
     * after the operation has been applied
     * @return the output column name
     */
    String outputColumnName();

    /**
     * The output column names
     * This will often be the same as the input
     * @return the output column names
     */
    String[] outputColumnNames();

    /**
     * Returns column names
     * this op is meant to run on
     * @return
     */
    String[] columnNames();

    /**
     * Returns a singular column name
     * this op is meant to run on
     * @return
     */
    String columnName();

}
