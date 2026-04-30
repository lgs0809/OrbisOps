package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SkillRuntimeCatalogAccessTest {
    @Test void globalGrantCannotOverrideDisabledLocalIdentity() {
        var catalog=mock(SkillCatalogPort.class);when(catalog.configuredGlobalSkillIds("p")).thenReturn(List.of("s"));
        when(catalog.listRuntimeGlobalEntries()).thenReturn(List.of(skill("GLOBAL","","ACTIVE")));
        assertEquals(1,new SkillRuntimeCatalogAccess(catalog).active("p").size());
        when(catalog.listRuntimeProjectEntries("p")).thenReturn(List.of(skill("PROJECT","p","DISABLED")));
        assertTrue(new SkillRuntimeCatalogAccess(catalog).active("p").isEmpty());
        assertThrows(SecurityException.class,()->new SkillRuntimeCatalogAccess(catalog).require("p","s","GLOBAL"));
    }
    @Test void runtimeBodyAndResourcesRecheckRevocationWhileArchiveQueriesRemainAvailable() {
        var catalog=mock(SkillCatalogPort.class);var packages=mock(SkillPackageQueryService.class);
        var query=new SkillCatalogQueryService(catalog,packages);
        when(catalog.listRuntimeProjectEntries("p")).thenReturn(List.of(skill("PROJECT","p","ACTIVE")));
        when(packages.getVersion(anyString(),anyString(),anyInt(),anyString(),anyString(),anyString())).thenAnswer(call->{
            when(catalog.listRuntimeProjectEntries("p")).thenReturn(List.of(skill("PROJECT","p","DISABLED")));
            return Map.of("content","VERSION_BOUND_BODY");
        });
        assertThrows(SecurityException.class,()->query.getRuntimeSkillVersion("p","s",1,"h","pkg","PROJECT"));
        assertThrows(SecurityException.class,()->query.getRuntimeSkillArtifact("p","s",1,"h","pkg","PROJECT","resource.json"));
        verify(packages,never()).getArtifact(anyString(),anyString(),anyInt(),anyString(),anyString(),anyString(),anyString());
        assertEquals("VERSION_BOUND_BODY",query.getSkillVersion("p","s",1,"h","pkg","PROJECT").get("content"));
    }
    @Test void activeOldVersionIsPassedUnmodifiedToExactPackageReader() {
        var catalog=mock(SkillCatalogPort.class);var packages=mock(SkillPackageQueryService.class);
        when(catalog.listRuntimeProjectEntries("p")).thenReturn(List.of(skill("PROJECT","p","ACTIVE")));
        when(packages.getVersion("p","s",1,"old-hash","old-package","PROJECT")).thenReturn(Map.of("content","OLD_EXACT_BODY"));
        assertEquals("OLD_EXACT_BODY",new SkillCatalogQueryService(catalog,packages).getRuntimeSkillVersion("p","s",1,"old-hash","old-package","PROJECT").get("content"));
    }
    private SkillCatalogSnapshot skill(String scope,String project,String status) {
        return new SkillCatalogSnapshot(new SkillRuntimeCandidate("s",project,scope,"s","inspect logs",2,"h2","pkg2","m2",Map.of(),"SKILL.md",status,"AUTO",10,
                new SkillRoutingProfile("GENERAL","","inspect logs",List.of("inspect logs"),List.of("delete production"),List.of("logs"))),Map.of("skillId","s"));
    }
}
