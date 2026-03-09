package cn.lgs.orbisops.trigger.http;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalResponseStatusAdviceMvcTest {

    private static final Validator NO_OP_VALIDATOR = new Validator() {
        @Override
        public boolean supports(Class<?> clazz) {
            return true;
        }

        @Override
        public void validate(Object target, Errors errors) {
            // This contract test covers exception-to-status mapping only.
        }
    };

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new GateController())
            .setControllerAdvice(new GlobalExceptionHandler(), new GlobalResponseStatusAdvice())
            .setValidator(NO_OP_VALIDATOR)
            .build();

    @Test
    void sandboxStaleSecurityExceptionReturnsConflict() throws Exception {
        mockMvc.perform(get("/gate/sandbox-stale"))
                .andExpect(status().isConflict());
    }

    @Test
    void sandboxUntrustedSecurityExceptionReturnsConflict() throws Exception {
        mockMvc.perform(get("/gate/sandbox-untrusted"))
                .andExpect(status().isConflict());
    }

    @Test
    void mcpRequiresChangePackageSecurityExceptionReturnsConflict() throws Exception {
        mockMvc.perform(get("/gate/mcp-change-package"))
                .andExpect(status().isConflict());
    }

    @RestController
    private static class GateController {

        @GetMapping("/gate/sandbox-stale")
        void sandboxStale() {
            throw new SecurityException("SANDBOX_STALE：沙箱验证已过期");
        }

        @GetMapping("/gate/sandbox-untrusted")
        void sandboxUntrusted() {
            throw new SecurityException("SANDBOX_UNTRUSTED_COMMAND：验证命令不可信");
        }

        @GetMapping("/gate/mcp-change-package")
        void mcpRequiresChangePackage() {
            throw new SecurityException("MCP_TOOL_REQUIRES_CHANGE_PACKAGE：需要 ChangePackage");
        }
    }
}
