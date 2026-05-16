package cn.lgs.orbisops.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpsExecutionResourceDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String resourceId;
    private String projectId;
    private String name;
    private String workerId;
    private String adapter;
    private String adapterTemplateId;

    @Builder.Default
    private List<String> environments = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> configuration = new LinkedHashMap<>();

    private String status;
    private String createdAt;
    private String updatedAt;
}
