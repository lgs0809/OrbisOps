package cn.lgs.orbisops.trigger.http;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApprovalReplayHttpStatusTest {
    @Test
    void terminalReplayIsAConflictWhileInfrastructureAndAuthorizationRemainDistinct() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new Probe())
                .setControllerAdvice(new GlobalExceptionHandler(), new GlobalResponseStatusAdvice()).build();
        mvc.perform(get("/approval-replay")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.info").value("WORK_SESSION_NOT_WAITING_APPROVAL：当前状态=FAILED"));
        mvc.perform(get("/storage")).andExpect(status().isInternalServerError());
        mvc.perform(get("/authority")).andExpect(status().isForbidden());
    }

    @RestController
    static class Probe {
        @GetMapping("/approval-replay") public void replay() {
            throw new IllegalStateException("WORK_SESSION_NOT_WAITING_APPROVAL：当前状态=FAILED");
        }
        @GetMapping("/storage") public void storage() {
            throw new IllegalStateException("WORK_SESSION_STORAGE_UNAVAILABLE");
        }
        @GetMapping("/authority") public void authority() {
            throw new SecurityException("WORK_SESSION_APPROVAL_FORBIDDEN");
        }
    }
}
