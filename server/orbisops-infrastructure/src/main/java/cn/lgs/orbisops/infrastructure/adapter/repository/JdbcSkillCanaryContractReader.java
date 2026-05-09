package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.runtime.contextbundle.service.RuntimeContextBundlePolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeJson.*;

/** Freezes observed bindings before exposure; no historical model identity is invented. */
final class JdbcSkillCanaryContractReader {
    private static final String VERSION="skill-canary-contract-v1";
    private static final String ACCEPTANCE="task-receipt-acceptance-v1";
    private final JdbcTemplate jdbc;
    JdbcSkillCanaryContractReader(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    Map<String,Object> freeze(SkillPatchCandidate candidate) {
        var binding=bindings(candidate.projectId(),candidate.sourceRunId());
        if(binding.isEmpty()) return Map.of();
        var conditions=new TreeSet<String>();
        var clusters=jdbc.queryForList("SELECT DISTINCT cluster_key FROM ai_ops_skill_verified_contribution WHERE project_id=? AND agent_id=? AND run_id=?",
                candidate.projectId(),candidate.agentId(),candidate.sourceRunId());
        for(var cluster:clusters) for(var source:new JdbcVerifiedSkillSourceReader(jdbc).sources(candidate.projectId(),candidate.agentId(),text(cluster.get("cluster_key"))))
            conditions.add(text(source.get("condition_key")));
        conditions.remove("");
        if(conditions.size()<2) return Map.of();
        var contract=new LinkedHashMap<String,Object>(binding);
        contract.put("version",VERSION);contract.put("candidateHash",candidate.candidateHash());
        contract.put("acceptancePolicy",ACCEPTANCE);contract.put("conditionKeys",List.copyOf(conditions));
        contract.put("contractHash",hash(CanonicalJson.stringify(contract)));
        return Map.copyOf(contract);
    }
    boolean matches(SkillReleaseSnapshot release,String run) {
        var contract=contract(release);
        if(contract.isEmpty()) return false;
        var actual=bindings(release.projectId(),run);
        return !actual.isEmpty() && actual.entrySet().stream().allMatch(e->e.getValue().equals(contract.get(e.getKey())));
    }
    Set<String> conditions(SkillReleaseSnapshot release) {
        var value=contract(release).get("conditionKeys");
        if(!(value instanceof List<?> keys) || keys.size()<2 || keys.stream().anyMatch(k->!(k instanceof String s) || !s.matches("[0-9a-f]{64}"))) return Set.of();
        return new HashSet<>(keys.stream().map(Object::toString).toList());
    }
    boolean candidateMatches(SkillReleaseSnapshot release,String refsJson) {
        Object refs=CanonicalJson.parse(refsJson);
        var frozen=contract(release);
        if(frozen.isEmpty() || !(refs instanceof List<?> values)) return false;
        return values.stream().anyMatch(value->value instanceof Map<?,?> ref
                && (release.releaseId().equals(ref.get("releaseId"))
                    && frozen.get("candidateHash").equals(ref.get("skillHash"))
                || release.releasedVersion()>0 && !release.releasedSkillHash().isBlank()
                    && release.targetSkillId().equals(ref.get("skillId"))
                    && release.releasedVersion()==number(ref.get("version"))
                    && release.releasedSkillHash().equals(ref.get("skillHash"))
                    && "PROJECT".equals(ref.get("scope"))));
    }
    private Map<String,Object> contract(SkillReleaseSnapshot release) {
        if(!(release.metadata().get("observationContract") instanceof Map<?,?> raw)) return Map.of();
        var contract=new LinkedHashMap<String,Object>();raw.forEach((key,value)->contract.put(key.toString(),value));
        String digest=text(contract.remove("contractHash"));
        if(!hash(CanonicalJson.stringify(contract)).equals(digest) || !VERSION.equals(contract.get("version"))
                || !ACCEPTANCE.equals(contract.get("acceptancePolicy"))) return Map.of();
        return contract;
    }
    private Map<String,Object> bindings(String project,String run) {
        var bundles=jdbc.queryForList("SELECT bundle_json,bundle_hash,used_skill_version_refs_json FROM ai_ops_runtime_context_bundle WHERE project_id=? AND run_id=? ORDER BY id",project,run);
        if(bundles.isEmpty()) return Map.of();
        var toolsets=new TreeSet<String>();
        try {
            for(var row:bundles) {
                var bundle=CanonicalJson.parseObject(text(row.get("bundle_json")));
                if(!new RuntimeContextBundlePolicy().hashObject(bundle).equals(text(row.get("bundle_hash")))) return Map.of();
                String boundary=text(bundle.get("toolsetBoundaryHash"));
                if(!boundary.matches("[0-9a-f]{64}")) return Map.of();
                toolsets.add(boundary);
            }
            var models=new TreeSet<String>();
            for(var row:jdbc.queryForList("SELECT payload_json FROM ai_ops_agent_node_trace WHERE run_id=? AND event_type='RUNTIME_RESOURCES' AND status='SUCCEEDED' ORDER BY id",run)) {
                var event=CanonicalJson.parseObject(text(row.get("payload_json")));
                if(!project.equals(event.get("projectId"))) return Map.of();
                String name=text(event.get("modelName")),binding=text(event.get("modelBindingHash"));
                if(!Set.of("gpt-5.6-luna","gpt-5.6-terra").contains(name) || !binding.matches("[0-9a-f]{64}")) return Map.of();
                models.add(CanonicalJson.stringify(Map.of("modelName",name,"bindingHash",binding)));
            }
            if(models.isEmpty()) return Map.of();
            return Map.of("modelBindings",List.copyOf(models),"toolsetBindings",List.copyOf(toolsets));
        } catch(IllegalArgumentException invalid) { return Map.of(); }
    }
}
