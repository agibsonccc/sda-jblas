package org.nd4j.linalg.api.ops.impl.layers.convolution.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Conv2DConfig extends BaseConvolutionConfig {
    @Builder.Default
    private long kH = 1;
    @Builder.Default
    private long kW = 1;
    @Builder.Default
    private long sH = 1;
    @Builder.Default
    private long sW = 1;
    @Builder.Default
    private long pH = 0;
    @Builder.Default
    private long pW = 0;
    @Builder.Default
    private long dH = 1;
    @Builder.Default
    private long dW = 1;
    private boolean isSameMode;
    @Builder.Default
    private String dataFormat = "NWHC";
    @Builder.Default
    private boolean isNHWC = false;

    public Map<String, Object> toProperties() {
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("kH", kH);
        ret.put("kW", kW);
        ret.put("sH", sH);
        ret.put("sW", sW);
        ret.put("pH", pH);
        ret.put("pW", pW);
        ret.put("dH", dH);
        ret.put("dW", dW);
        ret.put("isSameMode", isSameMode);
        ret.put("dataFormat", dataFormat);
        ret.put("isNWHC", isNHWC);
        return ret;
    }


}
