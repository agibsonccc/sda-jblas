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

package org.datavec.api.transform.serde.legacy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import org.datavec.api.transform.serde.JsonMappers;
import org.nd4j.serde.json.BaseLegacyDeserializer;
import org.nd4j.shade.jackson.databind.ObjectMapper;

import java.util.Map;

@AllArgsConstructor
@Data
public class GenericLegacyDeserializer<T> extends BaseLegacyDeserializer<T> {

    @Getter
    protected final Class<T> deserializedType;
    @Getter
    protected final Map<String,String> legacyNamesMap;

    @Override
    public ObjectMapper getLegacyJsonMapper() {
        return JsonMappers.getLegacyMapperFor(getDeserializedType());
    }
}
