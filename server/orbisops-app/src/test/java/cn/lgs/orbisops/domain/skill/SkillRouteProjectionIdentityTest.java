package cn.lgs.orbisops.domain.skill;

import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.skill.service.SkillRouteProjectionIdentity;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SkillRouteProjectionIdentityTest {
    @Test void embeddingExcludesNegativeMetadataButRerankerKeepsItWithLongPositiveText() {
        var c=skill("trace logs",List.of("delete production"));
        assertFalse(SkillRouteProjectionIdentity.document(c).contains("delete production"));
        assertTrue(SkillRouteProjectionIdentity.rerankDocument(c).contains("delete production"));
        var longText=skill("安全调查😀".repeat(500),List.of("delete production"));
        assertTrue(SkillRouteProjectionIdentity.rerankDocument(longText).contains("delete production"));
        for(var text:List.of(SkillRouteProjectionIdentity.document(longText),SkillRouteProjectionIdentity.rerankDocument(longText))) {
            assertTrue(text.getBytes(StandardCharsets.UTF_8).length<=300);
            assertFalse(text.contains("\uFFFD"));
        }
    }
    @Test void changedNegativeBoundaryInvalidatesGenerationWithoutEmbeddingProhibitedActions() {
        var a=skill("trace logs",List.of("delete production"));
        var b=skill("trace logs",List.of("restart production"));
        assertEquals(SkillRouteProjectionIdentity.document(a),SkillRouteProjectionIdentity.document(b));
        assertNotEquals(SkillRouteProjectionIdentity.key(a,"fixed-model"),SkillRouteProjectionIdentity.key(b,"fixed-model"));
    }
    private SkillRuntimeCandidate skill(String text,List<String> negative) {
        return new SkillRuntimeCandidate("s","p","PROJECT","inspect",text,1,"hash","package","manifest",Map.of(),"SKILL.md","ACTIVE","AUTO",20,
                new SkillRoutingProfile("GENERAL","",text,List.of(text),negative,List.of("logs")));
    }
}
