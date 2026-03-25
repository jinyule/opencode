package com.platform.workbench.opencode.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** 发送给 opencode 的 prompt_async 请求体 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OCPromptRequest {

    private List<Map<String, Object>> parts;
    private ModelRef model;
    private String agent;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ModelRef {
        private String providerID;
        private String modelID;
    }
}
