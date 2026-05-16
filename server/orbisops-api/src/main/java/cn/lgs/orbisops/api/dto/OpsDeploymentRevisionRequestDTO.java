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
public class OpsDeploymentRevisionRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String projectId;
    private String repositoryId;
    private String environment;
    private String serviceName;
    private String revision;
    private String imageRef;
}
