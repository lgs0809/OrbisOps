package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;

/** Compatibility facade over the shared deterministic SHA-256 helper. */
public final class OpsRuntimeHashing {

    private OpsRuntimeHashing() {
    }

    public static String canonicalHash(Object value) {
        return CanonicalObjectHasher.sha256(value);
    }
}
