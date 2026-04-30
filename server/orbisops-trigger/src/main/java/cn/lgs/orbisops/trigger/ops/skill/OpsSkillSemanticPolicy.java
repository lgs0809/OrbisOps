package cn.lgs.orbisops.trigger.ops.skill;

import org.springframework.util.StringUtils;

import java.util.List;

/** Pure candidate filtering, cache-key, and cosine similarity rules. */
final class OpsSkillSemanticPolicy {

    List<OpsSkillSemanticMatcher.SkillDocument> bounded(
            List<OpsSkillSemanticMatcher.SkillDocument> documents,
            OpsSkillSemanticSettings settings) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        OpsSkillSemanticSettings effective = settings == null
                ? OpsSkillSemanticSettings.defaults()
                : settings;
        return documents.stream()
                .filter(document -> document != null
                        && StringUtils.hasText(document.id())
                        && StringUtils.hasText(document.text()))
                .limit(effective.candidateLimit())
                .toList();
    }

    String cacheKey(OpsSkillSemanticMatcher.SkillDocument document) {
        return StringUtils.hasText(document.cacheKey())
                ? document.cacheKey()
                : document.id() + ":" + document.text().hashCode();
    }

    double cosine(float[] left, float[] right) {
        if (left == null
                || right == null
                || left.length == 0
                || left.length != right.length) {
            return 0D;
        }
        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0D || rightNorm == 0D) {
            return 0D;
        }
        return Math.max(
                0D,
                Math.min(
                        1D,
                        dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm))));
    }
}
