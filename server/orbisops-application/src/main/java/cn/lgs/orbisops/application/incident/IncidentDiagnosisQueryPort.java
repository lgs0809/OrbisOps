package cn.lgs.orbisops.application.incident;

import cn.lgs.orbisops.domain.incident.model.DiagnosisResult;

import java.util.List;
import java.util.Optional;

/** Narrow published-language port for reading authoritative structured diagnosis from linked analysis runs. */
public interface IncidentDiagnosisQueryPort {

    Optional<DiagnosisResult> latestByRunIds(List<String> runIds);
}
