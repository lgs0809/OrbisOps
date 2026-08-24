package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.changepackage.LandingVerificationRecordPort;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JdbcLandingVerificationRecordAdapterTest {
    @Test
    void repositoryStartsWithClassBasedExceptionTranslationAndRequiresDurableInsert() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1, 0);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean("mysqlJdbcTemplate", JdbcTemplate.class, () -> jdbc);
            context.registerBean(PersistenceExceptionTranslationPostProcessor.class, () -> {
                var processor = new PersistenceExceptionTranslationPostProcessor();
                processor.setProxyTargetClass(true);
                return processor;
            });
            context.register(JdbcLandingVerificationRecordAdapter.class);
            context.refresh();
            var records = context.getBean(LandingVerificationRecordPort.class);
            var proof = Map.<String, Object>of("passed", true, "readResultId", "tool-result-test");
            records.record("lv-1", "run-1", "project-1", "package-1", 1L, "hash-1", true, proof);
            verify(jdbc).update(contains("INSERT INTO ai_ops_landing_verification"),
                    eq("lv-1"), eq("run-1"), eq("project-1"), eq("package-1"),
                    eq(1L), eq("hash-1"), eq(true), contains("tool-result-test"));
            assertThrows(IllegalStateException.class, () -> records.record(
                    "lv-2", "run-1", "project-1", "package-1", 1L, "hash-1", true, proof));
        }
    }
}
