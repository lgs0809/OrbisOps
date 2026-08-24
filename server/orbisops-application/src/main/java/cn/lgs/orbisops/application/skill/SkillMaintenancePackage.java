package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import java.util.*;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

/** Bounded text-only maintenance of an existing package. Model output never controls file metadata. */
public final class SkillMaintenancePackage {
    private static final Pattern FENCES=Pattern.compile("(?ms)^(```|~~~).*?^\\1[^\\n]*(?:\\n|$)");
    private static final Pattern NUMBERS=Pattern.compile("(?<![\\p{L}\\d])[-+]?\\d+(?:\\.\\d+)?(?:%|ms|s|m|h)?");
    private final Map<String,Map<String,Object>> files=new LinkedHashMap<>();
    private final Set<String> reachable=new LinkedHashSet<>();

    public SkillMaintenancePackage(String body,List<Map<String,Object>> artifacts) {
        for(var item:artifacts) {
            String path=text(item.get("path"));
            if(path.isBlank() || files.put(path,Map.copyOf(item))!=null) throw invalid();
        }
        files.put("SKILL.md",Map.of("path","SKILL.md","role","ENTRYPOINT","encoding","UTF8","content",body));
        reachable.add("SKILL.md");
        boolean added;
        do {
            added=false;
            for(String current:List.copyOf(reachable)) {
                String content=text(files.get(current).get("content"));
                for(String path:files.keySet()) if(content.contains(path)) added|=reachable.add(path);
            }
        } while(added);
    }

    /** Conservative referenced-text total, including conditional links; not provider billing. */
    public int estimatedReadTokens(ToIntFunction<String> counter) {
        return reachable.stream().map(files::get).filter(this::utf8)
                .mapToInt(file->counter.applyAsInt(text(file.get("content")))).sum();
    }
    public List<Map<String,Object>> evidence() {return List.copyOf(files.values());}
    public Set<String> editablePaths() {
        var result=new LinkedHashSet<String>();
        for(String path:reachable) {
            var file=files.get(path);
            String role=text(file.get("role"));
            if(utf8(file) && (path.equals("SKILL.md") || path.endsWith(".md") &&
                    (role.equals("REFERENCE") || role.equals("RESOURCE")) || path.equals("resources/method.json") && role.equals("RESOURCE"))) result.add(path);
        }
        return Set.copyOf(result);
    }
    public Map<String,String> validate(Map<String,Object> proposal) {
        if(!(proposal.get("changes") instanceof List<?> changes) || changes.size()>files.size()) throw invalid();
        Map<String,String> result=new LinkedHashMap<>();
        for(Object value:changes) {
            if(!(value instanceof Map<?,?> change) || !(change.get("content") instanceof String content)
                    || text(change.get("reason")).isBlank()) throw invalid();
            String path=text(change.get("path"));
            if(!editablePaths().contains(path) || content.isBlank() || result.put(path,content)!=null) throw invalid();
            String old=text(files.get(path).get("content"));
            if(!frontMatter(old).equals(frontMatter(content)) || !matches(FENCES,old).equals(matches(FENCES,content))) throw invalid();
            // Numeric constants cannot be silently generalized. This is a syntax guard, not semantic proof.
            if(!new HashSet<>(matches(NUMBERS,old)).equals(new HashSet<>(matches(NUMBERS,content)))) throw invalid();
            if(path.endsWith(".json")) requireShape(CanonicalJson.parseObject(old),CanonicalJson.parseObject(content));
        }
        var before=reachable;
        var after=new SkillMaintenancePackage(result.getOrDefault("SKILL.md",text(files.get("SKILL.md").get("content"))),changedArtifacts(result));
        if(!before.equals(after.reachable)) throw invalid();
        result.entrySet().removeIf(e->e.getValue().equals(text(files.get(e.getKey()).get("content"))));
        if(!result.isEmpty() && after.estimatedReadTokens(SkillMaintenancePackage::bytes)>=estimatedReadTokens(SkillMaintenancePackage::bytes))
            throw invalid();
        return Map.copyOf(result);
    }
    public List<Map<String,Object>> changedArtifacts(Map<String,String> changes) {
        return files.entrySet().stream().filter(e->!e.getKey().equals("SKILL.md")).map(e->{
            var copy=new LinkedHashMap<>(e.getValue());
            if(changes.containsKey(e.getKey())) {
                copy.put("content",changes.get(e.getKey()));copy.remove("contentHash");copy.remove("sizeBytes");
            }
            return (Map<String,Object>)copy;
        }).toList();
    }
    public Map<String,Object> mutation(Map<String,String> changes) {
        var result=new LinkedHashMap<String,Object>();
        if(changes.containsKey("SKILL.md")) result.put("content",changes.get("SKILL.md"));
        if(changes.keySet().stream().anyMatch(p->!p.equals("SKILL.md"))) result.put("artifacts",changedArtifacts(changes));
        return result;
    }
    private void requireShape(Object before,Object after) {
        if(before instanceof Map<?,?> a && after instanceof Map<?,?> b && a.keySet().equals(b.keySet())) {
            for(Object key:a.keySet()) requireShape(a.get(key),b.get(key));return;
        }
        if(before instanceof List<?> a && after instanceof List<?> b) {
            // Strings may be semantically consolidated. Structured records retain their shape and order.
            if(a.stream().allMatch(String.class::isInstance) && b.stream().allMatch(String.class::isInstance)
                    && (a.isEmpty()==b.isEmpty())) return;
            if(a.size()!=b.size()) throw invalid();
            for(int i=0;i<a.size();i++) requireShape(a.get(i),b.get(i));return;
        }
        if(before instanceof String && after instanceof String) return;
        if(!Objects.equals(before,after)) throw invalid();
    }
    private boolean utf8(Map<String,Object> file) {return "UTF8".equals(file.getOrDefault("encoding","UTF8"));}
    private static String frontMatter(String value) {
        if(!value.startsWith("---\n")) return "";
        int end=value.indexOf("\n---",4);return end<0?value:value.substring(0,end+4);
    }
    private static List<String> matches(Pattern pattern,String value) {return pattern.matcher(value).results().map(java.util.regex.MatchResult::group).toList();}
    private static String text(Object value) {return Objects.toString(value,"");}
    private static int bytes(String value) {return value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;}
    private static IllegalArgumentException invalid() {return new IllegalArgumentException("SKILL_MAINTENANCE_UNSAFE_STRUCTURE");}
}
