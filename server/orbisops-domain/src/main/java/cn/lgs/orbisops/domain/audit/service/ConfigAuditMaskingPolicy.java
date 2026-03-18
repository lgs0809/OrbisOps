package cn.lgs.orbisops.domain.audit.service;

public final class ConfigAuditMaskingPolicy {

    public String mask(String payload) {
        if (payload == null || payload.isBlank()) return payload;
        return payload
                .replaceAll("(?i)(\"?[A-Za-z0-9_]*(secret|token|password|credential|private[_-]?key|api[_-]?key|webhook[_-]?secret|authorization|cookie|ssh[_-]?key|access[_-]?key)[A-Za-z0-9_]*\"?\\s*[:=]\\s*\")([^\"]+)(\")", "$1******$4")
                .replaceAll("(?i)(\\b[A-Za-z0-9_]*(secret|token|password|credential|private[_-]?key|api[_-]?key|webhook[_-]?secret|authorization|cookie|ssh[_-]?key|access[_-]?key)[A-Za-z0-9_]*\\s*=\\s*)([^\\s,;&\"}]+)", "$1******")
                .replaceAll("(?i)([a-z][a-z0-9+.-]*://[^\\s/@:]+:)([^\\s/@]+)(@)", "$1******$3")
                .replaceAll("(?i)(Bearer\\s+)[A-Za-z0-9._~+/-]+=*", "$1******");
    }
}
