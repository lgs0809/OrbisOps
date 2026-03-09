package cn.lgs.orbisops.application.modelpolicy;

import cn.lgs.orbisops.domain.modelpolicy.model.ModelDefaultPolicy;
import cn.lgs.orbisops.domain.modelpolicy.model.ModelUsage;
import cn.lgs.orbisops.domain.modelpolicy.service.ModelDefaultPolicyFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Application service for validated default-model policy reads and mutations. */
public final class ModelDefaultPolicyApplicationService {

    private final ModelDefaultPolicyPort policyPort;
    private final ModelCatalogPort catalogPort;
    private final ModelPolicyAuditPort auditPort;
    private final ModelDefaultPolicyFactory factory;

    public ModelDefaultPolicyApplicationService(ModelDefaultPolicyPort policyPort,
                                                ModelCatalogPort catalogPort,
                                                ModelPolicyAuditPort auditPort) {
        this(policyPort, catalogPort, auditPort, new ModelDefaultPolicyFactory());
    }

    ModelDefaultPolicyApplicationService(ModelDefaultPolicyPort policyPort,
                                         ModelCatalogPort catalogPort,
                                         ModelPolicyAuditPort auditPort,
                                         ModelDefaultPolicyFactory factory) {
        if (policyPort == null) throw new IllegalArgumentException("MODEL_POLICY_PORT_REQUIRED");
        if (catalogPort == null) throw new IllegalArgumentException("MODEL_CATALOG_PORT_REQUIRED");
        if (auditPort == null) throw new IllegalArgumentException("MODEL_POLICY_AUDIT_PORT_REQUIRED");
        if (factory == null) throw new IllegalArgumentException("MODEL_POLICY_FACTORY_REQUIRED");
        this.policyPort = policyPort;
        this.catalogPort = catalogPort;
        this.auditPort = auditPort;
        this.factory = factory;
    }

    public Map<String, Object> get(String projectId) {
        return find(projectId).map(ModelDefaultPolicySnapshot::view).orElse(Map.of());
    }

    public Optional<ModelDefaultPolicySnapshot> find(String projectId) {
        return policyPort.find(value(projectId));
    }

    public Map<String, Object> update(String projectId,
                                      Map<String, Object> request,
                                      String actor) {
        return updatePolicy(projectId, request, actor).view();
    }

    public ModelDefaultPolicySnapshot updatePolicy(String projectId,
                                                   Map<String, Object> request,
                                                   String actor) {
        String operator = required(actor, "MODEL_POLICY_ACTOR_REQUIRED");
        String project = value(projectId);
        Map<String, Object> before = policyPort.find(project)
                .map(ModelDefaultPolicySnapshot::view)
                .orElse(Map.of());
        ModelDefaultPolicy policy = factory.create(project, request);
        for (Map.Entry<ModelUsage, String> entry : policy.modelReferences().entrySet()) {
            if (!entry.getValue().isBlank()) {
                catalogPort.requireAvailable(entry.getValue(), entry.getKey());
            }
        }
        ModelDefaultPolicySnapshot result = policyPort.save(policy);
        Map<String, Object> auditAfter = new LinkedHashMap<>();
        auditAfter.put("policy", result.view());
        auditAfter.put("actor", operator);
        auditPort.record(project, before, auditAfter);
        return result;
    }

    private String required(String input, String error) {
        String normalized = value(input);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }
}
