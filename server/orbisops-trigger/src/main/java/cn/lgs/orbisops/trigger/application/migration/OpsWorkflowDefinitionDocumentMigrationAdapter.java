package cn.lgs.orbisops.trigger.application.migration;

import cn.lgs.orbisops.application.migration.WorkflowDefinitionDocumentMigrationPort;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowDefinition;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.trigger.application.agentdefinition.AgentWorkflowDefinitionMigrator;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionSnapshotMapper;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Component;

/** ACL from persisted JSON documents to the authoritative typed workflow migrator. */
@Component
public final class OpsWorkflowDefinitionDocumentMigrationAdapter
        implements WorkflowDefinitionDocumentMigrationPort {

    private final AgentWorkflowDefinitionMigrator migrator;
    private final OpsAgentDefinitionSnapshotMapper snapshots;

    public OpsWorkflowDefinitionDocumentMigrationAdapter(
            AgentWorkflowDefinitionMigrator migrator,
            OpsAgentDefinitionSnapshotMapper snapshots) {
        if (migrator == null || snapshots == null) {
            throw new IllegalArgumentException("WORKFLOW_DEFINITION_MIGRATION_DEPENDENCIES_REQUIRED");
        }
        this.migrator = migrator;
        this.snapshots = snapshots;
    }

    @Override
    public MigrationDocument migrate(String definitionJson) {
        String source = definitionJson == null ? "" : definitionJson.trim();
        if (source.isBlank()) {
            return MigrationDocument.review("WORKFLOW_DEFINITION_JSON_MISSING");
        }
        try {
            Object original = JSON.parse(source);
            OpsAgentDefinition definition = JSON.parseObject(source, OpsAgentDefinition.class);
            if (definition == null) {
                return MigrationDocument.review("WORKFLOW_DEFINITION_JSON_INVALID");
            }
            int originalSchema = definition.getSchemaVersion() == null
                    ? 0
                    : definition.getSchemaVersion();
            migrator.migrate(definition);
            String migrated = JSON.toJSONString(definition);
            String definitionHash = snapshots.definitionHash(definition);
            boolean current = originalSchema == AgentWorkflowDefinition.CURRENT_SCHEMA_VERSION
                    && CanonicalObjectHasher.sha256(original)
                    .equals(CanonicalObjectHasher.sha256(JSON.parse(migrated)));
            return current
                    ? MigrationDocument.current(migrated, definitionHash)
                    : MigrationDocument.migrated(migrated, definitionHash);
        } catch (RuntimeException error) {
            return MigrationDocument.review("WORKFLOW_DEFINITION_JSON_UNSUPPORTED");
        }
    }
}
