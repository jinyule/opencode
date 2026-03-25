package com.platform.workbench.opencode.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

/** opencode Session 对象 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OCSession {
    private String id;
    private String slug;
    private String title;
    private String directory;
    private Integer version;
    private Map<String, Object> summary;
    private Map<String, Object> time;
    private String parentID;
}
