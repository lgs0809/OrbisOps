package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.skill.model.TaskAcceptanceRequest;
import cn.lgs.orbisops.domain.skill.service.TaskAcceptancePolicy;
import cn.lgs.orbisops.domain.skill.service.TaskReceiptEvidencePolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeJson.*;

/** Revalidates the revision-bound ledger against the retained full receipts before admitting a Skill source. */
final class JdbcTaskAcceptanceIntegrity {
    private final JdbcTemplate jdbc;
    private final TaskAcceptancePolicy policy = new TaskAcceptancePolicy();
    private final TaskReceiptEvidencePolicy evidencePolicy = new TaskReceiptEvidencePolicy();
    JdbcTaskAcceptanceIntegrity(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    boolean valid(String project,String episode,long revision,String run,String acceptance,String condition,String raw,String digest) {
        return validOutcome(project,episode,revision,run,acceptance,condition,raw,digest,"SUCCEEDED");
    }
    boolean validOutcome(String project,String episode,long revision,String run,String acceptance,String condition,String raw,String digest,String outcome) {
        try {
            if (!Set.of("SUCCEEDED","FAILED").contains(outcome)) return false;
            if (!hash(raw).equals(digest)) return false;
            var record=CanonicalJson.parseObject(raw);
            if (!project.equals(record.get("projectId")) || !episode.equals(record.get("episodeId"))
                    || revision!=number(record.get("revision")) || !run.equals(record.get("sourceRunId"))
                    || !acceptance.equals(record.get("acceptanceId")) || !condition.equals(record.get("conditionKey"))
                    || !outcome.equals(record.get("outcome"))) return false;
            if (!(record.get("checks") instanceof List<?> checks) || checks.isEmpty() || checks.size()>20) return false;
            List<TaskAcceptanceRequest.Criterion> criteria = new ArrayList<>();
            List<String> conditions = new ArrayList<>();
            boolean failed=false;
            var scopedReceipts=new JdbcTaskEpisodeRunScope(jdbc).receipts(project,episode);
            for (Object value:checks) {
                var check=map(value);
                var criterion=new TaskAcceptanceRequest.Criterion(text(check.get("resultId")),text(check.get("outputHash")),
                        text(check.get("pointer")),text(check.get("operator")),check.get("expected"));
                criteria.add(criterion);
                var receipts=scopedReceipts.stream().filter(r->criterion.resultId().equals(r.get("result_id"))
                        && "MCP_REMOTE_TOOL".equals(r.get("source"))).toList();
                if (receipts.size()!=1) return false;
                var receipt=receipts.get(0);String output=text(receipt.get("full_output"));
                if (!"SUCCEEDED".equals(receipt.get("status")) || !criterion.outputHash().equals(receipt.get("output_hash"))
                        || !criterion.outputHash().equals(hash(output))) return false;
                var body = evidencePolicy.content(project, CanonicalJson.parseObject(output));
                if (body.isEmpty()) return false;
                var normalized = body.get();
                var current=policy.check(criterion,normalized);
                if (!Set.of("PASSED","FAILED").contains(current.get("verdict")) || !CanonicalJson.stringify(current).equals(CanonicalJson.stringify(check))) return false;
                failed |= "FAILED".equals(current.get("verdict"));
                conditions.add(CanonicalJson.stringify(evidencePolicy.condition(text(receipt.get("tool_name")), normalized)));
            }
            policy.validate(new TaskAcceptanceRequest("integrity-check",revision,text(record.get("goalReview")),criteria));
            return failed=="FAILED".equals(outcome) && condition.equals(hash(CanonicalJson.stringify(conditions.stream().distinct().sorted().toList())));
        } catch (IllegalArgumentException | SecurityException invalid) { return false; }
    }
    @SuppressWarnings("unchecked") private Map<String,Object> map(Object value) {
        return value instanceof Map<?,?> m ? (Map<String,Object>)m : Map.of();
    }
}
