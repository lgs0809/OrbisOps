package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackageAuditPayloadBoundaryArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/changepackage/model/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/changepackage/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/";

    @Test
    void domainEventAndApprovalOwnImmutableTypedMaps() throws IOException {
        String event = read(DOMAIN + "ChangePackageEvent.java");
        String approval = read(DOMAIN + "ChangePackageApproval.java");

        assertAll(
                () -> assertTrue(event.contains("Map<String, Object> payload")),
                () -> assertTrue(event.contains("Collections.unmodifiableMap")),
                () -> assertFalse(event.contains("String payloadJson")),
                () -> assertFalse(event.contains("payloadJson")),
                () -> assertTrue(approval.contains("Map<String, Object> metadata")),
                () -> assertTrue(approval.contains("Collections.unmodifiableMap")),
                () -> assertFalse(approval.contains("String metadataJson")),
                () -> assertFalse(approval.contains("metadataJson")),
                () -> assertFalse(event.contains("com.alibaba.fastjson")),
                () -> assertFalse(approval.contains("com.alibaba.fastjson")),
                () -> assertFalse(event.contains("payload_json")),
                () -> assertFalse(approval.contains("metadata_json")));
    }

    @Test
    void applicationWritesTypedAuditPayloadsWithoutSerialization() throws IOException {
        String approval = read(APPLICATION + "ChangePackageApprovalUseCase.java");
        String prepare = read(APPLICATION + "PrepareChangePackageUseCase.java");
        String cleanup = read(APPLICATION + "ChangePackageCleanupUseCase.java");
        String validation = read(APPLICATION + "ChangePackageValidationWritebackUseCase.java");
        String landing = read(APPLICATION + "ChangePackageLandingProcessManager.java");

        assertAll(
                () -> assertFalse(approval.contains("com.alibaba.fastjson")),
                () -> assertFalse(prepare.contains("com.alibaba.fastjson")),
                () -> assertTrue(approval.contains("assessment.adminConfirmation(),\n                metadata")),
                () -> assertTrue(approval.contains("summary,\n                payload,")),
                () -> assertTrue(prepare.contains("text(actor), summary, payload, null")),
                () -> assertTrue(cleanup.contains("text(actor), summary, payload, null")),
                () -> assertTrue(validation.contains("summary,\n                payload,")),
                () -> assertTrue(landing.contains("payload == null ? Map.of() : payload")),
                () -> assertFalse(approval.contains("JSON.toJSONString(payload)")),
                () -> assertFalse(prepare.contains("JSON.toJSONString(payload)")),
                () -> assertFalse(cleanup.contains("JSON.toJSONString(payload)")),
                () -> assertFalse(validation.contains("JSON.toJSONString(payload)")),
                () -> assertFalse(landing.contains("JSON.toJSONString(payload == null ? Map.of() : payload)")));
    }

    @Test
    void infrastructureOwnsEventAndApprovalJsonColumns() throws IOException {
        String codec = read(INFRASTRUCTURE + "ChangePackageJsonMapCodec.java");
        String eventRepository = read(INFRASTRUCTURE + "JdbcChangePackageEventRepository.java");
        String approvalRepository = read(INFRASTRUCTURE + "JdbcChangePackageApprovalRepository.java");
        String query = read(INFRASTRUCTURE + "JdbcChangePackageQueryAdapter.java");

        assertAll(
                () -> assertTrue(codec.contains("JSON.toJSONString")),
                () -> assertTrue(codec.contains("JSON.parseObject")),
                () -> assertFalse(codec.contains("org.springframework")),
                () -> assertTrue(eventRepository.contains("ChangePackageJsonMapCodec.encode(event.payload())")),
                () -> assertTrue(eventRepository.contains("ChangePackageJsonMapCodec.decode")),
                () -> assertTrue(approvalRepository.contains("ChangePackageJsonMapCodec.encode(approval.metadata())")),
                () -> assertTrue(query.contains("row.put(\"payload\", event.payload())")),
                () -> assertTrue(query.contains("ChangePackageJsonMapCodec.encode(event.payload())")),
                () -> assertFalse(eventRepository.contains("event.payloadJson()")),
                () -> assertFalse(approvalRepository.contains("approval.metadataJson()")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
