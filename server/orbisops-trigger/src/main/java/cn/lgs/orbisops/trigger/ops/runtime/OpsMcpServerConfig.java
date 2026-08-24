package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.changepackage.LandingOperationExecutionBinding;
import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class OpsMcpServerConfig {

    private String name;
    private String description;
    private String projectId;
    private String runId;
    private String agentId;
    private String nodeId;
    private String mcpId;
    private String toolId;
    private String operationId;
    private String toolCallStage;
    private String runtimeAuthority;
    private Instant authorityDeadline;
    private Boolean progressiveManaged;
    private Boolean landingApproved;
    private String changePackageId;
    private String approvedPackageHash;
    private Integer approvedPackageVersion;
    private String internalCaller;
    private String landingRuntimeToken;

    @JsonIgnore
    @JSONField(serialize = false)
    private Boolean verifiedReadOnly;

    @JsonIgnore
    @JSONField(serialize = false)
    private Map<String, Object> verifiedToolSchema;

    @JsonIgnore
    @JSONField(serialize = false)
    @Builder.Default
    private Map<String, Object> workSessionClaim = new LinkedHashMap<>();

    /**
     * Platform-owned bindings for the frozen Landing operations projected onto this run.
     * They are runtime-only and never part of persisted/user-visible MCP configuration.
     */
    @JsonIgnore
    @JSONField(serialize = false)
    @Builder.Default
    private List<LandingOperationExecutionBinding> landingOperationBindings = new ArrayList<>();

    private String transport;
    private String command;
    private String url;
    private Integer timeoutSeconds;

    @Builder.Default
    private List<String> args = new ArrayList<>();

    @Builder.Default
    private Map<String, String> env = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, String> headers = new LinkedHashMap<>();

    @Builder.Default
    private Map<String, String> toolCapabilities = new LinkedHashMap<>();

    @Builder.Default
    private List<String> allowedTools = new ArrayList<>();

    @Builder.Default
    private List<String> notificationTools = new ArrayList<>();

    @Builder.Default
    private List<String> blockedTools = new ArrayList<>();

}
