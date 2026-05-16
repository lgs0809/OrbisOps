package cn.lgs.orbisops.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * RAG 文档响应 DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RagDocumentResponseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String fileName;
    private String displayName;
    private String tag;
    private Long size;
    private String updateTime;
    private Boolean previewable;
    private String content;
    private String chunkId;
    private Integer chunkIndex;
    private String source;
    private String documentType;
    private String chunkStrategy;

}
