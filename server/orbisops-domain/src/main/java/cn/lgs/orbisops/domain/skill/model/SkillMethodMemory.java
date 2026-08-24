package cn.lgs.orbisops.domain.skill.model;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import java.util.*;

/** Private experience memory, never an executable or runtime-retrievable Skill package. */
public final class SkillMethodMemory {
    private SkillMethodMemory() { }
    public record Method(String goal,List<String> conditions,List<String> steps,List<String> acceptance,List<String> toolCategories) {
        public Method {
            goal=required(goal);conditions=bounded(conditions);steps=bounded(steps);
            acceptance=bounded(acceptance);toolCategories=bounded(toolCategories);
        }
        public Map<String,Object> view() {return Map.of("goal",goal,"conditions",conditions,"steps",steps,"acceptance",acceptance,"toolCategories",toolCategories);}
        public String document() {return "目标\n"+goal+"\n适用条件\n"+String.join("\n",conditions)+"\n有效步骤\n"+String.join("\n",steps)
                +"\n验收\n"+String.join("\n",acceptance)+"\n工具类别\n"+String.join("\n",toolCategories);}
        public static Method from(Map<String,Object> value) {
            return new Method((String)value.get("goal"),strings(value.get("conditions")),strings(value.get("steps")),
                    strings(value.get("acceptance")),strings(value.get("toolCategories")));
        }
    }
    public record Fact(String sourceId,String sourceHash,String episodeId,long revision,Method method) {
        public Map<String,Object> view() {return Map.of("sourceId",sourceId,"sourceHash",sourceHash,"episodeId",episodeId,"revision",revision,"method",method.view());}
    }
    public record Group(String groupId,String projectId,long version,String contentHash,Method method,List<Fact> sources) {
        public Group {sources=List.copyOf(sources);}
        public Ref reference() {return new Ref(groupId,version,contentHash);}
        /** The representative is a label, never an authority for the whole group's conditions. */
        public Map<String,Object> view() {return Map.of("groupId",groupId,"version",version,"contentHash",contentHash,
                "representativeMethod",method.view(),"sources",sources.stream().map(Fact::view).toList());}
        public String document() {return String.join("\n\n",sources.stream().map(f->f.method().document()).distinct().toList());}
    }
    public record Ref(String groupId,long version,String contentHash) { }
    public record Extraction(Method method,String auditJson) { }
    public record Decision(String action,String groupId,String reason,String auditJson) {
        public Decision {
            if(!Set.of("APPEND","CREATE","REVIEW").contains(action) || reason==null || reason.isBlank()
                    || groupId==null || ("APPEND".equals(action)==groupId.isBlank()))
                throw new IllegalArgumentException("SKILL_GROUPING_DECISION_INVALID");
        }
    }
    /** A group is a set of immutable methods, not a fabricated concatenated single-task method. */
    public static Method representative(List<Fact> facts) {
        return facts.stream().min(Comparator.comparing(Fact::sourceId)).orElseThrow(
                ()->new IllegalArgumentException("SKILL_GROUPING_SOURCES_EMPTY")).method();
    }
    public static String hash(Method method,List<Fact> sources) {
        return CanonicalObjectHasher.sha256(Map.of("method",method.view(),"sources",sources.stream().map(Fact::view).toList()));
    }
    private static String required(String text) {
        if(text==null || text.isBlank() || text.length()>24_000) throw new IllegalArgumentException("SKILL_GROUPING_METHOD_INVALID");
        return text.trim();
    }
    private static List<String> bounded(List<String> items) {
        if(items==null || items.isEmpty() || items.size()>200) throw new IllegalArgumentException("SKILL_GROUPING_METHOD_INVALID");
        return items.stream().map(SkillMethodMemory::required).distinct().toList();
    }
    private static List<String> strings(Object raw) {
        if(!(raw instanceof List<?> list) || list.stream().anyMatch(x -> !(x instanceof String)))
            throw new IllegalArgumentException("SKILL_GROUPING_METHOD_INVALID");
        return list.stream().map(String.class::cast).toList();
    }
}
