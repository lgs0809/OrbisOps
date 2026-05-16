package cn.lgs.orbisops.trigger.http;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalHttpStatusContractTest {

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler(), new GlobalResponseStatusAdvice())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    void sandboxStaleIsHttp409() throws Exception {
        assertConflict("sandbox-stale", "SANDBOX_STALE");
    }

    @Test
    void untrustedSandboxCommandIsHttp409() throws Exception {
        assertConflict("sandbox-untrusted", "SANDBOX_UNTRUSTED_COMMAND");
    }

    @Test
    void mcpTargetWriteRequiresChangePackageIsHttp409() throws Exception {
        assertConflict("mcp-package", "MCP_TOOL_REQUIRES_CHANGE_PACKAGE");
    }

    private void assertConflict(String kind, String reasonCode) throws Exception {
        mockMvc.perform(get("/__status-contract/{kind}", kind))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.info").value(org.hamcrest.Matchers.containsString(reasonCode)));
    }

    @RestController
    static class FailureController {
        @GetMapping("/__status-contract/{kind}")
        void fail(@PathVariable("kind") String kind) {
            switch (kind) {
                case "sandbox-stale" -> throw new SecurityException("SANDBOX_STALE：packageHash 已变化");
                case "sandbox-untrusted" -> throw new SecurityException("SANDBOX_UNTRUSTED_COMMAND：用户命令不能作为 proof");
                case "mcp-package" -> throw new SecurityException("MCP_TOOL_REQUIRES_CHANGE_PACKAGE：生产写必须审批");
                default -> throw new IllegalArgumentException("unknown kind");
            }
        }
    }
}
