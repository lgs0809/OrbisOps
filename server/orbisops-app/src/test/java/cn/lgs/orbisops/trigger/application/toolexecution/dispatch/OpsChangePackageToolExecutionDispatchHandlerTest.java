package cn.lgs.orbisops.trigger.application.toolexecution.dispatch;

import cn.lgs.orbisops.application.changepackage.ChangePackageCommands;
import cn.lgs.orbisops.application.changepackage.ChangePackageQueryService;
import cn.lgs.orbisops.application.changepackage.PrepareChangePackageUseCase;
import cn.lgs.orbisops.application.changepackage.ReviewChangePackageUseCase;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionRequest;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionScope;
import cn.lgs.orbisops.domain.toolexecution.model.ToolExecutionTarget;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChangePackageToolExecutionDispatchHandlerTest {

    @Test
    void shouldDispatchReviewThroughTypedUseCase() {
        ReviewChangePackageUseCase review = mock(ReviewChangePackageUseCase.class);
        when(review.submitReview(any(ChangePackageCommands.SubmitReview.class)))
                .thenReturn(Map.of("status", "REVIEWING"));
        OpsChangePackageToolExecutionDispatchHandler handler = handler(null, review, null);

        Object result = handler.dispatch(target("change_package_submit_review"), request(Map.of("packageId", "cp-1")));

        assertEquals("REVIEWING", ((Map<?, ?>) result).get("status"));
        verify(review).submitReview(any(ChangePackageCommands.SubmitReview.class));
    }

    @Test
    void approveRequiresExactVersionAndHashAndDelegatesTypedCommand() {
        ReviewChangePackageUseCase review = mock(ReviewChangePackageUseCase.class);
        when(review.approve(any(ChangePackageCommands.Approve.class)))
                .thenReturn(Map.of("status", "APPROVED"));
        OpsChangePackageToolExecutionDispatchHandler handler = handler(null, review, null);

        Object result = handler.dispatch(target("change_package_approve"), request(Map.of(
                "packageId", "cp-1",
                "version", 3,
                "packageHash", "hash-3")));

        assertEquals("APPROVED", ((Map<?, ?>) result).get("status"));
        ArgumentCaptor<ChangePackageCommands.Approve> captor =
                ArgumentCaptor.forClass(ChangePackageCommands.Approve.class);
        verify(review).approve(captor.capture());
        assertEquals("cp-1", captor.getValue().packageId());
        assertEquals(3, captor.getValue().version());
        assertEquals("hash-3", captor.getValue().packageHash());
        assertEquals("alice", captor.getValue().actor());
    }

    @Test
    void approveFailsClosedWithoutExactVersionOrHash() {
        OpsChangePackageToolExecutionDispatchHandler handler = handler(null, mock(ReviewChangePackageUseCase.class), null);

        assertThrows(IllegalArgumentException.class, () ->
                handler.dispatch(target("change_package_approve"), request(Map.of(
                        "packageId", "cp-1",
                        "packageHash", "hash-1"))));
        assertThrows(IllegalArgumentException.class, () ->
                handler.dispatch(target("change_package_approve"), request(Map.of(
                        "packageId", "cp-1",
                        "version", 1))));
    }

    @Test
    void queryToolMustFailClosedWhenQueryServiceMissing() {
        OpsChangePackageToolExecutionDispatchHandler handler = handler(null, null, null);
        assertThrows(IllegalStateException.class, () ->
                handler.dispatch(target("change_package_detail"), request(Map.of("packageId", "cp-1"))));
    }

    private OpsChangePackageToolExecutionDispatchHandler handler(
            PrepareChangePackageUseCase prepare,
            ReviewChangePackageUseCase review,
            ChangePackageQueryService query) {
        return new OpsChangePackageToolExecutionDispatchHandler(
                provider(prepare), provider(review), provider(query));
    }

    private ToolExecutionTarget target(String toolName) {
        return new ToolExecutionTarget(
                "change_package", toolName, "CHANGE_PACKAGE", "HIGH",
                false, false, true, true, true);
    }

    private ToolExecutionRequest request(Map<String, Object> arguments) {
        return new ToolExecutionRequest(
                "project-1", "alice", "alice", "change_package", "change_package_submit_review",
                ToolExecutionScope.PRE_APPROVAL_WORKFLOW, arguments,
                "session-1", "run-1", Map.of(), Map.of());
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
