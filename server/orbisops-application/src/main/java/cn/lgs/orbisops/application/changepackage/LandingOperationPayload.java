package cn.lgs.orbisops.application.changepackage;

/** External execution payload plus authoritative evidence identifiers. */
public record LandingOperationPayload(
        Object raw,
        String remoteRequestId,
        String resultId,
        String outputHash) {

    public LandingOperationPayload {
        raw = raw == null ? java.util.Map.of() : raw;
        remoteRequestId = text(remoteRequestId);
        resultId = text(resultId);
        outputHash = text(outputHash);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
