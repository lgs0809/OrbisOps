package cn.lgs.orbisops.application.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagIngestionJob;

import java.util.List;
import java.util.Map;

public record RagIngestionJobView(
        String jobId,
        String status,
        String name,
        String tag,
        List<String> fileNames,
        Long totalBytes,
        String errorMessage,
        String createdAt,
        String updatedAt,
        Map<String, Object> metadata
) {

    public RagIngestionJobView {
        fileNames = fileNames == null ? List.of() : List.copyOf(fileNames);
        metadata = metadata == null ? null : Map.copyOf(metadata);
    }

    public static RagIngestionJobView from(RagIngestionJob job, Map<String, Object> metadata) {
        if (job == null) {
            return null;
        }
        return new RagIngestionJobView(
                job.jobId(),
                job.status().name(),
                job.name(),
                job.tag(),
                job.fileNames(),
                job.totalBytes(),
                job.errorMessage(),
                job.createdAt(),
                job.updatedAt(),
                metadata);
    }

    public RagIngestionJobView withMetadata(Map<String, Object> value) {
        return new RagIngestionJobView(jobId, status, name, tag, fileNames, totalBytes,
                errorMessage, createdAt, updatedAt, value);
    }
}
