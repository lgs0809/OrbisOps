package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackagePreApprovalValidationBoundaryArchitectureTest {

    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/change/";

    @Test
    void validationEntryDelegatesSnapshotStructuredValueProofAndReportResponsibilities() throws IOException {
        String service = read(TRIGGER + "OpsPreApprovalValidationService.java");
        String snapshot = read(TRIGGER + "OpsPreApprovalSnapshotReader.java");
        String structured = read(TRIGGER + "OpsPreApprovalStructuredValueReader.java");
        String proof = read(TRIGGER + "OpsPreApprovalProofService.java");
        String report = read(TRIGGER + "OpsPreApprovalValidationReportFactory.java");

        assertAll(
                () -> assertTrue(service.contains("OpsPreApprovalSnapshotReader snapshotReader")),
                () -> assertTrue(service.contains("OpsPreApprovalStructuredValueReader STRUCTURED_VALUE_READER")),
                () -> assertTrue(service.contains("OpsPreApprovalProofService proofService")),
                () -> assertTrue(service.contains("OpsPreApprovalValidationReportFactory REPORT_FACTORY")),
                () -> assertTrue(service.contains("snapshotReader.load(")),
                () -> assertTrue(service.contains("REPORT_FACTORY.pass(")),
                () -> assertTrue(service.contains("REPORT_FACTORY.fail(")),
                () -> assertFalse(service.contains("proofService.record(")),
                () -> assertFalse(service.contains("proofService.sourceRef(")),
                () -> assertTrue(service.contains("STRUCTURED_VALUE_READER.operations(")),
                () -> assertFalse(service.contains("JSON.toJSONString(")),
                () -> assertFalse(service.contains("JSON.parseObject(")),
                () -> assertFalse(service.contains("JSON.parseArray(")),
                () -> assertFalse(service.contains("TypeReference")),
                () -> assertFalse(service.contains("verifyTrustedProof(")),
                () -> assertFalse(service.contains("recordTrustedProof(")),
                () -> assertFalse(service.contains("private Map<String, Object> snapshot(")),
                () -> assertFalse(service.contains("private List<String> reviseHints(")),
                () -> assertFalse(service.contains("private Object parseMaybeJson(")),
                () -> assertTrue(snapshot.contains("只能验证当前 ChangePackage 版本")),
                () -> assertTrue(snapshot.contains("snapshotJson")),
                () -> assertTrue(snapshot.contains("snapshot_json")),
                () -> assertTrue(structured.contains("import com.alibaba.fastjson.JSON;")),
                () -> assertTrue(structured.contains("JSON.parseObject(")),
                () -> assertTrue(structured.contains("JSON.parseArray(")),
                () -> assertTrue(proof.contains("verifyTrustedProof(")),
                () -> assertTrue(proof.contains("recordTrustedProof(")),
                () -> assertTrue(proof.contains("sourceRef(")),
                () -> assertTrue(report.contains("private List<String> reviseHints(")),
                () -> assertPlain(snapshot),
                () -> assertPlain(structured),
                () -> assertPlain(proof),
                () -> assertPlain(report));
    }

    private void assertPlain(String source) {
        assertFalse(source.contains("@Service"));
        assertFalse(source.contains("ObjectProvider"));
        assertFalse(source.contains("org.springframework"));
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
