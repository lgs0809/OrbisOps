package cn.lgs.orbisops.trigger.ops.capability;

import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeHashing;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Skill package fetch, parse, artifact, safety, identity, and create-command boundary. */
final class OpsSkillPackageMaterializer {

    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----");
    private static final Pattern RAW_SECRET = Pattern.compile(
            "(?im)^\\s*(?:password|passwd|token|api[_-]?key|access[_-]?key|secret[_-]?key)\\s*[:=]\\s*(?!\\$\\{env:|\\*{3})[^\\s#]{8,}");
    private static final Pattern AWS_ACCESS_KEY = Pattern.compile(
            "\\bAKIA[0-9A-Z]{16}\\b");

    private final OpsCapabilityArtifactFetcher artifactFetcher;
    private final OpsCapabilityImportUrlPolicy urlPolicy;
    private final OpsSkillImportRoutingResolver routingResolver =
            new OpsSkillImportRoutingResolver();

    OpsSkillPackageMaterializer(
            OpsCapabilityArtifactFetcher artifactFetcher,
            OpsCapabilityImportUrlPolicy urlPolicy) {
        this.artifactFetcher = artifactFetcher;
        this.urlPolicy = urlPolicy;
    }

    PreparedSkill prepare(Input input) {
        OpsCapabilityArtifactFetcher.FetchedArtifact root = artifactFetcher.fetch(
                input.sourceUrl(),
                input.settings().maxPackageBytes());
        String rootText = root.utf8();
        String name = text(input.capabilityName());
        String description = "";
        String content;
        List<Map<String, Object>> artifacts = new ArrayList<>();
        List<String> dependencies = List.of();
        List<String> evalSuites = List.of();
        Map<String, Object> packageRouting = Map.of();

        if (looksLikeJson(rootText)) {
            JSONObject manifest = parseManifest(rootText);
            if (!"SkillPackage".equalsIgnoreCase(text(manifest.get("kind")))) {
                throw new IllegalArgumentException("SKILL_PACKAGE_KIND_UNSUPPORTED");
            }
            JSONObject metadata = manifest.getJSONObject("metadata");
            name = firstText(
                    name,
                    metadata == null ? "" : metadata.getString("name"),
                    fileName(root.uri()));
            description = metadata == null
                    ? ""
                    : text(metadata.get("description"));
            packageRouting = objectMap(
                    manifest.containsKey("routingProfile")
                            ? manifest.get("routingProfile")
                            : metadata == null
                            ? null
                            : metadata.get("routingProfile"));
            Object inline = manifest.get("skillMd");
            if (inline != null && StringUtils.hasText(String.valueOf(inline))) {
                content = String.valueOf(inline);
            } else {
                String entry = firstText(
                        manifest.getString("skillMdUrl"),
                        manifest.getString("entrypoint"));
                if (!StringUtils.hasText(entry)) {
                    throw new IllegalArgumentException(
                            "SKILL_PACKAGE_ENTRYPOINT_REQUIRED");
                }
                content = artifactFetcher.fetch(
                                resolve(root.uri(), entry).toString(),
                                input.settings().maxArtifactBytes())
                        .utf8();
            }
            JSONArray rawArtifacts = manifest.getJSONArray("artifacts");
            if (rawArtifacts != null
                    && rawArtifacts.size()
                    > Math.max(1, input.settings().maxArtifacts())) {
                throw new IllegalArgumentException(
                        "SKILL_PACKAGE_TOO_MANY_ARTIFACTS");
            }
            if (rawArtifacts != null) {
                for (Object value : rawArtifacts) {
                    artifacts.add(loadArtifact(
                            root.uri(),
                            value,
                            input.settings().maxArtifactBytes()));
                }
            }
            dependencies = stringList(manifest.get("dependencies"));
            evalSuites = stringList(manifest.get("evalSuites"));
        } else {
            content = rootText;
            name = firstText(
                    name,
                    frontMatter(content, "name"),
                    fileName(root.uri()));
            description = frontMatter(content, "description");
        }

        assertSafeContent("SKILL.md", content);
        artifacts.forEach(item -> assertSafeContent(
                text(item.get("path")),
                text(item.get("content"))));
        Map<String, Object> routing = routingResolver.resolve(
                name, description, content, input.routingHints(), packageRouting);
        String sourceHash = OpsRuntimeHashing.canonicalHash(Map.of(
                "sourceUrl", input.sourceUrl(),
                "content", content,
                "artifacts", artifacts,
                "routingProfile", routing));
        String skillId = slug(name) + "-" + sourceHash.substring(0, 8);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("skillId", skillId);
        request.put("name", name);
        request.put("description", description);
        request.put("content", content);
        request.put("artifacts", artifacts);
        request.put("dependencies", dependencies);
        request.put("evalSuites", evalSuites);
        request.putAll(routing);
        request.put("status", "PAUSED");
        request.put("updateMode", "MANUAL_ONLY");
        request.put("autoUpdateEnabled", false);
        request.put("autoMergeEnabled", false);
        request.put("origin", "IMPORTED");
        request.put("sourceUrl", input.sourceUrl());
        request.put("sourceHash", sourceHash);
        return new PreparedSkill(skillId, sourceHash, request);
    }

