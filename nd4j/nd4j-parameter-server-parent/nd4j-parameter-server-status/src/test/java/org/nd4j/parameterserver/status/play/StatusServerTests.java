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

package org.nd4j.parameterserver.status.play;

import org.junit.Test;
import play.server.Server;

/**
 * Created by agibsonccc on 12/1/16.
 */
public class StatusServerTests {

    @Test(timeout = 20000L)
    public void runStatusServer() {
        Server server = StatusServer.startServer(new InMemoryStatusStorage(), 65236);
        server.stop();
    }

}
