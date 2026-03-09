package cn.lgs.orbisops.trigger.ops.capability;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsSkillPackageMaterializerTest {

    @Test
    void materializesManifestInlineAndRemoteArtifactsWithPausedCreateRequest() {
        OpsCapabilityArtifactFetcher fetcher = mock(OpsCapabilityArtifactFetcher.class);
        OpsCapabilityImportUrlPolicy urlPolicy = mock(OpsCapabilityImportUrlPolicy.class);
        URI rootUri = URI.create("https://example.com/packages/skill-package.json");
        URI guideUri = URI.create("https://example.com/packages/references/guide.md");
        URI logoUri = URI.create("https://example.com/packages/assets/logo.png");
        String manifest = """
                {
                  "kind":"SkillPackage",
                  "metadata":{"name":"Manifest Skill","description":"Imported package"},
                  "skillMd":"# Main Skill\\nUse evidence first.",
                  "artifacts":[
                    {"path":"scripts/check.sh","content":"echo ok"},
                    {"path":"references/guide.md","url":"references/guide.md"},
                    {"path":"assets/logo.png","url":"assets/logo.png","mediaType":"image/png"}
                  ],
                  "dependencies":["mysql", ""],
                  "evalSuites":["slow-sql-eval"]
                }
                """;
        when(fetcher.fetch("https://example.com/packages/skill-package.json", 2_048L))
                .thenReturn(fetched(rootUri, manifest, "application/json"));
        when(urlPolicy.validate(guideUri.toString())).thenReturn(guideUri);
        when(urlPolicy.validate(logoUri.toString())).thenReturn(logoUri);
        when(fetcher.fetch(guideUri.toString(), 512L))
                .thenReturn(fetched(guideUri, "# Guide", "text/markdown"));
        byte[] logo = new byte[]{1, 2, 3, 4};
        when(fetcher.fetch(logoUri.toString(), 512L))
                .thenReturn(new OpsCapabilityArtifactFetcher.FetchedArtifact(
                        logoUri,
                        logo,
                        "image/png"));
        OpsSkillPackageMaterializer materializer =
                new OpsSkillPackageMaterializer(fetcher, urlPolicy);

        OpsSkillPackageMaterializer.PreparedSkill prepared = materializer.prepare(
                input("https://example.com/packages/skill-package.json", "Override Name", 8));

        Map<String, Object> request = prepared.createRequest();
        assertTrue(prepared.skillId().startsWith("override-name-"));
        assertEquals(8 + "override-name-".length(), prepared.skillId().length());
        assertFalse(prepared.sourceHash().isBlank());
        assertEquals(prepared.sourceHash(), request.get("sourceHash"));
        assertEquals(prepared.skillId(), request.get("skillId"));
        assertEquals("Override Name", request.get("name"));
        assertEquals("Imported package", request.get("description"));
        assertEquals("# Main Skill\nUse evidence first.", request.get("content"));
        assertEquals(List.of("mysql"), request.get("dependencies"));
        assertEquals(List.of("slow-sql-eval"), request.get("evalSuites"));
        assertEquals("PAUSED", request.get("status"));
        assertEquals("MANUAL_ONLY", request.get("updateMode"));
        assertEquals(false, request.get("autoUpdateEnabled"));
        assertEquals(false, request.get("autoMergeEnabled"));
        assertEquals("IMPORTED", request.get("origin"));
        assertEquals(List.of("故障诊断"), request.get("whenToUse"));
        assertEquals(List.of("生产写操作"), request.get("whenNotToUse"));
        assertEquals(
                "https://example.com/packages/skill-package.json",
                request.get("sourceUrl"));

        List<?> artifacts = (List<?>) request.get("artifacts");
        assertEquals(3, artifacts.size());
        Map<?, ?> script = (Map<?, ?>) artifacts.get(0);
        assertEquals("scripts/check.sh", script.get("path"));
        assertEquals("SCRIPT", script.get("role"));
        assertEquals("UTF-8", script.get("encoding"));
        assertEquals("echo ok", script.get("content"));
        Map<?, ?> guide = (Map<?, ?>) artifacts.get(1);
        assertEquals("REFERENCE", guide.get("role"));
        assertEquals("# Guide", guide.get("content"));
        Map<?, ?> image = (Map<?, ?>) artifacts.get(2);
        assertEquals("RESOURCE", image.get("role"));
        assertEquals("BASE64", image.get("encoding"));
        assertEquals("image/png", image.get("mediaType"));
        assertEquals(Base64.getEncoder().encodeToString(logo), image.get("content"));

        verify(urlPolicy).validate(guideUri.toString());
        verify(urlPolicy).validate(logoUri.toString());
    }

    @Test
    void materializesMarkdownFrontMatterAndFallsBackFromSkillFileName() {
        OpsCapabilityArtifactFetcher fetcher = mock(OpsCapabilityArtifactFetcher.class);
        OpsCapabilityImportUrlPolicy urlPolicy = mock(OpsCapabilityImportUrlPolicy.class);
        URI uri = URI.create("https://example.com/SKILL.md");
        String markdown = """
                ---
                name: 'Slow SQL'
                description: "Diagnose database latency"
                ---
                # Slow SQL
                """;
        when(fetcher.fetch(uri.toString(), 2_048L))
                .thenReturn(fetched(uri, markdown, "text/markdown"));
        OpsSkillPackageMaterializer materializer =
                new OpsSkillPackageMaterializer(fetcher, urlPolicy);

        OpsSkillPackageMaterializer.PreparedSkill prepared = materializer.prepare(
                input(uri.toString(), "", 8));

        assertTrue(prepared.skillId().startsWith("slow-sql-"));
        assertEquals("Slow SQL", prepared.createRequest().get("name"));
        assertEquals(
                "Diagnose database latency",
                prepared.createRequest().get("description"));
        assertEquals(markdown, prepared.createRequest().get("content"));
        assertEquals(List.of(), prepared.createRequest().get("artifacts"));
    }

    @Test
    void rejectsNewSkillPackageWithoutPositiveAndNegativeRoutingBoundary() {
        OpsCapabilityArtifactFetcher fetcher =
                mock(OpsCapabilityArtifactFetcher.class);
        OpsCapabilityImportUrlPolicy urlPolicy =
                mock(OpsCapabilityImportUrlPolicy.class);
        URI uri = URI.create("https://example.com/SKILL.md");
        when(fetcher.fetch(uri.toString(), 2_048L))
                .thenReturn(fetched(
                        uri,
                        "# Imported skill",
                        "text/markdown"));
        OpsSkillPackageMaterializer materializer =
                new OpsSkillPackageMaterializer(fetcher, urlPolicy);

        OpsSkillImportRoutingResolver.RoutingMetadataRequired error =
                assertThrows(
                        OpsSkillImportRoutingResolver.RoutingMetadataRequired.class,
                        () -> materializer.prepare(new OpsSkillPackageMaterializer.Input(
                                uri.toString(),
                                "",
                                Map.of(),
                                new OpsSkillPackageMaterializer.Settings(
                                        512L,
                                        2_048L,
                                        8))));

        assertEquals(
                List.of("whenToUse", "whenNotToUse"),
                error.requiredFields());
    }

    @Test
    void rejectsInvalidManifestKindEntrypointArtifactCountAndSecrets() {
        OpsCapabilityArtifactFetcher fetcher = mock(OpsCapabilityArtifactFetcher.class);
        OpsCapabilityImportUrlPolicy urlPolicy = mock(OpsCapabilityImportUrlPolicy.class);
        URI uri = URI.create("https://example.com/package.json");
        OpsSkillPackageMaterializer materializer =
                new OpsSkillPackageMaterializer(fetcher, urlPolicy);

        when(fetcher.fetch(uri.toString(), 2_048L))
                .thenReturn(
                        fetched(uri, "{invalid", "application/json"),
                        fetched(uri, "{\"kind\":\"Other\"}", "application/json"),
                        fetched(uri, "{\"kind\":\"SkillPackage\"}", "application/json"),
                        fetched(uri, """
                                {"kind":"SkillPackage","skillMd":"# Skill","artifacts":[
                                  {"path":"a.txt","content":"a"},
                                  {"path":"b.txt","content":"b"}
                                ]}
                                """, "application/json"),
                        fetched(uri, "# Skill\napi_key=1234567890", "text/markdown"));

        IllegalArgumentException invalid = assertThrows(
                IllegalArgumentException.class,
                () -> materializer.prepare(input(uri.toString(), "", 8)));
        assertEquals("SKILL_PACKAGE_MANIFEST_INVALID", invalid.getMessage());
        assertNotNull(invalid.getCause());
        assertEquals(
                "SKILL_PACKAGE_KIND_UNSUPPORTED",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> materializer.prepare(input(uri.toString(), "", 8)))
                        .getMessage());
        assertEquals(
                "SKILL_PACKAGE_ENTRYPOINT_REQUIRED",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> materializer.prepare(input(uri.toString(), "", 8)))
                        .getMessage());
        assertEquals(
                "SKILL_PACKAGE_TOO_MANY_ARTIFACTS",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> materializer.prepare(input(uri.toString(), "", 1)))
                        .getMessage());
        assertEquals(
                "CAPABILITY_IMPORT_SECRET_DETECTED：SKILL.md",
                assertThrows(
                        SecurityException.class,
                        () -> materializer.prepare(input(uri.toString(), "", 8)))
                        .getMessage());
    }

    private OpsSkillPackageMaterializer.Input input(
            String sourceUrl,
            String capabilityName,
            int maxArtifacts) {
        return new OpsSkillPackageMaterializer.Input(
                sourceUrl,
                capabilityName,
                Map.of(
                        "whenToUse", List.of("故障诊断"),
                        "whenNotToUse", List.of("生产写操作")),
                new OpsSkillPackageMaterializer.Settings(
                        512L,
                        2_048L,
                        maxArtifacts));
    }

    private OpsCapabilityArtifactFetcher.FetchedArtifact fetched(
            URI uri,
            String content,
            String contentType) {
        return new OpsCapabilityArtifactFetcher.FetchedArtifact(
                uri,
                content.getBytes(StandardCharsets.UTF_8),
                contentType);
    }
}
