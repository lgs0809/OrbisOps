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
public class OpsRepairWorkspaceDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String workspaceId;
    private String projectId;
    private String serviceId;
    private String repositoryId;
    private String environment;
    private String baseCommit;
    private String verifiedCommit;
    private String status;
    private String summary;
    private String unifiedDiff;

    @Builder.Default
    private List<String> changedFiles = new ArrayList<>();

    private String testProfile;
    private String testCommand;
    private Integer testExitCode;
    private String testLog;
    private String artifactPath;
    private String artifactSha256;
    private Long artifactSize;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
}
