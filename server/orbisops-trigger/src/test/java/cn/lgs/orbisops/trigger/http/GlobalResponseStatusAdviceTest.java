package cn.lgs.orbisops.trigger.http;

import cn.lgs.orbisops.types.enums.ResponseCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalResponseStatusAdviceTest {

    @Test
    void sandboxAndMcpGateErrorsMapToConflictEvenWhenCodeIsForbidden() {
        GlobalResponseStatusAdvice advice = new GlobalResponseStatusAdvice();

        assertEquals(HttpStatus.CONFLICT,
                ReflectionTestUtils.invokeMethod(advice, "mapStatus", ResponseCode.FORBIDDEN.getCode(), "SANDBOX_STALE：沙箱验证已过期"));
        assertEquals(HttpStatus.CONFLICT,
                ReflectionTestUtils.invokeMethod(advice, "mapStatus", ResponseCode.FORBIDDEN.getCode(), "SANDBOX_UNTRUSTED_COMMAND：验证命令不可信"));
        assertEquals(HttpStatus.CONFLICT,
                ReflectionTestUtils.invokeMethod(advice, "mapStatus", ResponseCode.FORBIDDEN.getCode(), "MCP_TOOL_REQUIRES_CHANGE_PACKAGE：需要 ChangePackage"));
    }
}
