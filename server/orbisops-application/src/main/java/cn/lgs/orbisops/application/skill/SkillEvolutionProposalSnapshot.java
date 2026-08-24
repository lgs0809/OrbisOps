package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import java.util.Map;

/** Copies through canonical JSON so a model adapter cannot mutate the retained authoring input. */
public record SkillEvolutionProposalSnapshot(String planId,String planHash,String inputJson,String authoredJson) {
    public Map<String,Object> input() { return CanonicalJson.parseObject(inputJson); }
    public Map<String,Object> authored() { return authoredJson==null || authoredJson.isBlank()?Map.of():CanonicalJson.parseObject(authoredJson); }
}
