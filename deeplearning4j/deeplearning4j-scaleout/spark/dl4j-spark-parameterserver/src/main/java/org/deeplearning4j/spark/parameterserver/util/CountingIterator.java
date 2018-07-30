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

package org.deeplearning4j.spark.parameterserver.util;

import lombok.AllArgsConstructor;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple iterator that adds 1 to the specified counter every time next() is called
 *
 * @param <T> Type of iterator
 * @author Alex Black
 */
@AllArgsConstructor
public class CountingIterator<T> implements Iterator<T> {

    private final Iterator<T> iter;
    private final AtomicInteger counter;

    @Override
    public boolean hasNext() {
        return iter.hasNext();
    }

    @Override
    public T next() {
        counter.getAndIncrement();
        return iter.next();
    }
}
