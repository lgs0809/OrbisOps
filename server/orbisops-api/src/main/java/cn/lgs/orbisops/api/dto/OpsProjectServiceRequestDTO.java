package cn.lgs.orbisops.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpsProjectServiceRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String serviceId;
    private String projectId;
    private String name;
    private String repositoryId;
    private String modulePath;
    private String buildProfile;
    private String artifactPath;
    private String deploymentResourceId;
    private String healthUrl;

    @Builder.Default
    private List<String> smokeUrls = new ArrayList<>();
}
