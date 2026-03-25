package com.platform.workbench.opencode.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 权限回复请求体 */
@Data
@AllArgsConstructor
public class OCPermissionReply {
    /** once | always | reject */
    private String reply;
}
