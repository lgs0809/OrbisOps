package cn.lgs.orbisops.infrastructure.adapter.toolset;

import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationBackfillOutcome;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationCommand;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationContributor;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationInspection;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationStep;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Real JSON compatibility scan for custom Tool Definition safety semantics. */
@Repository
public class JdbcToolDefinitionSemanticsMigrationContributor
        implements PlatformCapabilityMigrationContributor {

    private static final List<String> REQUIRED_COLUMNS = List.of(
            "project_id", "toolset_id", "tools_json", "enabled");
    private static final Set<String> SAFETY_FIELDS = Set.of(
            "readOnly",
            "writesRepairWorkspace",
            "writesTargetResource",
            "requiresChangePackage",
            "requiresApproval",
            "riskLevel",
            "outputBudgetJson",
            "enabled");
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final JdbcTemplate jdbc;

    public JdbcToolDefinitionSemanticsMigrationContributor(
            @Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> provider) {
        this.jdbc = provider.getIfAvailable();
    }

    @Override
    public PlatformCapabilityMigrationStep step() {
        return PlatformCapabilityMigrationStep.TOOL_DEFINITION_SEMANTICS_PROVIDER;
    }

    @Override
    public PlatformCapabilityMigrationInspection inspect(
            PlatformCapabilityMigrationCommand command) {
        if (jdbc == null) return failed("TOOL_DEFINITION_STORE_UNAVAILABLE");
        if (!schemaReady()) return failed("TOOL_DEFINITION_SCHEMA_MISSING");
        List<ToolsetRow> rows = rows(0);
        long current = 0L;
        long backfillable = 0L;
        long manual = 0L;
        Set<String> reasons = new LinkedHashSet<>();
        for (ToolsetRow row : rows) {
            Analysis analysis = analyze(row.toolsJson());
            if (analysis.manualReviewRequired()) {
                manual++;
                reasons.addAll(analysis.reasonCodes());
            } else if (analysis.backfillRequired()) {
                backfillable++;
                reasons.addAll(analysis.reasonCodes());
            } else {
                current++;
            }
        }
        return new PlatformCapabilityMigrationInspection(
                step(), rows.size(), current, backfillable, manual,
                true, true, reasons.stream().sorted().toList());
    }

    @Override
    public PlatformCapabilityMigrationBackfillOutcome backfill(
            PlatformCapabilityMigrationCommand command,
            PlatformCapabilityMigrationInspection inspection) {
        if (jdbc == null || inspection.backfillableRows() == 0) {
            return PlatformCapabilityMigrationBackfillOutcome.none(step());
        }
        int limit = Math.max(1, Math.min(command.batchSize(), 10_000));
        long attempted = 0L;
        long backfilled = 0L;
        long manual = 0L;
        long failed = 0L;
        Set<String> reasons = new LinkedHashSet<>();
        for (ToolsetRow row : rows(limit)) {
            Analysis analysis = analyze(row.toolsJson());
            if (analysis.manualReviewRequired()) {
                manual++;
                reasons.addAll(analysis.reasonCodes());
                continue;
            }
            if (!analysis.backfillRequired()) continue;
            attempted++;
            int updated = jdbc.update("""
                    UPDATE ai_ops_toolset
                       SET tools_json=?, enabled=0, update_by='SYSTEM_MIGRATION',
                           update_time=CURRENT_TIMESTAMP
                     WHERE project_id=? AND toolset_id=?
                       AND COALESCE(tools_json,'')=?
                    """,
                    analysis.migratedJson(), row.projectId(), row.toolsetId(), text(row.toolsJson()));
            if (updated == 1) {
                backfilled++;
                reasons.add("TOOL_DEFINITION_UNKNOWN_SEMANTICS_QUARANTINED");
            } else {
                failed++;
                reasons.add("TOOL_DEFINITION_CAS_DRIFT");
            }
        }
        return new PlatformCapabilityMigrationBackfillOutcome(
                step(), attempted, backfilled, manual, failed,
                reasons.stream().sorted().toList());
    }

    private Analysis analyze(String toolsJson) {
        String source = text(toolsJson);
        if (source.isBlank()) return Analysis.current("[]");
        try {
            JSONArray array = JSON.parseArray(source);
            if (array == null) return Analysis.review("TOOL_DEFINITION_JSON_INVALID");
            List<Object> migrated = new ArrayList<>();
            boolean backfill = false;
            for (Object item : array) {
                if (!(item instanceof Map<?, ?> raw)) {
                    return Analysis.review("TOOL_DEFINITION_ENTRY_INVALID");
                }
                Map<String, Object> tool = stringMap(raw);
                if (text(tool.get("toolName")).isBlank()) {
                    return Analysis.review("TOOL_DEFINITION_TOOL_NAME_MISSING");
                }
                if (text(tool.get("adapterType")).isBlank()) {
                    return Analysis.review("TOOL_DEFINITION_ADAPTER_TYPE_MISSING");
                }
                String risk = text(tool.get("riskLevel")).toUpperCase();
                boolean semanticsReady = tool.keySet().containsAll(SAFETY_FIELDS)
                        && RISK_LEVELS.contains(risk)
                        && isBoolean(tool.get("readOnly"))
                        && isBoolean(tool.get("writesRepairWorkspace"))
                        && isBoolean(tool.get("writesTargetResource"))
                        && isBoolean(tool.get("requiresChangePackage"))
                        && isBoolean(tool.get("requiresApproval"))
                        && isBoolean(tool.get("enabled"));
                if (!semanticsReady) {
                    backfill = true;
                    quarantine(tool);
                }
                migrated.add(tool);
            }
            String migratedJson = JSON.toJSONString(migrated);
            return backfill
                    ? Analysis.backfillable(
                            migratedJson,
                            "TOOL_DEFINITION_EXPLICIT_SEMANTICS_BACKFILL_REQUIRED")
                    : Analysis.current(migratedJson);
        } catch (RuntimeException error) {
            return Analysis.review("TOOL_DEFINITION_JSON_INVALID");
        }
    }

    private void quarantine(Map<String, Object> tool) {
        tool.put("readOnly", true);
        tool.put("writesRepairWorkspace", false);
        tool.put("writesTargetResource", false);
        tool.put("requiresChangePackage", true);
        tool.put("requiresApproval", true);
        tool.put("riskLevel", "HIGH");
        tool.putIfAbsent("outputBudgetJson", Map.of(
                "maxRows", 200,
                "maxBytes", 32768,
                "maxChars", 2000));
        tool.put("enabled", false);
        tool.put("migrationReviewRequired", true);
    }

    private List<ToolsetRow> rows(int limit) {
        String sql = """
                SELECT project_id, toolset_id, tools_json
                  FROM ai_ops_toolset
                 ORDER BY project_id, toolset_id
                """ + (limit > 0 ? " LIMIT " + limit : "");
        return jdbc.query(sql, (rs, rowNum) -> new ToolsetRow(
                rs.getString("project_id"),
                rs.getString("toolset_id"),
                rs.getString("tools_json")));
    }

    private boolean schemaReady() {
        Long table = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                 WHERE table_schema=DATABASE() AND table_name='ai_ops_toolset'
                """, Long.class);
        if (table == null || table != 1L) return false;
        String placeholders = String.join(",", REQUIRED_COLUMNS.stream().map(ignored -> "?").toList());
        Long columns = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema=DATABASE() AND table_name='ai_ops_toolset' "
                        + "AND column_name IN (" + placeholders + ")",
                Long.class,
                REQUIRED_COLUMNS.toArray());
        return columns != null && columns == REQUIRED_COLUMNS.size();
    }

    private PlatformCapabilityMigrationInspection failed(String reason) {
        return new PlatformCapabilityMigrationInspection(
                step(), 0, 0, 0, 0, false, false, List.of(reason));
    }

    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private boolean isBoolean(Object value) {
        return value instanceof Boolean;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record ToolsetRow(String projectId, String toolsetId, String toolsJson) {
    }

    private record Analysis(
            boolean backfillRequired,
            boolean manualReviewRequired,
            String migratedJson,
            List<String> reasonCodes) {

        static Analysis current(String json) {
            return new Analysis(false, false, json, List.of());
        }

        static Analysis backfillable(String json, String reason) {
            return new Analysis(true, false, json, List.of(reason));
        }

        static Analysis review(String reason) {
            return new Analysis(false, true, "", List.of(reason));
        }
    }
}
