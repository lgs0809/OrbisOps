package cn.lgs.orbisops.application.episode;

import java.util.List;

/** Interpretation only: model output never records or grants task acceptance. */
public interface TaskAcceptanceDraftModelPort {
    Draft propose(String frozenInput);
    record Check(String label, String resultId, String pointer, String operator, Object expected) { }
    record Draft(String status, String explanation, String goalReview, List<Check> checks) { }
}
