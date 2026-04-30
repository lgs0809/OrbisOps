package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class SkillRouteProjectionIdentity {
    private SkillRouteProjectionIdentity() { }
    public static String key(SkillRuntimeCandidate c,String modelIdentity) {
        return CanonicalObjectHasher.sha256(Map.of("scope",c.scope(),"project",c.projectId(),"skill",c.skillId(),
                "version",c.version(),"skillHash",c.skillHash(),"packageHash",c.packageHash(),
                "routingHash",CanonicalObjectHasher.sha256(c.routingProfile()),"model",modelIdentity,"text",document(c)));
    }
    /** UTF-8 bytes bound token count conservatively, including multi-byte Chinese characters. */
    public static String document(SkillRuntimeCandidate c) { return bounded(c.retrievalDescriptor(),300); }
    /** Exclusions are kept in a separate quota so long positive metadata cannot hide them. */
    public static String rerankDocument(SkillRuntimeCandidate c) {
        return "name: " + bounded(c.name(),24)
                + "\nsummary: " + bounded(c.routingProfile().searchDescription(),64)
                + "\nuse: " + bounded(String.join("; ",c.routingProfile().useCases()),90)
                + "\nexclude: " + bounded(String.join("; ",c.routingProfile().exclusions()),90);
    }
    public static String bounded(String text,int maximumBytes) {
        if(text.getBytes(StandardCharsets.UTF_8).length<=maximumBytes) return text;
        var out=new StringBuilder();int bytes=0;
        for(int point:text.codePoints().toArray()) {
            String part=new String(Character.toChars(point));int n=part.getBytes(StandardCharsets.UTF_8).length;
            if(bytes+n>maximumBytes) break;out.append(part);bytes+=n;
        }
        return out.toString();
    }
}
