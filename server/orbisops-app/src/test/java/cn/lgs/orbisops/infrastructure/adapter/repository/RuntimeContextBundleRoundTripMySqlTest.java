package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextBundleSnapshot;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class RuntimeContextBundleRoundTripMySqlTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("context_roundtrip").withUsername("fixture").withPassword("fixture");
    JdbcTemplate jdbc; JdbcRuntimeContextBundleRepository repository;
    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()));
        var beans = new DefaultListableBeanFactory(); beans.registerSingleton("jdbc",jdbc);
        repository = new JdbcRuntimeContextBundleRepository(beans.getBeanProvider(JdbcTemplate.class));
        repository.init();
    }
    RuntimeContextBundleSnapshot snapshot(List<Map<String,Object>> refs) {
        var payload = new LinkedHashMap<String,Object>();
        payload.put("layers",Map.of("taskContext",Map.of("explicitConstraints",List.of())));
        payload.put("skillCatalogRefs",refs);
        payload.put("usedSkillVersionRefs",refs);
        return new RuntimeContextBundleSnapshot(null,"bundle-"+UUID.randomUUID(),"frozen-hash","session","run",
                "project-a","agent","actor","memory",List.of(),refs,"skill-hash","tools","runtime",payload,Instant.now());
    }
    @Test void sharedEmptyListsStayListsAfterDatabaseRoundTrip() {
        var original=snapshot(List.of()); repository.save(original);
        var raw=jdbc.queryForObject("SELECT bundle_json FROM ai_ops_runtime_context_bundle WHERE bundle_id=?",String.class,original.bundleId());
        assertFalse(raw.contains("\"$ref\""));
        var restored=repository.find(original.bundleId()).orElseThrow();
        assertEquals(List.of(),restored.compatiblePayload().get("usedSkillVersionRefs"));
        assertEquals(original.bundleHash(),restored.bundleHash());
    }
    @Test void sharedNonEmptyReferencesRetainValuesAndNestedLists() {
        var refs=List.<Map<String,Object>>of(Map.of("skillId","synthetic-skill","version",3,"tags",List.of("readonly")));
        var original=snapshot(refs); repository.save(original);
        var payload=repository.find(original.bundleId()).orElseThrow().compatiblePayload();
        assertEquals(refs,payload.get("usedSkillVersionRefs"));
        assertEquals(refs,payload.get("skillCatalogRefs"));
    }
    @Test void legacyAliasUsesFrozenColumnWithoutChangingStoredEvidence() {
        var original=snapshot(List.of(Map.of("skillId","frozen-skill","version",2))); repository.save(original);
        // Explicit synthetic legacy serialization fixture, not a business status mutation.
        jdbc.update("UPDATE ai_ops_runtime_context_bundle SET bundle_json=JSON_SET(bundle_json,'$.usedSkillVersionRefs',JSON_OBJECT('$ref','$.skillCatalogRefs')) WHERE bundle_id=?",original.bundleId());
        String before=jdbc.queryForObject("SELECT bundle_json FROM ai_ops_runtime_context_bundle WHERE bundle_id=?",String.class,original.bundleId());
        var restored=repository.find(original.bundleId()).orElseThrow();
        assertEquals(original.usedSkillVersionRefs(),restored.compatiblePayload().get("usedSkillVersionRefs"));
        assertEquals(original.bundleHash(),restored.bundleHash());
        assertEquals(before,jdbc.queryForObject("SELECT bundle_json FROM ai_ops_runtime_context_bundle WHERE bundle_id=?",String.class,original.bundleId()));
    }
}
