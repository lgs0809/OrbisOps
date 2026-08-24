package cn.lgs.orbisops.application.skill;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillMaintenancePackageTest {
    Map<String,Object> file(String path,String role,String content) {return Map.of("path",path,"role",role,"encoding","UTF8","content",content,"contentHash","frozen");}
    Map<String,Object> change(String path,String content) {return Map.of("changes",List.of(Map.of("path",path,"content",content,"reason","equivalent consolidation")));}
    @Test void countsReferencedTextTransitivelyOnceIncludingCyclesButNotUnusedResources() {
        var p=new SkillMaintenancePackage("Read references/a.md",List.of(file("references/a.md","REFERENCE","Read references/b.md"),
            file("references/b.md","REFERENCE","See SKILL.md"),file("references/unused.md","REFERENCE","unused ".repeat(1000))));
        assertEquals("Read references/a.md".length()+"Read references/b.md".length()+"See SKILL.md".length(),p.estimatedReadTokens(String::length));
        assertEquals(Set.of("SKILL.md","references/a.md","references/b.md"),p.editablePaths());
    }
    @Test void semanticRewriteCanConsolidateDifferentWordingAndPreservesOpaqueResources() {
        var original="Read references/check.md. Never change production. Production changes are forbidden.";
        var p=new SkillMaintenancePackage(original,List.of(file("references/check.md","REFERENCE","Check the current evidence."),file("scripts/check.sh","SCRIPT","echo preserved")));
        var changes=p.validate(change("SKILL.md","Read references/check.md. Never change production."));
        assertEquals(1,changes.size());assertFalse(p.mutation(changes).containsKey("artifacts"));
        assertEquals("echo preserved",p.changedArtifacts(changes).get(1).get("content"));
    }
    @Test void methodJsonCanConsolidateTextButNotRemoveKeysOrChangeNumbersOrTypes() {
        var old="{\"steps\":[\"Read current evidence before judging.\",\"Only judge with current evidence.\"],\"limit\":100,\"enabled\":false}";
        var p=new SkillMaintenancePackage("Read resources/method.json",List.of(file("resources/method.json","RESOURCE",old)));
        var valid=p.validate(change("resources/method.json","{\"steps\":[\"Judge only with current evidence.\"],\"limit\":100,\"enabled\":false}"));
        assertEquals(1,valid.size());assertTrue(p.mutation(valid).containsKey("artifacts"));
        assertFalse(p.changedArtifacts(valid).get(0).containsKey("contentHash"));
        for(String invalid:List.of("{\"steps\":[],\"limit\":100,\"enabled\":false}","{\"steps\":[\"Read\"],\"limit\":10,\"enabled\":false}",
                "{\"steps\":[\"Read\"],\"limit\":100,\"enabled\":true}","{\"limit\":100,\"enabled\":false}"))
            assertThrows(IllegalArgumentException.class,()->p.validate(change("resources/method.json",invalid)));
    }
    @Test void rejectsNewFilesScriptsMissingReferencesAndBiggerPackages() {
        var p=new SkillMaintenancePackage("Read resources/method.json twice. Read resources/method.json again.",
                List.of(file("resources/method.json","RESOURCE","{\"step\":\"Read evidence.\"}"),file("scripts/check.sh","SCRIPT","echo 100")));
        assertThrows(IllegalArgumentException.class,()->p.validate(change("resources/new.md","new")));
        assertThrows(IllegalArgumentException.class,()->p.validate(change("scripts/check.sh","rm data")));
        assertThrows(IllegalArgumentException.class,()->p.validate(change("SKILL.md","Read evidence.")));
        assertThrows(IllegalArgumentException.class,()->p.validate(change("SKILL.md","Read resources/method.json ".repeat(10))));
    }
    @Test void preservesFrontMatterFencedCommandsAndNumericThresholds() {
        var original="---\nname: method\n---\n\n```sh\necho 100\n```\n\nThreshold 100 requests. Require 100 samples.";
        var p=new SkillMaintenancePackage(original,List.of());
        assertThrows(IllegalArgumentException.class,()->p.validate(change("SKILL.md",original.replace("name: method","name: x"))));
        assertThrows(IllegalArgumentException.class,()->p.validate(change("SKILL.md",original.replace("echo 100","rm x"))));
        assertThrows(IllegalArgumentException.class,()->p.validate(change("SKILL.md",original.replace("100","10"))));
        assertEquals(1,p.validate(change("SKILL.md",original.replace("Threshold 100 requests. Require 100 samples.","Require 100 requests."))).size());
    }
}
