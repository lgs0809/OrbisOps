package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillEvolutionProposalResolverTest {
    final SkillEvolutionProposalPort proposals=mock(SkillEvolutionProposalPort.class);
    final SkillEvolutionSimilarityPort similarity=mock(SkillEvolutionSimilarityPort.class);
    final SkillEvolutionJobSnapshot claim=mock(SkillEvolutionJobSnapshot.class);
    final SkillEvolutionProposalResolver resolver=new SkillEvolutionProposalResolver(proposals,similarity);
    final SkillEvolutionProposalSnapshot plan=new SkillEvolutionProposalSnapshot("plan","hash","{}","");
    @Test void transportRetryReusesImmutableInputAndDoesNotReadNewCatalog() {
        when(proposals.existing(claim,"source")).thenReturn(Optional.of(plan));
        assertEquals(plan,resolver.resolve("p",claim,"source","cluster",Map.of()));
        verifyNoInteractions(similarity);verify(proposals,never()).supersedeChangedBaseline(any(),any());
    }
    @Test void staleUnpublishedInputIsArchivedBeforeReadingFreshRelatedSkills() {
        when(proposals.existing(claim,"source")).thenThrow(new IllegalStateException("SKILL_EVOLUTION_RELATED_SKILL_CHANGED"));
        when(proposals.supersedeChangedBaseline(claim,"source")).thenReturn(true);
        when(similarity.relatedSkills("p",Map.of("goal","test"))).thenReturn(new SkillEvolutionRelatedSkills("p",List.of()));
        when(proposals.freeze(eq(claim),eq("source"),eq("cluster"),anyMap())).thenReturn(plan);
        assertEquals(plan,resolver.resolve("p",claim,"source","cluster",Map.of("goal","test")));
        var order=inOrder(proposals,similarity);
        order.verify(proposals).existing(claim,"source");
        order.verify(proposals).supersedeChangedBaseline(claim,"source");
        order.verify(similarity).relatedSkills("p",Map.of("goal","test"));
        order.verify(proposals).freeze(claim,"source","cluster",Map.of("goal","test","relatedSkills",List.of()));
    }
    @Test void candidateLinkedProposalAndRevokedSourcesCannotBeReauthored() {
        when(proposals.existing(claim,"source")).thenThrow(new IllegalStateException("SKILL_EVOLUTION_RELATED_SKILL_CHANGED"));
        assertThrows(IllegalStateException.class,()->resolver.resolve("p",claim,"source","cluster",Map.of()));
        verifyNoInteractions(similarity);
        reset(proposals);
        when(proposals.existing(claim,"source")).thenThrow(new IllegalStateException("SKILL_EVOLUTION_SOURCE_REVOKED"));
        assertThrows(IllegalStateException.class,()->resolver.resolve("p",claim,"source","cluster",Map.of()));
        verify(proposals,never()).supersedeChangedBaseline(any(),any());
        verifyNoInteractions(similarity);
    }
}
