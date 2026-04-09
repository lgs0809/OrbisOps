package cn.lgs.orbisops.application.alert;

import cn.lgs.orbisops.domain.alert.model.AlertRunRequest;

@FunctionalInterface
public interface AlertOutboxSubmissionPort {
    String submit(AlertRunRequest request);
}
