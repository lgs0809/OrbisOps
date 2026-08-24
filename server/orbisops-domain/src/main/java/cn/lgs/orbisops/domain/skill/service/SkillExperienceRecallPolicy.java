package cn.lgs.orbisops.domain.skill.service;
import cn.lgs.orbisops.domain.skill.model.SkillMethodMemory.Ref;
import java.util.*;
import java.util.regex.Pattern;

/** Group-memory budget: lexical 10 + vector 10, RRF 60, at most 5. */
public final class SkillExperienceRecallPolicy {
    public List<Ref> fuse(List<Ref> lexical,List<Ref> vector) {
        var scores=new HashMap<String,Double>();var refs=new HashMap<String,Ref>();
        for(var lane:List.of(lexical,vector)) {
            var seen=new HashSet<String>();int rank=0;
            for(var ref:lane.stream().limit(10).toList()) {
                rank++;if(!seen.add(ref.groupId())) continue;
                refs.put(ref.groupId(),ref);scores.merge(ref.groupId(),1D/(60+rank),Double::sum);
            }
        }
        return refs.values().stream().sorted(Comparator.<Ref>comparingDouble(r->-scores.get(r.groupId())).thenComparing(Ref::groupId)).limit(5).toList();
    }
    public List<String> terms(String text) {
        var terms=new LinkedHashSet<String>();
        var words=Pattern.compile("[a-z0-9_]{2,48}").matcher(text.toLowerCase(Locale.ROOT));
        while(words.find() && terms.size()<256) terms.add(words.group());
        var chinese=Pattern.compile("[\\p{IsHan}]+").matcher(text);
        while(chinese.find() && terms.size()<512) {
            var value=chinese.group();for(int i=0;i+1<value.length() && terms.size()<512;i++) terms.add(value.substring(i,i+2));
        }
        return List.copyOf(terms);
    }
}
