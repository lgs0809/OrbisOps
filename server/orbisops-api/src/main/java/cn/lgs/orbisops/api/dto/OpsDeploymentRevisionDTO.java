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
public class OpsDeploymentRevisionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String deploymentId;
    private String projectId;
    private String repositoryId;
    private String environment;
    private String serviceName;
    private String commitSha;
    private String imageRef;
    private String recordedBy;
    private String deployedAt;
    private String updatedAt;
}
