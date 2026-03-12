package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.schedule.TaskScheduleRuntimeConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsTaskScheduleRuntimeConfigurationCodecTest {

    private final OpsTaskScheduleRuntimeConfigurationCodec codec =
            new OpsTaskScheduleRuntimeConfigurationCodec();

    @Test
    void roundTripsUnifiedProjectAgentRuntimeConfiguration() {
        TaskScheduleRuntimeConfiguration expected = new TaskScheduleRuntimeConfiguration(
                "payment", "PINNED_VERSION", 5, "hash-v5", "检查错误率", 30, "5m",
                false, 4, 2, 90, 20, true, "oncall", "room");

        TaskScheduleRuntimeConfiguration actual = codec.decode(codec.encode(expected));

        assertEquals(expected, actual);
    }

    @Test
    void rejectsMissingLegacyAndMalformedConfigurationFailClosed() {
        IllegalStateException missing = assertThrows(IllegalStateException.class, () -> codec.decode(" "));
        IllegalStateException legacy = assertThrows(IllegalStateException.class,
                () -> codec.decode("旧版文本 prompt"));
        IllegalStateException malformed = assertThrows(IllegalStateException.class,
                () -> codec.decode("{not-json}"));

        assertEquals("周期任务缺少项目级 Agent 运行配置，请重新保存任务", missing.getMessage());
        assertEquals("周期任务仍使用旧版文本配置，请重新创建任务", legacy.getMessage());
        assertEquals("周期任务配置 JSON 不合法", malformed.getMessage());
    }
}
