package cn.lgs.orbisops.acceptance;

import cn.lgs.orbisops.domain.skill.model.SkillExperienceConsolidationSample;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionSourceSetPolicy;
import cn.lgs.orbisops.domain.skill.service.SkillPackageArtifactPolicy;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.nio.file.*;
import java.util.*;

/** Calls the actual packaged structural policies. No model, persistence or business acceptance. */
public final class SkillGovernanceTruthProbe {
    public static void main(String[] args) throws Exception {
        JSONArray facts = JSON.parseArray(Files.readString(Path.of(args[0])));
        JSONArray reference = JSON.parseArray(Files.readString(Path.of(args[1])));
        Map<String, JSONObject> truths = new HashMap<>();
        for (Object entry : reference) {
            JSONObject row = (JSONObject) entry;
            truths.put(row.getString("caseId"), row);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object entry : facts) {
            JSONObject row = (JSONObject) entry;
            JSONObject truth = truths.get(row.getString("caseId"));
            if (!truth.getString("split").equals(args[2])) continue;
            JSONObject input = row.getJSONObject("facts").getJSONObject("observations");
            boolean allowed = false;
            String rejection = "";
            try {
                if ("SKILL_SOURCE_STRUCTURE".equals(input.getString("nativeOperation"))) {
                    List<SkillExperienceConsolidationSample> sources = new ArrayList<>();
                    for (Object sourceEntry : input.getJSONArray("sources")) {
                        JSONObject s = (JSONObject) sourceEntry;
                        sources.add(new SkillExperienceConsolidationSample(
                            s.getString("observationId"), s.getString("runId"), s.getString("sessionId"),
                            s.getString("observationType"), s.getString("outcome"), s.getString("taskTemplateHash"),
                            s.getString("trajectoryHash"), s.getString("summary"), s.getDoubleValue("qualityScore"),
                            List.of(), s.getString("sourceId"), s.getString("sourceHash"), s.getString("episodeJson"),
                            s.getString("taskEpisodeId"), s.getString("conditionKey")));
                    }
                    new SkillEvolutionSourceSetPolicy().requireUsable(sources, input.getString("projectId"),
                        input.getString("currentRun"), input.getString("currentHash"));
                } else if ("SKILL_PACKAGE_ARTIFACT".equals(input.getString("nativeOperation"))) {
                    SkillPackageArtifactPolicy.validate(input.get("path"), input.get("role"), input.get("mediaType"),
                        input.get("encoding"), input.get("content"), input.getBooleanValue("executable"),
                        input.getLongValue("maxArtifactBytes"));
                } else {
                    throw new IllegalStateException("UNKNOWN_NATIVE_OPERATION");
                }
                allowed = true;
            } catch (IllegalArgumentException | IllegalStateException failure) {
                rejection = failure.getMessage();
            }
            String actual = allowed ? "满足结构约束" : "必须拒绝";
            rows.add(Map.of("caseId", row.getString("caseId"), "split", args[2], "family", truth.getString("family"),
                "actualLabel", actual, "expectedLabel", truth.getString("expectedLabel"), "rejection", rejection,
                "status", actual.equals(truth.getString("expectedLabel")) ? "PASS" : "FAIL"));
        }
        long failures = rows.stream().filter(row -> row.get("status").equals("FAIL")).count();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("scope", "ACTUAL_PACKAGED_STRUCTURAL_POLICIES_SYNTHETIC_INPUT_NO_PERSISTENCE_NO_MODEL");
        report.put("executedAt", java.time.Instant.now().toString());
        report.put("split", args[2]); report.put("tests", rows.size()); report.put("failures", failures);
        report.put("status", failures == 0 && !rows.isEmpty() ? "PASS" : "FAIL");
        report.put("newModelExecutions", 0); report.put("businessWrites", 0); report.put("results", rows);
        Files.writeString(Path.of(args[3]), JSON.toJSONString(report, true), StandardOpenOption.CREATE_NEW);
        if (failures != 0 || rows.isEmpty()) System.exit(1);
    }
}