    private JSONObject parseManifest(String rootText) {
        try {
            return JSON.parseObject(rootText);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "SKILL_PACKAGE_MANIFEST_INVALID",
                    e);
        }
    }

    private Map<String, Object> loadArtifact(
            URI base,
            Object raw,
            long maxArtifactBytes) {
        if (!(raw instanceof Map<?, ?> input)) {
            throw new IllegalArgumentException("SKILL_ARTIFACT_INVALID");
        }
        Map<String, Object> item = new LinkedHashMap<>();
        input.forEach((key, value) -> item.put(String.valueOf(key), value));
        String path = require(
                text(item.get("path")),
                "SKILL_ARTIFACT_PATH_REQUIRED");
        String role = firstText(text(item.get("role")), inferRole(path));
        String encoding = firstText(text(item.get("encoding")), "UTF-8");
        String content = text(item.get("content"));
        if (!StringUtils.hasText(content)) {
            String url = require(
                    text(item.get("url")),
                    "SKILL_ARTIFACT_CONTENT_OR_URL_REQUIRED");
            OpsCapabilityArtifactFetcher.FetchedArtifact fetched =
                    artifactFetcher.fetch(
                            resolve(base, url).toString(),
                            maxArtifactBytes);
            if (isBinary(path, fetched.contentType())) {
                content = Base64.getEncoder().encodeToString(fetched.bytes());
                encoding = "BASE64";
            } else {
                content = fetched.utf8();
            }
        }
        return Map.of(
                "path", path,
                "role", role,
                "encoding", encoding,
                "mediaType", firstText(
                        text(item.get("mediaType")),
                        "text/plain; charset=utf-8"),
                "content", content);
    }

    private void assertSafeContent(String path, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        if (PRIVATE_KEY.matcher(content).find()
                || RAW_SECRET.matcher(content).find()
                || AWS_ACCESS_KEY.matcher(content).find()) {
            throw new SecurityException(
                    "CAPABILITY_IMPORT_SECRET_DETECTED：" + path);
        }
    }

    private URI resolve(URI base, String value) {
        URI resolved = base.resolve(value).normalize();
        return urlPolicy.validate(resolved.toString());
    }

    private boolean looksLikeJson(String value) {
        return StringUtils.hasText(value)
                && value.stripLeading().startsWith("{");
    }

    private boolean isBinary(String path, String type) {
        String lower = path.toLowerCase(Locale.ROOT);
        return (type != null
                && !type.toLowerCase(Locale.ROOT).startsWith("text/")
                && !type.toLowerCase(Locale.ROOT).contains("json"))
                || lower.matches(".*\\.(png|jpg|jpeg|gif|webp|pdf|zip|jar)$");
    }

    private String inferRole(String path) {
        if (path.startsWith("scripts/")) {
            return "SCRIPT";
        }
        if (path.startsWith("templates/")) {
            return "TEMPLATE";
        }
        if (path.startsWith("evals/")) {
            return "EVAL";
        }
        if (path.startsWith("references/")) {
            return "REFERENCE";
        }
        return "RESOURCE";
    }

    private String frontMatter(String markdown, String key) {
        if (markdown == null || !markdown.startsWith("---")) {
            return "";
        }
        int end = markdown.indexOf("\n---", 3);
        if (end < 0) {
            return "";
        }
        Matcher matcher = Pattern.compile(
                        "(?m)^" + Pattern.quote(key)
                                + "\\s*:\\s*[\"']?(.+?)[\"']?\\s*$")
                .matcher(markdown.substring(3, end));
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String fileName(URI uri) {
        String path = uri.getPath();
        if (!StringUtils.hasText(path)) {
            return "imported-skill";
        }
        String name = path.substring(path.lastIndexOf('/') + 1)
                .replaceFirst("(?i)\\.(md|json)$", "");
        return StringUtils.hasText(name)
                && !"skill".equalsIgnoreCase(name)
                ? name
                : "imported-skill";
    }

    private String slug(String value) {
        String result = firstText(value, "imported-capability")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "");
        return StringUtils.hasText(result)
                ? result
                : "imported-capability";
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        iterable.forEach(item -> {
            if (StringUtils.hasText(String.valueOf(item))) {
                result.add(String.valueOf(item));
            }
        });
        return List.copyOf(result);
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) ->
                result.put(String.valueOf(key), item));
        return Map.copyOf(result);
    }

    private String require(String value, String code) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(code);
        }
        return value.trim();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    record Input(
            String sourceUrl,
            String capabilityName,
            Map<String, Object> routingHints,
            Settings settings) {
    }

    record Settings(
            long maxArtifactBytes,
            long maxPackageBytes,
            int maxArtifacts) {
    }

    record PreparedSkill(
            String skillId,
            String sourceHash,
            Map<String, Object> createRequest) {
    }
}
