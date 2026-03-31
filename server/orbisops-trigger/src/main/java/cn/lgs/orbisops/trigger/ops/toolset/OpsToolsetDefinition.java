package cn.lgs.orbisops.trigger.ops.toolset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpsToolsetDefinition {

    private String toolsetId;
    private String name;
    private String description;
    private String prerequisites;
    @Builder.Default
    private List<String> tags = new ArrayList<>();
    private String sourceType;
    private String adapterType;
    private boolean enabled;
    private boolean readOnlyDefault;
    @Builder.Default
    private List<OpsToolDefinition> tools = new ArrayList<>();
    private String createBy;
    private String updateBy;
    private String createTime;
    private String updateTime;
}
