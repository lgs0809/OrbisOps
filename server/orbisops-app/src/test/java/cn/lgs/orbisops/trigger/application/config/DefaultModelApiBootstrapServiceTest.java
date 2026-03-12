package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.DefaultModelApiBootstrapPlan;
import cn.lgs.orbisops.application.config.DefaultModelApiBootstrapResult;
import cn.lgs.orbisops.application.config.DefaultModelApiBootstrapUseCase;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultModelApiBootstrapServiceTest {

    @Test
    void startupFacadeDelegatesTypedBootstrapPlan() {
        DefaultModelApiBootstrapUseCase useCase = mock(DefaultModelApiBootstrapUseCase.class);
        DefaultModelApiBootstrapPlan plan = new DefaultModelApiBootstrapPlan(
                true,
                "1001",
                "https://proxy.example.com/",
                "configured-value");
        when(useCase.bootstrap(plan)).thenReturn(new DefaultModelApiBootstrapResult(
                DefaultModelApiBootstrapResult.Action.CREATED,
                "1001",
                "https://proxy.example.com"));
        DefaultModelApiBootstrapService service = new DefaultModelApiBootstrapService(useCase, plan);

        service.bootstrap();

        verify(useCase).bootstrap(plan);
    }

    @Test
    void skippedBootstrapRemainsAValidStartupOutcome() {
        DefaultModelApiBootstrapUseCase useCase = mock(DefaultModelApiBootstrapUseCase.class);
        DefaultModelApiBootstrapPlan plan = new DefaultModelApiBootstrapPlan(
                false,
                "1001",
                "",
                "");
        when(useCase.bootstrap(plan)).thenReturn(new DefaultModelApiBootstrapResult(
                DefaultModelApiBootstrapResult.Action.SKIPPED,
                "1001",
                ""));
        DefaultModelApiBootstrapService service = new DefaultModelApiBootstrapService(useCase, plan);

        service.bootstrap();

        verify(useCase).bootstrap(plan);
    }
}
