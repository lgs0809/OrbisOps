package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.alert.AlertAggregationApplicationService;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Uses independent service instances against real InnoDB locks and transactions. */
@Testcontainers(disabledWithoutDocker = true)
class AlertAggregationMySqlTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("alert_aggregation_test").withUsername("agent").withPassword("agent");
    private JdbcTemplate jdbc;
    private DefaultListableBeanFactory beans;

    @BeforeEach void setup() {
        var dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        beans = new DefaultListableBeanFactory();
        beans.registerSingleton("mysqlJdbcTemplate", jdbc);
        beans.registerSingleton("mysqlTransactionManager", new DataSourceTransactionManager(dataSource));
        var initializer = new JdbcAlertAggregationSchemaInitializer(beans.getBeanProvider(JdbcTemplate.class));
        ReflectionTestUtils.setField(initializer, "autoInit", true);
        initializer.initialize();
        jdbc.execute("CREATE TABLE IF NOT EXISTS retry_probe (test_id VARCHAR(80), attempt INT)");
    }

    @Test void concurrentFirstAndDuplicateNotificationsPreserveEveryOccurrenceAndOneFirstDecision() throws Exception {
        String fingerprint = "concurrent-" + UUID.randomUUID();
        List<AlertAggregateEventType> events = new ArrayList<>();
        for (int batch = 0; batch < 5; batch++) {
            var pool = Executors.newFixedThreadPool(8);
            var ready = new CountDownLatch(8);
            var start = new CountDownLatch(1);
            try {
                List<java.util.concurrent.Future<AlertAggregateEventType>> futures = new ArrayList<>();
                for (int i = 0; i < 8; i++) futures.add(pool.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return service().record("p", 1L, fingerprint, "firing", "warning", "orders",
                            Map.of("startsAt", "2026-09-09T00:00:00Z"), 120, 900).eventType();
                }));
                assertTrue(ready.await(10, TimeUnit.SECONDS));
                start.countDown();
                for (var future : futures) events.add(future.get(15, TimeUnit.SECONDS));
            } finally {
                start.countDown();
                pool.shutdownNow();
            }
        }
        assertEquals(1, events.stream().filter(event -> event == AlertAggregateEventType.FIRST).count());
        assertEquals(39, events.stream().filter(event -> event == AlertAggregateEventType.DUPLICATE).count());
        var row = jdbc.queryForMap("SELECT occurrence_count,pending_summary_count FROM ai_ops_alert_aggregate WHERE fingerprint=?", fingerprint);
        assertEquals(40, ((Number) row.get("occurrence_count")).intValue());
        assertEquals(39, ((Number) row.get("pending_summary_count")).intValue());
        assertEquals(AlertAggregateEventType.RECOVERY, service().record("p", 1L, fingerprint,
                "resolved", "warning", "orders", Map.of(), 120, 900).eventType());
        assertEquals("RESOLVED", jdbc.queryForObject("SELECT current_state FROM ai_ops_alert_aggregate WHERE fingerprint=?", String.class, fingerprint));
    }

    @Test void retriesOnlyRolledBackLockFailuresAndKeepsTheThreeAttemptBound() {
        String id = UUID.randomUUID().toString();
        var attempts = new AtomicInteger();
        int value = transactions().required(() -> {
            int attempt = attempts.incrementAndGet();
            jdbc.update("INSERT INTO retry_probe VALUES (?,?)", id, attempt);
            if (attempt < 3) throw new CannotAcquireLockException("injected rolled-back lock failure");
            return attempt;
        });
        assertEquals(3, value);
        assertEquals(List.of(3), jdbc.queryForList("SELECT attempt FROM retry_probe WHERE test_id=?", Integer.class, id));
        attempts.set(0);
        assertThrows(CannotAcquireLockException.class, () -> transactions().required(() -> {
            attempts.incrementAndGet();
            throw new CannotAcquireLockException("persistent lock failure");
        }));
        assertEquals(3, attempts.get());
    }

    @Test void joinedTransactionsAndNonLockErrorsAreNeverRetried() {
        var attempts = new AtomicInteger();
        var outer = new TransactionTemplate(beans.getBean(PlatformTransactionManager.class));
        assertThrows(CannotAcquireLockException.class, () -> outer.execute(status -> transactions().required(() -> {
            attempts.incrementAndGet();
            throw new CannotAcquireLockException("caller owns rollback");
        })));
        assertEquals(1, attempts.get());
        attempts.set(0);
        assertThrows(IllegalStateException.class, () -> transactions().required(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("integrity failure");
        }));
        assertEquals(1, attempts.get());
    }

    private SpringAlertAggregationTransactionAdapter transactions() {
        return new SpringAlertAggregationTransactionAdapter(beans.getBeanProvider(PlatformTransactionManager.class));
    }

    private AlertAggregationApplicationService service() {
        return new AlertAggregationApplicationService(new JdbcAlertAggregationRepository(beans.getBeanProvider(JdbcTemplate.class)),
                () -> UUID.randomUUID().toString(), transactions());
    }
}
