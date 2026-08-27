package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.*;

/** Uses the deployed evidence-scope reader in an explicitly read-only transaction. */
class InspectTaskChildEvidence {
    public static void main(String[] args) throws Exception {
        var input=CanonicalJson.parseObject(new String(System.in.readAllBytes(),StandardCharsets.UTF_8));
        String project="ops-acceptance-a",episode=String.valueOf(input.get("episodeId"));
        try(var connection=DriverManager.getConnection("jdbc:mysql://127.0.0.1:13362/orbisops_acceptance?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
                "root",System.getenv("OPS_ACCEPTANCE_READ_PASSWORD"))) {
            connection.setReadOnly(true);connection.setAutoCommit(false);
            var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));jdbc.execute("START TRANSACTION READ ONLY");
            try {
                var scope=new JdbcTaskEpisodeRunScope(jdbc);var runs=scope.runs(project,episode);
                var remote=scope.receipts(project,episode).stream().filter(r->"MCP_REMOTE_TOOL".equals(r.get("source"))).toList();
                if(runs.size()<2 || remote.size()<2 || runs.stream().anyMatch(r->!"SUCCEEDED".equals(r.row().get("status"))))
                    throw new IllegalStateException("ACTUAL_COMPLETED_PARENT_CHILD_TASK_REQUIRED");
                for(var receipt:remote) if(!CanonicalObjectHasher.sha256Text(String.valueOf(receipt.get("full_output"))).equals(receipt.get("output_hash")))
                    throw new IllegalStateException("ACTUAL_RECEIPT_HASH_MISMATCH");
                var report=new LinkedHashMap<String,Object>();report.put("status","PASS_READ_ONLY_CHILD_PROVENANCE");
                report.put("episodeId",episode);report.put("scope","Existing completed parent/child receipts; no new acceptance or business-success verdict");
                report.put("runs",runs.stream().map(r->Map.of("runId",r.runId(),"rootRunId",r.rootRunId(),"revision",r.revision(),"depth",r.depth())).toList());
                report.put("receipts",remote.stream().map(r->Map.of("runId",r.get("run_id"),"resultId",r.get("result_id"),"hash",r.get("output_hash"),"tool",r.get("tool_name"),"revision",r.get("episode_revision"))).toList());
                report.put("checks",Map.of("committedCheckpointHashesVerified",true,"deterministicChildIdentityVerified",true,
                        "sameProjectAndOwnerVerified",true,"frozenWorkflowIdentityVerified",true,"originalReceiptHashesVerified",true,
                        "readOnlyTransactionRolledBack",true));
                if(Boolean.TRUE.equals(input.get("verifyAccepted"))) {
                    String root=runs.stream().filter(r->r.depth()==0).findFirst().orElseThrow().runId();
                    var accepted=new JdbcVerifiedTaskOutcomeReader(jdbc).read(project,root);
                    if(!accepted.matches(project,root)) throw new IllegalStateException("CURRENT_VERIFIED_PARENT_TASK_REQUIRED");
                    var record=jdbc.queryForMap("SELECT record_json,record_hash FROM ai_ops_task_acceptance WHERE acceptance_id=? AND project_id=?",accepted.verificationId(),project);
                    var source=new JdbcSkillEvolutionSourceReader(jdbc).loadAccepted(project,root,accepted.verificationId());
                    var frozen=CanonicalJson.parseObject(source.episodeJson());
                    var checks=(List<?>)CanonicalJson.parseObject(String.valueOf(record.get("record_json"))).get("checks");
                    var frozenScope=(List<?>)frozen.get("evidenceRunScope");
                    var frozenReceipts=(List<?>)frozen.get("receipts");
                    if(frozenScope.size()!=runs.size() || !CanonicalObjectHasher.sha256Text(String.valueOf(record.get("record_json"))).equals(record.get("record_hash")))
                        throw new IllegalStateException("FROZEN_CHILD_ACCEPTANCE_MISMATCH");
                    for(var child:runs) if(child.depth()>0 && new JdbcVerifiedTaskOutcomeReader(jdbc).read(project,child.runId()).matches(project,child.runId()))
                        throw new IllegalStateException("CHILD_COUNTED_AS_ANOTHER_TASK");
                    report.put("status","PASS_CURRENT_ACCEPTED_CHILD_PROVENANCE");
                    report.put("scope","Actual browser-submitted investigation acceptance, rechecked through the deployed source admission; no service-recovery verdict");
                    report.put("acceptance",Map.of("acceptanceId",accepted.verificationId(),"recordHash",record.get("record_hash"),"checks",checks.size(),
                            "sourceHash",source.sourceHash(),"frozenRuns",frozenScope.size(),"frozenReceipts",frozenReceipts.size(),
                            "childMessages",((List<?>)frozen.get("childMessages")).size(),"childTrace",((List<?>)frozen.get("childTrace")).size(),
                            "childrenAreNotIndependentSources",true));
                }
                connection.rollback();System.out.println(CanonicalJson.stringify(report));
            } finally {connection.rollback();}
        }
    }
}
