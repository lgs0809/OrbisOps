package cn.lgs.orbisops.trigger.application.changepackage;

import cn.lgs.orbisops.application.changepackage.ChangePackageValidationProofPort;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageValidationWriteback;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import cn.lgs.orbisops.trigger.ops.toolset.OpsTrustedProofService;
import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class OpsChangePackageValidationSupportAdapter implements ChangePackageValidationProofPort {

    private static final Set<ChangePackageType> SOURCE_PROOF_REQUIRED_TYPES = Set.of(
            ChangePackageType.GIT_BRANCH_REPAIR,
            ChangePackageType.MCP_OPERATION_PACKAGE,
            ChangePackageType.CONFIG_PACKAGE,
            ChangePackageType.RELEASE_PACKAGE);

    private final OpsTrustedProofService trustedProofService;

    public OpsChangePackageValidationSupportAdapter(
            ObjectProvider<OpsTrustedProofService> trustedProofServiceProvider) {
        this.trustedProofService = trustedProofServiceProvider.getIfAvailable();
    }

    @Override
    public void verifySourceProofs(ChangePackageCurrent current,
                                   ChangePackageVersion sourceVersion,
                                   Map<String, Object> validationReport) {
        Map<String, Object> sourceSnapshot = sourceVersion.snapshot().toMap();
        ChangePackageType packageType = ChangePackageType.require(text(sourceSnapshot.get("packageType")));
        if (!SOURCE_PROOF_REQUIRED_TYPES.contains(packageType)) return;
        OpsTrustedProofService proofService = requireProofService(
                "TrustedProofService 未初始化，validation source proof 必须 fail closed");
        List<Map<String, Object>> refs = mapList(firstNonNull(
                validationReport.get("trustedProofRefs"), validationReport.get("proofRefs"), List.of()));
        if (refs.isEmpty()) {
            throw new IllegalStateException(
                    "VALIDATION_SOURCE_PROOF_MISSING：可执行 ChangePackage 不能只靠验证报告声明通过");
        }
        String projectId = text(sourceSnapshot.get("projectId"));
        String riskLevel = fallback(text(sourceSnapshot.get("riskLevel")), "MEDIUM");
        boolean verified = refs.stream().anyMatch(ref -> {
            String proofType = text(ref.get("proofType"));
            String external = text(firstNonNull(ref.get("proofId"), ref.get("externalRunId")));
            return !proofType.isBlank() && proofService.verifyTrustedProof(
                    projectId,
                    current.packageId(),
                    sourceVersion.version(),
                    sourceVersion.packageHash(),
                    riskLevel,
                    proofType,
                    external);
        });
        if (!verified) {
            throw new IllegalStateException(
                    "VALIDATION_SOURCE_PROOF_UNTRUSTED：报告中的 proof 不存在于平台可信 proof 存储");
        }
    }

    @Override
    public void recordWritebackProof(ChangePackageCurrent current,
                                     ChangePackageValidationWriteback writeback,
                                     String actor) {
        OpsTrustedProofService proofService = requireProofService(
                "TrustedProofService 未初始化，validation proof writeback 必须 fail closed");
        Map<String, Object> snapshot = writeback.snapshot().toMap();
        Map<String, Object> report = writeback.validationReport();
        List<Map<String, Object>> sourceProofRefs = mapList(firstNonNull(
                report.get("trustedProofRefs"), report.get("proofRefs"), List.of()));
        Map<String, Object> firstSourceProof = sourceProofRefs.isEmpty() ? Map.of() : sourceProofRefs.get(0);
        String sourceOutputHash = firstNonBlank(
                report.get("testProofHash"),
                report.get("outputHash"),
                report.get("sourceOutputHash"),
                firstSourceProof.get("outputHash"),
                firstSourceProof.get("externalRunId"),
                firstSourceProof.get("proofId"),
                snapshot.get("testProofHash"));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourcePackageVersion", writeback.sourceVersion());
        metadata.put("sourcePackageHash", writeback.sourcePackageHash());
        metadata.put("sourceProofRefs", sourceProofRefs);
        metadata.put("sourceOutputHash", sourceOutputHash);
        metadata.put("targetPackageVersion", writeback.targetVersion());
        metadata.put("targetPackageHash", writeback.targetPackageHash());
        metadata.put("reasonCode", writeback.reasonCode());

        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("proofId", writeback.proofId());
        proof.put("projectId", current.projectId());
        proof.put("packageId", current.packageId());
        proof.put("packageVersion", writeback.targetVersion());
        proof.put("packageHash", writeback.targetPackageHash());
        proof.put("riskLevel", fallback(text(snapshot.get("riskLevel")), "MEDIUM"));
        proof.put("proofType", "VALIDATION_PROOF_WRITEBACK");
        proof.put("source", "TOOL_EXECUTED");
        proof.put("externalRunId", sourceOutputHash);
        proof.put("resultStatus", "PASSED");
        proof.put("metadata", metadata);
        proofService.recordTrustedProof(proof, actor);
    }

    private OpsTrustedProofService requireProofService(String message) {
        if (trustedProofService == null) throw new IllegalStateException(message);
        return trustedProofService;
    }

    private List<Map<String, Object>> mapList(Object raw) {
        Object value = parseMaybeJson(raw);
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : iterable) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> entry = new LinkedHashMap<>();
                map.forEach((key, itemValue) -> entry.put(String.valueOf(key), itemValue));
                result.add(entry);
            }
        }
        return result;
    }

    private Object parseMaybeJson(Object raw) {
        if (raw instanceof String text && !text.isBlank()) {
            String trimmed = text.trim();
            if (trimmed.startsWith("[")) return JSON.parseArray(trimmed);
            if (trimmed.startsWith("{")) return JSON.parseObject(trimmed);
        }
        return raw;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            String candidate = text(value);
            if (!candidate.isBlank()) return candidate;
        }
        return "";
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
