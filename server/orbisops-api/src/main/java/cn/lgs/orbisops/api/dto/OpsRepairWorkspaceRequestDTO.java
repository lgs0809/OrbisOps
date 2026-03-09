package cn.lgs.orbisops.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpsRepairWorkspaceRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String projectId;
    private String serviceId;
    private String environment;
    private String summary;
    private String unifiedDiff;
    private String baseCommit;
}
