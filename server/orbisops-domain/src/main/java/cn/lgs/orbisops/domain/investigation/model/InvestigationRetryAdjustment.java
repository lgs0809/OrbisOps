package cn.lgs.orbisops.domain.investigation.model;

/** Parameters selected for one bounded Investigation retry. */
public record InvestigationRetryAdjustment(int rangeMinutes,
                                           String promWindow) {
}
