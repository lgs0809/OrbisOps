package cn.lgs.orbisops.application.alert;

import cn.lgs.orbisops.domain.alert.model.AlertEventSnapshot;

public interface AlertEventIncidentPort {
    void ingest(AlertEventSnapshot event);
}
