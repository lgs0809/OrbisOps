package cn.lgs.orbisops.trigger.ops;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsMainAgentSubAgentCatalogTest {

    @Test
    void emptyCatalogExposesBuiltinSourcesAndCapabilities() {
        OpsMainAgentSubAgentCatalog catalog = new OpsMainAgentSubAgentCatalog();

        assertTrue(catalog.availableSources().containsAll(List.of(
                OpsMainAgentPlanner.SOURCE_RAG,
                OpsMainAgentPlanner.SOURCE_ES,
                OpsMainAgentPlanner.SOURCE_PROM,
                OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL)));
        assertTrue(catalog.capabilityCatalog().contains("rag-knowledge-agent"));
        assertTrue(catalog.capabilityCatalog().contains("mysql-slow-sql-agent"));
    }

    @Test
    void replaceFiltersInvalidAgentsAndKeepsLastDuplicate() {
        OpsSubAgent first = agent("custom", "first-agent", "first capability");
        OpsSubAgent last = agent("custom", "last-agent", "last capability");
        OpsSubAgent blank = agent(" ", "blank-agent", "blank capability");
        OpsMainAgentSubAgentCatalog catalog = new OpsMainAgentSubAgentCatalog();

        catalog.replace(List.of(first, blank, last));

        assertEquals(List.of("custom"), catalog.availableSources().stream().toList());
        assertTrue(catalog.capabilityCatalog().contains("last-agent"));
        assertTrue(catalog.capabilityCatalog().contains("last capability"));
        assertFalse(catalog.capabilityCatalog().contains("first-agent"));
        assertFalse(catalog.capabilityCatalog().contains("blank-agent"));
    }

    @Test
    void nullReplacementClearsDynamicCatalogAndRestoresBuiltinFallback() {
        OpsMainAgentSubAgentCatalog catalog = new OpsMainAgentSubAgentCatalog();
        catalog.replace(List.of(agent("custom", "custom-agent", "custom capability")));

        catalog.replace(null);

        assertFalse(catalog.availableSources().contains("custom"));
        assertTrue(catalog.availableSources().contains(OpsMainAgentPlanner.SOURCE_PROM));
        assertTrue(catalog.capabilityCatalog().contains("prometheus-agent"));
    }

    private OpsSubAgent agent(String source, String agentId, String capability) {
        OpsSubAgent agent = mock(OpsSubAgent.class);
        when(agent.source()).thenReturn(source);
        when(agent.agentId()).thenReturn(agentId);
        when(agent.capability()).thenReturn(capability);
        return agent;
    }
}
