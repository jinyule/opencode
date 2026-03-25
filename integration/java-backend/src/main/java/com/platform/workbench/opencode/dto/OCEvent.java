package com.platform.workbench.opencode.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * opencode SSE 事件通用结构。
 * 格式: {"type":"<event_type>","properties":{...}}
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OCEvent {

    private String type;

    /** 原始 properties，按 type 进一步解析 */
    private JsonNode properties;

    // ── 常量：opencode 事件类型 ────────────────────────────────

    public static final String SERVER_CONNECTED        = "server.connected";
    public static final String SERVER_HEARTBEAT        = "server.heartbeat";
    public static final String SERVER_INSTANCE_DISPOSED = "server.instance.disposed";

    public static final String SESSION_CREATED         = "session.created";
    public static final String SESSION_UPDATED         = "session.updated";
    public static final String SESSION_DELETED         = "session.deleted";
    public static final String SESSION_STATUS          = "session.status";
    public static final String SESSION_ERROR           = "session.error";
    public static final String SESSION_IDLE            = "session.idle";
    public static final String SESSION_COMPACTED       = "session.compacted";

    public static final String MESSAGE_UPDATED         = "message.updated";
    public static final String MESSAGE_REMOVED         = "message.removed";
    public static final String MESSAGE_PART_UPDATED    = "message.part.updated";
    public static final String MESSAGE_PART_REMOVED    = "message.part.removed";

    public static final String PERMISSION_UPDATED      = "permission.updated";
    public static final String PERMISSION_REPLIED      = "permission.replied";

    public static final String TODO_UPDATED            = "todo.updated";
    public static final String FILE_EDITED             = "file.edited";
}
