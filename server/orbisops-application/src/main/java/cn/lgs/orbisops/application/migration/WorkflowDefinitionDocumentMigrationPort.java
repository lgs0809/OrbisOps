package cn.lgs.orbisops.application.migration;

/** Converts one persisted Agent definition document through the authoritative workflow migrator. */
public interface WorkflowDefinitionDocumentMigrationPort {

    MigrationDocument migrate(String definitionJson);

    record MigrationDocument(
            String migratedJson,
            String definitionHash,
            boolean current,
            boolean manualReviewRequired,
            String reasonCode) {

        public MigrationDocument {
            migratedJson = migratedJson == null ? "" : migratedJson.trim();
            definitionHash = definitionHash == null ? "" : definitionHash.trim().toLowerCase();
            reasonCode = reasonCode == null ? "" : reasonCode.trim();
            if (!manualReviewRequired && migratedJson.isBlank()) {
                throw new IllegalArgumentException("WORKFLOW_MIGRATION_DOCUMENT_REQUIRED");
            }
            if (!manualReviewRequired && !definitionHash.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("WORKFLOW_MIGRATION_DEFINITION_HASH_INVALID");
            }
        }

        public static MigrationDocument current(String json, String definitionHash) {
            return new MigrationDocument(json, definitionHash, true, false, "");
        }

        public static MigrationDocument migrated(String json, String definitionHash) {
            return new MigrationDocument(
                    json, definitionHash, false, false, "WORKFLOW_SCHEMA_V0_MIGRATABLE");
        }

        public static MigrationDocument review(String reasonCode) {
            return new MigrationDocument("", "", false, true,
                    reasonCode == null || reasonCode.isBlank()
                            ? "WORKFLOW_DEFINITION_MANUAL_REVIEW_REQUIRED"
                            : reasonCode);
        }
    }
}
