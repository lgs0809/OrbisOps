package cn.lgs.orbisops.domain.knowledge.rag.model;

import java.util.List;

public record RagIngestionJob(
        String jobId,
        RagIngestionJobStatus status,
        String name,
        String tag,
        List<String> fileNames,
        long totalBytes,
        String errorMessage,
        String createdAt,
        String updatedAt
) {

    public RagIngestionJob {
        if (jobId == null || jobId.isBlank()) throw new IllegalArgumentException("RAG_INGESTION_JOB_ID_REQUIRED");
        if (status == null) throw new IllegalArgumentException("RAG_INGESTION_JOB_STATUS_REQUIRED");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("RAG_INGESTION_NAME_REQUIRED");
        if (tag == null || tag.isBlank()) throw new IllegalArgumentException("RAG_INGESTION_TAG_REQUIRED");
        fileNames = fileNames == null ? List.of() : List.copyOf(fileNames);
        totalBytes = Math.max(0L, totalBytes);
        createdAt = createdAt == null ? "" : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    public static RagIngestionJob pending(String jobId,
                                          String name,
                                          String tag,
                                          List<String> fileNames,
                                          long totalBytes,
                                          String now) {
        return new RagIngestionJob(jobId, RagIngestionJobStatus.PENDING, name, tag,
                fileNames, totalBytes, null, now, now);
    }

    public RagIngestionJob start(String now) {
        requireStatus(RagIngestionJobStatus.PENDING);
        return transition(RagIngestionJobStatus.RUNNING, null, now);
    }

    public RagIngestionJob succeed(String now) {
        requireStatus(RagIngestionJobStatus.RUNNING);
        return transition(RagIngestionJobStatus.SUCCEEDED, null, now);
    }

    public RagIngestionJob fail(String reason, String now) {
        if (status != RagIngestionJobStatus.PENDING && status != RagIngestionJobStatus.RUNNING) {
            throw new IllegalStateException("RAG_INGESTION_JOB_CANNOT_FAIL_FROM_" + status);
        }
        return transition(RagIngestionJobStatus.FAILED, reason, now);
    }

    private RagIngestionJob transition(RagIngestionJobStatus next, String reason, String now) {
        return new RagIngestionJob(jobId, next, name, tag, fileNames, totalBytes,
                reason, createdAt, now);
    }

    private void requireStatus(RagIngestionJobStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("RAG_INGESTION_JOB_EXPECTED_" + expected + "_BUT_WAS_" + status);
        }
    }
}
