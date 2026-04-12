package cn.lgs.orbisops.domain.investigation.model;

import java.util.Locale;

/** Question signals required by deterministic investigation follow-up policy. */
public record InvestigationQuestionSignals(String loweredQuestion,
                                           boolean logSignal,
                                           boolean metricSignal,
                                           boolean slowSqlSignal,
                                           boolean knowledgeSignal,
                                           boolean explicitRuntimeFilter) {

    public InvestigationQuestionSignals {
        loweredQuestion = loweredQuestion == null
                ? ""
                : loweredQuestion.trim().toLowerCase(Locale.ROOT);
    }
}
