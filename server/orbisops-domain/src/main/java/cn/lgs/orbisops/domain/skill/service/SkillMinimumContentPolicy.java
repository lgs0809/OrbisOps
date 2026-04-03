package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate;
import java.util.*;
import java.util.regex.Pattern;

/** Deterministic checks supplement content review; they do not claim to detect all semantic risks. */
public final class SkillMinimumContentPolicy {
    private static final Pattern SECRET=Pattern.compile(
            "-----BEGIN (?:[A-Z ]+ )?PRIVATE KEY-----|\\bsk-[A-Za-z0-9_-]{20,}|(?i)(?:password|api[_-]?key|access[_-]?token)\\s*[:=]\\s*[\"']?[A-Za-z0-9/+_-]{16,}");
    public List<String> evaluate(SkillPatchCandidate candidate, Map<String,Object> input) {
        Set<String> tools=new HashSet<>();
        if(input.get("consolidatedExperiences") instanceof List<?> samples) for(Object value:samples) {
            if(!(value instanceof Map<?,?> sample)) continue;
            Object episode=sample.get("acceptedTaskEpisode");
            Map<String,Object> parsed=episode instanceof String raw
                    ?cn.lgs.orbisops.domain.shared.json.CanonicalJson.parseObject(raw):Map.of();
            if(parsed.get("receipts") instanceof List<?> receipts) for(Object item:receipts)
                if(item instanceof Map<?,?> receipt && receipt.get("toolName") instanceof String name) tools.add(name);
        }
        var failures=new LinkedHashSet<String>();
        inspect(candidate.changes(),tools,failures);
        inspect(candidate.artifacts(),tools,failures);
        return List.copyOf(failures);
    }
    private void inspect(Object value,Set<String> tools,Set<String> failures) {
        if(value instanceof Map<?,?> map) {
            for(var entry:map.entrySet()) {
                String key=String.valueOf(entry.getKey()); Object item=entry.getValue();
                if(Set.of("toolName","toolNames","requiredTools").contains(key)) {
                    var names=item instanceof List<?> list?list:List.of(item==null?"":item);
                    if(names.stream().anyMatch(name -> !(name instanceof String) || !tools.contains(name)))
                        failures.add("POLICY_UNSUPPORTED_TOOL_DEPENDENCY");
                }
                if(Set.of("executable","productionWriteAllowed","directLandingAllowed").contains(key)
                        && !Boolean.FALSE.equals(item)) failures.add("POLICY_CAPABILITY_EXPANSION");
                inspect(item,tools,failures);
            }
        } else if(value instanceof List<?> list) list.forEach(v -> inspect(v,tools,failures));
        else if(value instanceof String text && SECRET.matcher(text).find()) failures.add("POLICY_SENSITIVE_CONTENT");
    }
}
