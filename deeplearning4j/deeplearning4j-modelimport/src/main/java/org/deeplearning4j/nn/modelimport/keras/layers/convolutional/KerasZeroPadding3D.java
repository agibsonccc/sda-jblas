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

package org.deeplearning4j.nn.modelimport.keras.layers.convolutional;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.ZeroPadding3DLayer;
import org.deeplearning4j.nn.conf.layers.ZeroPaddingLayer;
import org.deeplearning4j.nn.modelimport.keras.KerasLayer;
import org.deeplearning4j.nn.modelimport.keras.exceptions.InvalidKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.exceptions.UnsupportedKerasConfigurationException;

import java.util.Map;

import static org.deeplearning4j.nn.modelimport.keras.layers.convolutional.KerasConvolutionUtils.getPaddingFromConfig;

/**
 * Imports a Keras ZeroPadding 3D layer.
 *
 * @author Max Pumperla
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = false)
public class KerasZeroPadding3D extends KerasLayer {

    /**
     * Constructor from parsed Keras layer configuration dictionary.
     *
     * @param layerConfig   dictionary containing Keras layer configuration.
     *
     * @throws InvalidKerasConfigurationException Invalid Keras config
     * @throws UnsupportedKerasConfigurationException Unsupported Keras config
     */
    public KerasZeroPadding3D(Map<String, Object> layerConfig)
                    throws InvalidKerasConfigurationException, UnsupportedKerasConfigurationException {
        this(layerConfig, true);
    }

    /**
     * Constructor from parsed Keras layer configuration dictionary.
     *
     * @param layerConfig               dictionary containing Keras layer configuration
     * @param enforceTrainingConfig     whether to enforce training-related configuration options
     * @throws InvalidKerasConfigurationException Invalid Keras config
     * @throws UnsupportedKerasConfigurationException Unsupported Keras config
     */
    public KerasZeroPadding3D(Map<String, Object> layerConfig, boolean enforceTrainingConfig)
                    throws InvalidKerasConfigurationException, UnsupportedKerasConfigurationException {
        super(layerConfig, enforceTrainingConfig);
        String paddingField = conf.getLAYER_FIELD_ZERO_PADDING();
        int[] padding = getPaddingFromConfig(layerConfig, conf, paddingField,3);
        ZeroPadding3DLayer.Builder builder = new ZeroPadding3DLayer.Builder(padding)
                .name(this.layerName).dropOut(this.dropout);
        this.layer = builder.build();
        this.vertex = null;
    }

    /**
     * Get DL4J ZeroPadding3DLayer.
     *
     * @return  ZeroPadding3DLayer
     */
    public ZeroPadding3DLayer getZeroPadding3DLayer() {
        return (ZeroPadding3DLayer) this.layer;
    }

    /**
     * Get layer output type.
     *
     * @param  inputType    Array of InputTypes
     * @return              output type as InputType
     * @throws InvalidKerasConfigurationException Invalid Keras config
     */
    @Override
    public InputType getOutputType(InputType... inputType) throws InvalidKerasConfigurationException {
        if (inputType.length > 1)
            throw new InvalidKerasConfigurationException(
                            "Keras ZeroPadding3D layer accepts only one input (received " + inputType.length + ")");
        return this.getZeroPadding3DLayer().getOutputType(-1, inputType[0]);
    }
}
