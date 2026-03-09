package cn.lgs.orbisops.application.alert;

public interface AlertOutboxProjectCapacityPort {
    boolean canDispatch(String projectId, int maxRunning);
}
