package cn.lgs.orbisops.domain.changepackage.model;

public record ChangePackagePreparationProof(String status,
                                            String source,
                                            boolean untrusted) {

    public ChangePackagePreparationProof {
        status = value(status);
        source = value(source);
    }

    public boolean passed() {
        if (untrusted || "UNTRUSTED_USER_INPUT".equalsIgnoreCase(source)) {
            return false;
        }
        return "PASSED".equalsIgnoreCase(status) || "SUCCEEDED".equalsIgnoreCase(status);
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
