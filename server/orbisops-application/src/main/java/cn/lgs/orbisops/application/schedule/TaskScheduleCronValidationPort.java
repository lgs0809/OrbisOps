package cn.lgs.orbisops.application.schedule;

/** Protocol boundary for validating scheduler cron expressions. */
public interface TaskScheduleCronValidationPort {

    void validate(String cronExpression);
}
