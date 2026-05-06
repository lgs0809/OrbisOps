package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.skill.model.*;
import java.util.*;

/** Exact route snapshot, with live governance supplied separately by its authorized caller. */
final class SkillPublishedCandidateCodec {
    static SkillRuntimeCandidate decode(String json, SkillGovernanceState currentGovernance) {
        var m = CanonicalJson.parseObject(json);
        @SuppressWarnings("unchecked") var r = (Map<String,Object>) m.get("routingProfile");
        Map<String,String> hashes = new TreeMap<>();
        ((Map<?,?>) m.get("artifactHashes")).forEach((k,v) -> hashes.put(String.valueOf(k),String.valueOf(v)));
        return new SkillRuntimeCandidate(s(m,"skillId"),s(m,"projectId"),s(m,"scope"),s(m,"name"),s(m,"description"),
                ((Number)m.get("version")).intValue(),s(m,"skillHash"),s(m,"packageHash"),s(m,"manifestHash"),hashes,
                s(m,"entrypoint"),s(m,"status"),s(m,"updateMode"),((Number)m.get("contentLength")).intValue(),
                new SkillRoutingProfile(s(r,"category"),s(r,"subcategory"),s(r,"searchDescription"),list(r,"useCases"),list(r,"exclusions"),list(r,"keywords")),currentGovernance);
    }
    private static String s(Map<String,Object> m,String key) { return Objects.requireNonNull(m.get(key)).toString(); }
    private static List<String> list(Map<String,Object> m,String key) { return ((List<?>)m.get(key)).stream().map(Object::toString).toList(); }
}
