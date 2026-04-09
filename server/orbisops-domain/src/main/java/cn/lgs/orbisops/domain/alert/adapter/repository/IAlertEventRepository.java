package cn.lgs.orbisops.domain.alert.adapter.repository;

import cn.lgs.orbisops.domain.alert.model.AlertEventDraft;
import cn.lgs.orbisops.domain.alert.model.AlertEventSnapshot;
import cn.lgs.orbisops.domain.alert.model.AlertRunOutcome;

import java.util.List;

public interface IAlertEventRepository {

    List<AlertEventSnapshot> list(int limit);

    AlertEventSnapshot append(AlertEventDraft draft, boolean completed);

    void updateRunOutcome(AlertRunOutcome outcome, boolean completed);
}
