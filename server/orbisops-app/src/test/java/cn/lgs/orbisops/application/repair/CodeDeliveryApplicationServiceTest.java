package cn.lgs.orbisops.application.repair;

import cn.lgs.orbisops.domain.repair.adapter.repository.ICodeDeliveryRepository;
import cn.lgs.orbisops.domain.repair.model.CodeDelivery;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryBranch;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryCandidate;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryCiSnapshot;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryMode;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryPullRequest;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodeDeliveryApplicationServiceTest {

    private static final String BASE = "0123456789abcdef0123456789abcdef01234567";
    private static final String REPAIR = "89abcdef0123456789abcdef0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-07-25T08:00:00Z");
    private static final Instant LATER = Instant.parse("2026-07-25T09:00:00Z");

    @Test
    void publishesLocalBranchPersistsAndAuditsTypedDelivery() {
        Fixture fixture = fixture();
        when(fixture.provider.configured()).thenReturn(false);
        when(fixture.git.publishBranch(any(), eq(Path.of("/tmp/repair-1")), any(), eq("fix"), eq(false)))
                .thenReturn(new CodeDeliveryBranch("ops-repair/service-1/repair-1", REPAIR));

        CodeDelivery result = fixture.application.publish(
                "repair-1", new CodeDeliveryCandidate(CodeDeliveryMode.LOCAL_BRANCH, "", ""), "alice");

        assertEquals(CodeDeliveryMode.LOCAL_BRANCH, result.mode());
        assertEquals("LOCAL_VERIFIED", result.ciStatus());
        assertEquals("delivery-1", result.deliveryId());
        verify(fixture.deliveries).save(result);
        ArgumentCaptor<RepairAuditEvent> event = ArgumentCaptor.forClass(RepairAuditEvent.class);
        verify(fixture.audit).record(event.capture());
        assertEquals("repair-1", event.getValue().targetId());
        verify(fixture.provider, never()).openPullRequest(any(), any(), any(), any());
    }

    @Test
    void publishesGithubBranchThenCreatesPullRequest() {
        Fixture fixture = fixture();
        when(fixture.provider.configured()).thenReturn(true);
        when(fixture.git.publishBranch(any(), any(), any(), eq("review fix"), eq(true)))
                .thenReturn(new CodeDeliveryBranch("ops-repair/service-1/repair-1", REPAIR));
        when(fixture.provider.openPullRequest(
                "ops-repair/service-1/repair-1", "review fix", "main", "repair-1"))
                .thenReturn(new CodeDeliveryPullRequest("https://github.test/pr/1"));

        CodeDelivery result = fixture.application.publish(
                "repair-1", new CodeDeliveryCandidate(CodeDeliveryMode.GITHUB_PR, "review fix", ""), "alice");

        assertEquals("QUEUED", result.ciStatus());
        assertEquals("https://github.test/pr/1", result.pullRequestUrl());
        verify(fixture.provider).openPullRequest(
                "ops-repair/service-1/repair-1", "review fix", "main", "repair-1");
    }

    @Test
    void returnsExistingWorkspaceModeDeliveryWithoutDuplicateGitSideEffect() {
        Fixture fixture = fixture();
        CodeDelivery existing = delivery(CodeDeliveryMode.LOCAL_BRANCH, "LOCAL_VERIFIED", "now");
        when(fixture.provider.configured()).thenReturn(false);
        when(fixture.deliveries.findByWorkspaceAndMode("repair-1", CodeDeliveryMode.LOCAL_BRANCH))
                .thenReturn(Optional.of(existing));

        assertSame(existing, fixture.application.publish(
                "repair-1", new CodeDeliveryCandidate(CodeDeliveryMode.LOCAL_BRANCH, "", ""), "alice"));

        verify(fixture.git, never()).publishBranch(any(), any(), any(), any(), anyBoolean());
        verify(fixture.deliveries, never()).save(any());
        ArgumentCaptor<RepairAuditEvent> event = ArgumentCaptor.forClass(RepairAuditEvent.class);
        verify(fixture.audit).record(event.capture());
        assertEquals("repair-1", event.getValue().targetId());
    }

    @Test
    void refreshesGithubCiAndAuditsInTransaction() {
        Fixture fixture = fixture();
        CodeDelivery before = delivery(CodeDeliveryMode.GITHUB_PR, "QUEUED", "now");
        when(fixture.deliveries.find("delivery-1")).thenReturn(Optional.of(before));
        when(fixture.provider.configured()).thenReturn(true);
        when(fixture.provider.latestCi(before.branchName())).thenReturn(Optional.of(
                new CodeDeliveryCiSnapshot("success", "https://github.test/run/1")));
        fixture.clock.set(LATER);

        CodeDelivery result = fixture.application.refreshCi("delivery-1", "alice");

        assertEquals("SUCCESS", result.ciStatus());
        assertEquals("2026-07-25 09:00:00", result.updatedAt());
        verify(fixture.deliveries).save(result);
        ArgumentCaptor<RepairAuditEvent> event = ArgumentCaptor.forClass(RepairAuditEvent.class);
        verify(fixture.audit).record(event.capture());
        assertEquals("refresh-ci", event.getValue().action());
    }

    @Test
    void rejectsUnverifiedWorkspaceUnconfiguredGithubAndMissingActor() {
        Fixture fixture = fixture();
        when(fixture.workspaces.get("repair-1")).thenReturn(workspace(RepairWorkspaceStatus.COMMITTED));
        assertThrows(IllegalStateException.class, () -> fixture.application.publish(
                "repair-1", new CodeDeliveryCandidate(CodeDeliveryMode.LOCAL_BRANCH, "", ""), "alice"));

        when(fixture.workspaces.get("repair-1")).thenReturn(workspace(RepairWorkspaceStatus.VERIFIED));
        when(fixture.provider.configured()).thenReturn(false);
        assertEquals("GitHub PR Provider 未配置",
                assertThrows(IllegalStateException.class, () -> fixture.application.publish(
                        "repair-1", new CodeDeliveryCandidate(CodeDeliveryMode.GITHUB_PR, "", "main"), "alice"))
                        .getMessage());
        assertEquals("REPAIR_ACTOR_REQUIRED",
                assertThrows(IllegalArgumentException.class,
                        () -> fixture.application.refreshCi("delivery-1", " ")).getMessage());
    }

    private Fixture fixture() {
        RepairWorkspaceApplicationService workspaces = mock(RepairWorkspaceApplicationService.class);
        ICodeDeliveryRepository deliveries = mock(ICodeDeliveryRepository.class);
        CodeDeliveryGitPort git = mock(CodeDeliveryGitPort.class);
        CodeDeliveryProviderPort provider = mock(CodeDeliveryProviderPort.class);
        MutableClock clock = new MutableClock(NOW);
        RepairAuditPort audit = mock(RepairAuditPort.class);
        when(workspaces.get("repair-1")).thenReturn(workspace(RepairWorkspaceStatus.VERIFIED));
        when(workspaces.worktreePath("repair-1")).thenReturn(Path.of("/tmp/repair-1"));
        when(deliveries.findByWorkspaceAndMode(any(), any())).thenReturn(Optional.empty());
        when(deliveries.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        RepairTransactionPort transaction = new RepairTransactionPort() {
            @Override
            public <T> T required(java.util.function.Supplier<T> action) {
                return action.get();
            }
        };
        return new Fixture(
                workspaces, deliveries, git, provider, clock, audit,
                new CodeDeliveryApplicationService(
                        workspaces,
                        deliveries,
                        git,
                        provider,
                        () -> "delivery-1",
                        clock,
                        audit,
                        transaction));
    }

    private RepairWorkspace workspace(RepairWorkspaceStatus status) {
        return new RepairWorkspace(
                "repair-1", "project-1", "service-1", "repo-1", "prod", BASE, REPAIR,
                status, "fix", "patch", List.of("module/src/App.java"), "MAVEN_VERIFY",
                "mvn test", 0, "ok", "", "", 0L, "alice", "now", "now");
    }

    private CodeDelivery delivery(CodeDeliveryMode mode, String ciStatus, String updatedAt) {
        return new CodeDelivery(
                "delivery-1", "repair-1", "project-1", "service-1", mode,
                "ops-repair/service-1/repair-1", REPAIR,
                mode == CodeDeliveryMode.GITHUB_PR ? "https://github.test/pr/1" : "",
                ciStatus, "", "alice", "now", updatedAt);
    }

    private record Fixture(
            RepairWorkspaceApplicationService workspaces,
            ICodeDeliveryRepository deliveries,
            CodeDeliveryGitPort git,
            CodeDeliveryProviderPort provider,
            MutableClock clock,
            RepairAuditPort audit,
            CodeDeliveryApplicationService application) {
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
