package cn.lgs.orbisops.application.skill;

import java.util.function.Supplier;

public interface SkillTransactionPort {

    <T> T required(Supplier<T> action);
}
