package cn.lgs.orbisops.trigger.ops.skill;

import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OpsSkillAuthoringAgentTest {
    private Map<String,Object> author(String response) {
        var client=mock(OpsSkillAuthoringModelClient.class);
        when(client.available()).thenReturn(true);
        when(client.generate(anyString(),anyString())).thenReturn(JSON.parseObject(response));
        return new OpsSkillAuthoringAgent(client).author(Map.of());
    }
    @Test void missingOrUnknownDecisionCannotBeConsumedAsNoChange() {
        for(String response:new String[]{"{}","{\"reason\":\"incomplete\"}",
                "{\"patchType\":\"CREATE_OTHER\",\"reason\":\"unknown\"}","{\"patchType\":\"NO_CHANGE\"}"})
            assertThrows(IllegalStateException.class,()->author(response));
    }
    @Test void conflictingNoChangeCannotDiscardAnAuthoredModification() {
        for(String field:new String[]{"\"targetSkillId\":\"existing\"", "\"changes\":[{\"section\":\"routingProfile\"}]",
                "\"artifacts\":[{\"path\":\"resources/method.json\"}]"})
            assertThrows(IllegalStateException.class,()->author("{\"patchType\":\"NO_CHANGE\",\"reason\":\"covered\","+field+"}"));
    }
    @Test void explicitNoChangeRetainsReasonAndInputProvenance() {
        var result=author("{\"patchType\":\"NO_CHANGE\",\"reason\":\"Existing method covers all verified steps\","
                +"\"modelInputEncoding\":\"ORIGINAL\",\"sourceInputHash\":\"abc\",\"modelInputHash\":\"abc\","
                +"\"sourceInputChars\":42,\"modelInputChars\":42}");
        assertEquals("NO_CHANGE",result.get("patchType"));
        assertEquals("Existing method covers all verified steps",result.get("reason"));
        assertEquals("abc",result.get("sourceInputHash"));
        assertEquals(42,result.get("modelInputChars"));
    }
}
