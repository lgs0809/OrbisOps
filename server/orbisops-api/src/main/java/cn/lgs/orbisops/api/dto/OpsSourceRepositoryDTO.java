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
public class OpsSourceRepositoryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String repositoryId;
    private String mcpId;
    private String projectId;
    private String name;
    private String localPath;
    private String accessMode;
    private String codeMcpId;
    private String logicalRoot;
    private String defaultRevision;
    private String defaultCommitSha;
    private String status;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
}
