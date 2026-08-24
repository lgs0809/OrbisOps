package cn.lgs.orbisops.application.episode;

@FunctionalInterface
public interface BackgroundModelReadinessPort {
    boolean ready(BackgroundModelRecoveryPort.Candidate candidate);
}
