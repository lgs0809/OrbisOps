package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class ChangePackageCanonicalHasher {

    private static final Set<String> PACKAGE_TRANSIENT_FIELDS = Set.of(
            "packageHash", "createTime", "updateTime", "approvedAt");

    private ChangePackageCanonicalHasher() {
    }

    public static String packageHash(Map<String, Object> snapshot) {
        Map<String, Object> identity = new LinkedHashMap<>();
        if (snapshot != null) {
            snapshot.forEach((key, value) -> {
                if (!PACKAGE_TRANSIENT_FIELDS.contains(key)) identity.put(key, value);
            });
        }
        return canonicalHash(identity);
    }

    public static OperationHashes applyOperationHashes(Map<String, Object> operation) {
        if (operation == null) throw new IllegalArgumentException("CHANGE_PACKAGE_OPERATION_REQUIRED");
        OperationHashes hashes = calculateOperationHashes(operation);
        operation.put("argumentsHash", hashes.argumentsHash());
        operation.put("preconditionHash", hashes.preconditionHash());
        operation.put("postCheckHash", hashes.postCheckHash());
        operation.put("rollbackHash", hashes.rollbackHash());
        String operationHash = canonicalHash(canonicalOperation(operation, hashes));
        operation.put("operationHash", operationHash);
        return new OperationHashes(hashes.argumentsHash(), hashes.preconditionHash(), hashes.postCheckHash(),
                hashes.rollbackHash(), operationHash);
    }

    public static OperationHashes calculateOperationHashes(Map<String, Object> operation) {
        if (operation == null) throw new IllegalArgumentException("CHANGE_PACKAGE_OPERATION_REQUIRED");
        Object arguments = firstNonNull(operation.get("arguments"), operation.get("args"), Map.of());
        Object preconditions = firstNonNull(operation.get("preconditions"), operation.get("precondition"), Map.of());
        Object postCheck = firstNonNull(operation.get("postCheck"), operation.get("postCheckPlan"), Map.of());
        Object rollback = firstNonNull(operation.get("rollbackPlan"), operation.get("rollbackSteps"), Map.of());
        String argumentsHash = canonicalHash(arguments);
        String preconditionHash = canonicalHash(preconditions);
        String postCheckHash = canonicalHash(postCheck);
        String rollbackHash = canonicalHash(rollback);
        String operationHash = canonicalHash(canonicalOperation(operation,
                new OperationHashes(argumentsHash, preconditionHash, postCheckHash, rollbackHash, "")));
        return new OperationHashes(argumentsHash, preconditionHash, postCheckHash, rollbackHash, operationHash);
    }

    public static String canonicalHash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(CanonicalJson.stringify(canonicalValue(value)).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("CHANGE_PACKAGE_CANONICAL_HASH_FAILED", e);
        }
    }

    private static Map<String, Object> canonicalOperation(Map<String, Object> operation, OperationHashes hashes) {
        Map<String, Object> canonical = new LinkedHashMap<>(operation);
        canonical.put("argumentsHash", hashes.argumentsHash());
        canonical.put("preconditionHash", hashes.preconditionHash());
        canonical.put("postCheckHash", hashes.postCheckHash());
        canonical.put("rollbackHash", hashes.rollbackHash());
        canonical.remove("operationHash");
        canonical.remove("operation_hash");
        return canonical;
    }

    private static Object canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> canonical = new TreeMap<>();
            map.forEach((key, item) -> canonical.put(String.valueOf(key), canonicalValue(item)));
            return canonical;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> items = new ArrayList<>();
            iterable.forEach(item -> items.add(canonicalValue(item)));
            return items;
        }
        return value == null ? "" : value;
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    public record OperationHashes(String argumentsHash,
                                  String preconditionHash,
                                  String postCheckHash,
                                  String rollbackHash,
                                  String operationHash) {
    }
}
