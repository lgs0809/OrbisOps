package cn.lgs.orbisops.domain.alert.adapter.repository;

import cn.lgs.orbisops.domain.alert.model.AlertRuleDefinition;

import java.util.List;
import java.util.Optional;

public interface IAlertRuleRepository {

    List<AlertRuleDefinition> list();

    Optional<AlertRuleDefinition> find(Long id);

    AlertRuleDefinition save(AlertRuleDefinition rule);

    boolean updateStatus(Long id, int status);

    boolean delete(Long id);
}
