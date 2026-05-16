package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.runtime.workflow.adapter.repository.IWorkflowToolCallBudgetRepository;
import cn.lgs.orbisops.domain.runtime.workflow.service.WorkflowToolCallBudgetPolicy;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Run row lock serializes budget consumption across nodes, instances and recovered workers. */
@Repository
public class JdbcWorkflowToolCallBudgetRepository implements IWorkflowToolCallBudgetRepository {
    private final JdbcTemplate jdbc;
    private final cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentDefinitionRepository definitions;
    private final cn.lgs.orbisops.domain.runtime.workflow.adapter.repository.IPlatformRuntimeDefinitionSource platformDefinitions;

    public JdbcWorkflowToolCallBudgetRepository(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc,
            cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentDefinitionRepository definitions) {
        this(jdbc, definitions, (id, version, hash) -> java.util.Optional.empty());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public JdbcWorkflowToolCallBudgetRepository(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc,
            cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentDefinitionRepository definitions,
            cn.lgs.orbisops.domain.runtime.workflow.adapter.repository.IPlatformRuntimeDefinitionSource platformDefinitions) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc);
        this.definitions = java.util.Objects.requireNonNull(definitions);
        this.platformDefinitions = java.util.Objects.requireNonNull(platformDefinitions);
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public int reserve(Dispatch dispatch) {
        var runs = jdbc.queryForList("""
                SELECT project_id,agent_id,agent_version,agent_definition_hash,status,cancel_requested
                FROM ai_ops_agent_run WHERE run_id=? FOR UPDATE
                """, dispatch.runId());
        if (runs.isEmpty()) return 0; // Control-plane and legacy invocations have no graph budget.
        var run = runs.get(0);
        if (!dispatch.projectId().equals(run.get("project_id"))) throw new SecurityException("WORKFLOW_TOOL_BUDGET_PROJECT_MISMATCH");
        if (run.get("agent_version") == null) return 0;
        var definition = definitions.findVersion(String.valueOf(run.get("agent_id")), ((Number) run.get("agent_version")).intValue())
                .map(version -> CanonicalJson.parseObject(version.definitionJson()))
                .or(() -> platformDefinitions.find(String.valueOf(run.get("agent_id")),
                        ((Number) run.get("agent_version")).intValue(), String.valueOf(run.get("agent_definition_hash"))))
                .orElseThrow(() -> new SecurityException("WORKFLOW_TOOL_BUDGET_DEFINITION_MISSING"));
        int limit = new WorkflowToolCallBudgetPolicy().limit(definition);
        if (!String.valueOf(run.get("agent_definition_hash")).equals(String.valueOf(definition.get("definitionHash")))) {
            throw new SecurityException("WORKFLOW_TOOL_BUDGET_DEFINITION_CHANGED");
        }
        if (limit == 0) return 0;
        Object canceled = run.get("cancel_requested");
        if (!"RUNNING".equals(run.get("status")) || Boolean.TRUE.equals(canceled)
                || canceled instanceof Number number && number.intValue() != 0) {
            throw new SecurityException("WORKFLOW_TOOL_BUDGET_RUN_NOT_ACTIVE");
        }
        int used = jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_workflow_tool_dispatch WHERE run_id=?", Integer.class, dispatch.runId());
        if (used >= limit) throw new SecurityException("WORKFLOW_REAL_TOOL_CALL_BUDGET_EXHAUSTED:" + limit);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_workflow_tool_dispatch WHERE run_id=? AND logical_call_id=? AND physical_attempt=?",
                Integer.class, dispatch.runId(), dispatch.logicalCallId(), dispatch.physicalAttempt()) != 0) {
            throw new SecurityException("WORKFLOW_TOOL_DISPATCH_ALREADY_RESERVED");
        }
        jdbc.update("""
                INSERT INTO ai_ops_workflow_tool_dispatch
                (run_id,project_id,node_id,mcp_id,tool_name,logical_call_id,physical_attempt,request_id,budget_limit,definition_hash)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """, dispatch.runId(), dispatch.projectId(), dispatch.nodeId(), dispatch.mcpId(), dispatch.toolName(),
                dispatch.logicalCallId(), dispatch.physicalAttempt(), dispatch.requestId(), limit, run.get("agent_definition_hash"));
        return used + 1;
    }
}
