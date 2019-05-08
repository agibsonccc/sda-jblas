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

package org.datavec.local.transforms.functions;

import org.datavec.api.writable.IntWritable;
import org.datavec.api.writable.NDArrayWritable;
import org.datavec.api.writable.Writable;

import org.datavec.local.transforms.misc.WritablesToNDArrayFunction;
import org.junit.Test;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestWritablesToNDArrayFunction {

    @Test
    public void testWritablesToNDArrayAllScalars() throws Exception {
        Nd4j.setDataType(DataType.FLOAT);
        List<Writable> l = new ArrayList<>();
        for (int i = 0; i < 5; i++)
            l.add(new IntWritable(i));
        INDArray expected = Nd4j.arange(5f).castTo(DataType.FLOAT).reshape(1, 5);
        assertEquals(expected, new WritablesToNDArrayFunction().apply(l));
    }

    @Test
    public void testWritablesToNDArrayMixed() throws Exception {
        Nd4j.setDataType(DataType.FLOAT);
        List<Writable> l = new ArrayList<>();
        l.add(new IntWritable(0));
        l.add(new IntWritable(1));
        INDArray arr = Nd4j.arange(2, 5).reshape(1, 3);
        l.add(new NDArrayWritable(arr));
        l.add(new IntWritable(5));
        arr = Nd4j.arange(6, 9).reshape(1, 3);
        l.add(new NDArrayWritable(arr));
        l.add(new IntWritable(9));

        INDArray expected = Nd4j.arange(10).castTo(DataType.FLOAT).reshape(1, 10);
        assertEquals(expected, new WritablesToNDArrayFunction().apply(l));
    }
}
