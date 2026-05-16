package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;
import com.alibaba.fastjson.JSON;

import java.util.LinkedHashMap;
import java.util.Map;

/** Infrastructure codec for the persisted ChangePackage snapshot_json columns. */
final class ChangePackageSnapshotJsonCodec {

    private ChangePackageSnapshotJsonCodec() {
    }

    static String encode(ChangePackageSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("CHANGE_PACKAGE_SNAPSHOT_REQUIRED");
        return JSON.toJSONString(snapshot.toMap());
    }

    static ChangePackageSnapshot decode(String snapshotJson, String packageHash) {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_VERSION_SNAPSHOT_REQUIRED");
        }
        try {
            Map<String, Object> parsed = JSON.parseObject(snapshotJson);
            if (parsed == null || parsed.isEmpty()) {
                throw new IllegalArgumentException("CHANGE_PACKAGE_VERSION_SNAPSHOT_INVALID");
            }
            return new ChangePackageSnapshot(new LinkedHashMap<>(parsed), packageHash);
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_VERSION_SNAPSHOT_INVALID", failure);
        }
    }
}
