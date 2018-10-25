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

package org.deeplearning4j.spark.data.loader;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.datavec.spark.util.SerializableHadoopConfig;
import org.nd4j.api.loader.Source;
import org.nd4j.api.loader.SourceFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;


public class RemoteFileSourceFactory implements SourceFactory {
    private transient FileSystem fileSystem;
    private final SerializableHadoopConfig conf;

    public RemoteFileSourceFactory(Configuration configuration){
        this.conf = (configuration == null ? null : new SerializableHadoopConfig(configuration));
    }

    @Override
    public Source getSource(String path) {
        if(fileSystem == null){
            Configuration c = (conf != null ? conf.getConfiguration() : new Configuration());
            try {
                fileSystem = FileSystem.get(new URI(path), c);
            } catch (IOException | URISyntaxException u){
                throw new RuntimeException(u);
            }
        }

        return new RemoteFileSource(path, fileSystem);
    }
}
