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
public class OpsCodeDeliveryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String deliveryId;
    private String workspaceId;
    private String projectId;
    private String serviceId;
    private String mode;
    private String branchName;
    private String commitSha;
    private String pullRequestUrl;
    private String ciStatus;
    private String ciUrl;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
}
