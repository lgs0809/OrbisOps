package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.schedule.TaskScheduleCronValidationPort;
import org.springframework.scheduling.support.CronExpression;

/** Spring scheduler cron protocol adapter. */
public final class OpsTaskScheduleCronValidationAdapter implements TaskScheduleCronValidationPort {

    @Override
    public void validate(String cronExpression) {
        try {
            CronExpression.parse(cronExpression);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(
                    "Cron 表达式格式不正确，请使用 6 段格式，例如：0 0/15 * * * ?",
                    error);
        }
    }
}
