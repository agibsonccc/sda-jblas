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

package org.nd4j.linalg.serde;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.nd4j.linalg.BaseNd4jTest;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.factory.Nd4jBackend;
import org.nd4j.linalg.io.ClassPathResource;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Slf4j
public class NumpyFormatTests extends BaseNd4jTest {

    @Rule
    public TemporaryFolder testDir = new TemporaryFolder();

    public NumpyFormatTests(Nd4jBackend backend) {
        super(backend);
    }

    @Test
    public void testToNpyFormat() throws Exception {

        val dir = testDir.newFolder();
        new ClassPathResource("numpy_arrays/").copyDirectory(dir);

        File[] files = dir.listFiles();
        int cnt = 0;

        for(File f : files){
            if(!f.getPath().endsWith(".npy")){
                log.warn("Skipping: {}", f);
                continue;
            }

            String path = f.getAbsolutePath();
            int lastDot = path.lastIndexOf('.');
            int lastUnderscore = path.lastIndexOf('_');
            String dtype = path.substring(lastUnderscore+1, lastDot);
            System.out.println(path + " : " + dtype);

            DataType dt = DataType.fromNumpy(dtype);
            //System.out.println(dt);

            INDArray arr = Nd4j.arange(12).castTo(dt).reshape(3,4);
            byte[] bytes = Nd4j.toNpyByteArray(arr);
            byte[] expected = FileUtils.readFileToByteArray(f);
/*
            log.info("E: {}", Arrays.toString(expected));
            for( int i=0; i<expected.length; i++ ){
                System.out.print((char)expected[i]);
            }

            System.out.println();System.out.println();

            log.info("A: {}", Arrays.toString(bytes));
            for( int i=0; i<bytes.length; i++ ){
                System.out.print((char)bytes[i]);
            }
            System.out.println();
*/

            assertArrayEquals("Failed with file [" + f.getName() + "]", expected, bytes);
            cnt++;
        }

        assertTrue(cnt > 0);
    }

    @Test
    public void testToNpyFormatScalars() throws Exception {
//        File dir = new File("C:\\DL4J\\Git\\dl4j-test-resources\\src\\main\\resources\\numpy_arrays\\scalar");

        val dir = testDir.newFolder();
        new ClassPathResource("numpy_arrays/scalar/").copyDirectory(dir);

        File[] files = dir.listFiles();
        int cnt = 0;

        for(File f : files){
            if(!f.getPath().endsWith(".npy")){
                log.warn("Skipping: {}", f);
                continue;
            }

            String path = f.getAbsolutePath();
            int lastDot = path.lastIndexOf('.');
            int lastUnderscore = path.lastIndexOf('_');
            String dtype = path.substring(lastUnderscore+1, lastDot);
            System.out.println(path + " : " + dtype);

            DataType dt = DataType.fromNumpy(dtype);
            //System.out.println(dt);

            INDArray arr = Nd4j.scalar(dt, 1);
            byte[] bytes = Nd4j.toNpyByteArray(arr);
            byte[] expected = FileUtils.readFileToByteArray(f);

            /*
            log.info("E: {}", Arrays.toString(expected));
            for( int i=0; i<expected.length; i++ ){
                System.out.print((char)expected[i]);
            }

            System.out.println();System.out.println();

            log.info("A: {}", Arrays.toString(bytes));
            for( int i=0; i<bytes.length; i++ ){
                System.out.print((char)bytes[i]);
            }
            System.out.println();
            */

            assertArrayEquals("Failed with file [" + f.getName() + "]", expected, bytes);
            cnt++;

            System.out.println();
        }

        assertTrue(cnt > 0);
    }


    @Test
    public void testNpzReading() throws Exception {

        val dir = testDir.newFolder();
        new ClassPathResource("numpy_arrays/npz/").copyDirectory(dir);

        File[] files = dir.listFiles();
        int cnt = 0;

        for(File f : files){
            if(!f.getPath().endsWith(".npz")){
                log.warn("Skipping: {}", f);
                continue;
            }

            String path = f.getAbsolutePath();
            int lastDot = path.lastIndexOf('.');
            int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            String dtype = path.substring(lastSlash+1, lastDot);
            System.out.println(path + " : " + dtype);

            DataType dt = DataType.fromNumpy(dtype);
            //System.out.println(dt);

            INDArray arr = Nd4j.arange(12).castTo(dt).reshape(3,4);
            INDArray arr2 = Nd4j.linspace(DataType.FLOAT, 0, 3, 10);

            Map<String,INDArray> m = Nd4j.createFromNpzFile(f);
            assertEquals(2, m.size());
            assertTrue(m.containsKey("firstArr"));
            assertTrue(m.containsKey("secondArr"));

            assertEquals(arr, m.get("firstArr"));
            assertEquals(arr2, m.get("secondArr"));
            cnt++;
        }

        assertTrue(cnt > 0);
    }

    @Test
    public void testTxtReading() throws Exception {
        File f = new ClassPathResource("numpy_arrays/txt/arange_3,4_float32.txt").getFile();
        INDArray arr = Nd4j.readNumpy(DataType.FLOAT, f.getPath());

        INDArray exp = Nd4j.arange(12).castTo(DataType.FLOAT).reshape(3,4);
        assertEquals(exp, arr);

        arr = Nd4j.readNumpy(DataType.DOUBLE, f.getPath());

        assertEquals(exp.castTo(DataType.DOUBLE), arr);

        f = new ClassPathResource("numpy_arrays/txt_tab/arange_3,4_float32.txt").getFile();
        arr = Nd4j.readNumpy(DataType.FLOAT, f.getPath(), "\t");

        assertEquals(exp, arr);
    }

    @Override
    public char ordering() {
        return 'c';
    }
}
