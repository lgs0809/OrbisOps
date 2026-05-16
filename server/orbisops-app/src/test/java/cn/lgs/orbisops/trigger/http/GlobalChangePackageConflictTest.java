package cn.lgs.orbisops.trigger.http;

import cn.lgs.orbisops.domain.changepackage.service.ChangePackageStateMachine;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;

class GlobalChangePackageConflictTest {
    final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test void invalidApprovalTransitionIsAnActionableConflictAndRetainsReason() {
        var error=assertThrows(IllegalStateException.class,()->new ChangePackageStateMachine().requireTransition("VALIDATION_FAILED","APPROVED"));
        var response=handler.handleIllegalState(error);
        assertEquals(HttpStatus.CONFLICT,response.getStatusCode());
        assertEquals("CHANGE_PACKAGE_TRANSITION_REJECTED:VALIDATION_FAILED->APPROVED",response.getBody().getInfo());
    }
    @Test void disabledLandingAndUnapprovedRequestsAreConflictsWhileUnavailableStoreIsServerFailure() {
        assertEquals(HttpStatus.CONFLICT,handler.handleIllegalState(new IllegalStateException("APPROVED_LANDING_DISABLED：当前环境未开放")).getStatusCode());
        assertEquals(HttpStatus.CONFLICT,handler.handleIllegalState(new IllegalStateException("CHANGE_PACKAGE_APPROVAL_REQUIRED")).getStatusCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,handler.handleIllegalState(new IllegalStateException("CHANGE_PACKAGE_CURRENT_STORE_UNAVAILABLE")).getStatusCode());
    }

    @Test void mvcResponseAdviceCannotOverwriteExplicitConflictStatus() throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new StateController())
                .setControllerAdvice(handler,new GlobalResponseStatusAdvice()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/__change-state/approve"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/__change-state/land"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/__change-state/store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isInternalServerError());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/__change-state/store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isMethodNotAllowed())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Allow", org.hamcrest.Matchers.containsString("GET")));
    }
    @org.springframework.web.bind.annotation.RestController
    static class StateController {
        @org.springframework.web.bind.annotation.GetMapping("/__change-state/{action}")
        void action(@org.springframework.web.bind.annotation.PathVariable("action") String action) {
            switch(action) {
                case "approve" -> new ChangePackageStateMachine().requireTransition("VALIDATION_FAILED","APPROVED");
                case "land" -> throw new IllegalStateException("APPROVED_LANDING_DISABLED：当前环境未开放");
                default -> throw new IllegalStateException("CHANGE_PACKAGE_CURRENT_STORE_UNAVAILABLE");
            }
        }
    }
}
