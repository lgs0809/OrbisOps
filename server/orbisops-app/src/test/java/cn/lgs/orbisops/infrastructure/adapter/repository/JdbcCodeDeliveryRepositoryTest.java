package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.repair.model.CodeDelivery;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryMode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JdbcCodeDeliveryRepositoryTest {

    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

    @Test
    void memoryViewSupportsSaveFindModeAndSortedList() {
        JdbcCodeDeliveryRepository repository =
                new JdbcCodeDeliveryRepository((JdbcTemplate) null, false);
        CodeDelivery older = delivery("delivery-1", CodeDeliveryMode.LOCAL_BRANCH, "2026-07-23 10:00:00");
        CodeDelivery newer = delivery("delivery-2", CodeDeliveryMode.GITHUB_PR, "2026-07-23 11:00:00");

        repository.save(older);
        repository.save(newer);

        assertEquals(older, repository.find("delivery-1").orElseThrow());
        assertEquals(newer, repository.findByWorkspaceAndMode(
                "repair-1", CodeDeliveryMode.GITHUB_PR).orElseThrow());
        assertEquals(List.of("delivery-2", "delivery-1"),
                repository.list("repair-1").stream().map(CodeDelivery::deliveryId).toList());
        assertTrue(repository.list("other").isEmpty());
    }

    @Test
    void jdbcOwnsTypedUpsert() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doReturn(1).when(jdbc).update(anyString(), any(Object[].class));
        JdbcCodeDeliveryRepository repository = new JdbcCodeDeliveryRepository(jdbc, true);
        CodeDelivery delivery = delivery("delivery-1", CodeDeliveryMode.GITHUB_PR, "now");

        repository.save(delivery);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());
        assertTrue(sql.getValue().contains("INSERT INTO ai_ops_code_delivery"));
        assertTrue(sql.getValue().contains("ON DUPLICATE KEY UPDATE"));
        assertEquals("GITHUB_PR", args.getValue()[4]);
        assertEquals(COMMIT, args.getValue()[6]);
    }

    private CodeDelivery delivery(String id, CodeDeliveryMode mode, String createdAt) {
        return new CodeDelivery(
                id, "repair-1", "project-1", "service-1", mode,
                "ops-repair/service-1/repair-1", COMMIT,
                mode == CodeDeliveryMode.GITHUB_PR ? "https://github.test/pr/1" : "",
                mode == CodeDeliveryMode.GITHUB_PR ? "QUEUED" : "LOCAL_VERIFIED",
                "", "alice", createdAt, createdAt);
    }
}
