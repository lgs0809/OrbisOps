package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.SemanticMemoryDocumentSnapshot;
import cn.lgs.orbisops.domain.memory.service.MemoryContentHashPolicy;
import cn.lgs.orbisops.domain.memory.service.SemanticMemoryPolicy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

/** Application orchestration for vector-first semantic-memory writes with lexical fallback. */
public class SemanticMemoryWriteApplicationService {

    private final SemanticMemoryPolicy semanticPolicy;
    private final SemanticVectorWritePort vectorWritePort;
    private final SemanticLexicalWritePort lexicalWritePort;
    private final SemanticMemoryWriteFailurePort failurePort;

    public SemanticMemoryWriteApplicationService(
            SemanticMemoryPolicy semanticPolicy,
            SemanticVectorWritePort vectorWritePort,
            SemanticLexicalWritePort lexicalWritePort,
            SemanticMemoryWriteFailurePort failurePort) {
        this.semanticPolicy = semanticPolicy == null
                ? new SemanticMemoryPolicy(new MemoryContentHashPolicy())
                : semanticPolicy;
        this.vectorWritePort = vectorWritePort;
        this.lexicalWritePort = lexicalWritePort;
        this.failurePort = failurePort;
    }

    public static SemanticMemoryWriteApplicationService withDefaultPolicy(
            SemanticVectorWritePort vectorWritePort,
            SemanticLexicalWritePort lexicalWritePort,
            SemanticMemoryWriteFailurePort failurePort) {
        return new SemanticMemoryWriteApplicationService(
                new SemanticMemoryPolicy(new MemoryContentHashPolicy()),
                vectorWritePort,
                lexicalWritePort,
                failurePort);
    }

    public SemanticMemoryWriteResult write(SemanticMemoryWriteCommand command) {
        if (command == null || !hasText(command.content())) {
            return SemanticMemoryWriteResult.skipped();
        }
        SemanticMemoryDocumentSnapshot document = project(command);
        boolean pending=command.embeddingConfigured();
        String pendingReason=command.embeddingAvailable()?"VECTOR_WRITE_TEMPORARILY_FAILED":"EMBEDDING_TEMPORARILY_UNAVAILABLE";
        if (command.embeddingConfigured() && command.embeddingAvailable() && vectorWritePort != null) {
            try {
                vectorWritePort.writeVector(projection(document,"VECTOR"));
                return SemanticMemoryWriteResult.vector();
            } catch (RuntimeException error) {
                observeFailure("vector-write", error);
            }
        }
        if (lexicalWritePort == null) {
            return SemanticMemoryWriteResult.skipped();
        }
        try {
            lexicalWritePort.writeLexical(projection(document,"LEXICAL"));
            return pending ? SemanticMemoryWriteResult.lexicalPending(pendingReason) : SemanticMemoryWriteResult.lexical();
        } catch (RuntimeException error) {
            observeFailure("lexical-write", error);
            return SemanticMemoryWriteResult.skipped();
        }
    }

    SemanticMemoryDocumentSnapshot project(SemanticMemoryWriteCommand command) {
        Map<String, Object> metadata = new LinkedHashMap<>(semanticPolicy.baseMetadata(
                command.sessionId(),
                command.userId(),
                "message"));
        metadata.put("role", hasText(command.role()) ? command.role() : "assistant");
        metadata.put("created_at", command.createdAt());
        putIfPresent(metadata, "turn_index", command.metadata().get("turn_index"));
        putIfPresent(metadata, "created_at_epoch_ms", command.metadata().get("created_at_epoch_ms"));
        metadata.put("memory_status", command.metadata().getOrDefault("memory_status", "ACTIVE"));
        metadata.put("origin_metadata", command.metadata());
        putIfPresent(metadata, "projectId", command.metadata().get("projectId"));
        putIfPresent(metadata, "messageSeq", command.metadata().get("messageSeq"));
        for(String key:List.of("semantic_job_id","semantic_epoch","semantic_source_hash"))
            putIfPresent(metadata,key,command.metadata().get(key));
        String identity = command.sessionId() + ":" + command.metadata().getOrDefault("messageSeq",
                new MemoryContentHashPolicy().stableHash(command.role() + ":" + command.content()));
        return new SemanticMemoryDocumentSnapshot(UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString(),
                command.content(), metadata);
    }

    private SemanticMemoryDocumentSnapshot projection(SemanticMemoryDocumentSnapshot document,String kind) {
        if(!(document.metadata().get("semantic_job_id") instanceof Number))return document;
        var metadata=new LinkedHashMap<>(document.metadata());metadata.put("semantic_projection",kind);
        String id=document.id();
        if("VECTOR".equals(kind)) {
            String epochIdentity=id+":vector:"+metadata.get("semantic_job_id")+":"+metadata.get("semantic_epoch");
            id=UUID.nameUUIDFromBytes(epochIdentity.getBytes(StandardCharsets.UTF_8)).toString();
        }
        return new SemanticMemoryDocumentSnapshot(id,document.content(),metadata);
    }

    private void observeFailure(String operation, RuntimeException error) {
        if (failurePort == null) {
            return;
        }
        try {
            failurePort.onWriteFailure(operation, error);
        } catch (RuntimeException ignored) {
        }
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
