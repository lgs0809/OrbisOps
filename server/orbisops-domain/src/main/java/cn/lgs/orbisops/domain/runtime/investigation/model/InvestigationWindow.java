package cn.lgs.orbisops.domain.runtime.investigation.model;

/** Bounded datasource query window carried across sub-agent retry attempts. */
public record InvestigationWindow(
        int rangeMinutes,
        String prometheusWindow,
        Boolean includeRecentLogs) {

    public InvestigationWindow {
        rangeMinutes = Math.max(1, rangeMinutes);
        prometheusWindow = prometheusWindow == null
                ? ""
                : prometheusWindow.trim();
    }
}
