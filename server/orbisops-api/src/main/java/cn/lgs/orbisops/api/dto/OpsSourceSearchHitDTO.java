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
public class OpsSourceSearchHitDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String repositoryId;
    private String commitSha;
    private String path;
    private Integer line;
    private String text;
}
