package cn.lgs.orbisops.trigger.ops.capability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Bounded, no-redirect downloader used only after URL policy validation. */
@Service
public class OpsCapabilityArtifactFetcher {

    private final OpsCapabilityImportUrlPolicy urlPolicy;
    private final OpsCapabilityImportSettings settings;
    private final HttpClient client;

    public OpsCapabilityArtifactFetcher(OpsCapabilityImportUrlPolicy urlPolicy) {
        this(urlPolicy, OpsCapabilityImportSettings.legacyConstructorDefaults());
    }

    @Autowired
    public OpsCapabilityArtifactFetcher(
            OpsCapabilityImportUrlPolicy urlPolicy,
            OpsCapabilityImportSettings settings) {
        this(
                urlPolicy,
                settings,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build());
    }

    OpsCapabilityArtifactFetcher(
            OpsCapabilityImportUrlPolicy urlPolicy,
            OpsCapabilityImportSettings settings,
            HttpClient client) {
        this.urlPolicy = urlPolicy;
        this.settings = settings == null
                ? OpsCapabilityImportSettings.defaults()
                : settings;
        this.client = client;
    }

    public FetchedArtifact fetch(String value, long maxBytes) {
        URI uri = urlPolicy.validate(value);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(settings.fetchTimeoutSeconds()))
                .header(
                        "Accept",
                        "text/markdown, application/json, text/plain, application/octet-stream")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                throw new IllegalArgumentException("CAPABILITY_IMPORT_REDIRECT_FORBIDDEN");
            }
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("CAPABILITY_IMPORT_HTTP_" + status);
            }
            long declared = response.headers()
                    .firstValueAsLong("Content-Length")
                    .orElse(-1L);
            if (declared > maxBytes) {
                throw new IllegalArgumentException("CAPABILITY_IMPORT_ARTIFACT_TOO_LARGE");
            }
            byte[] bytes;
            try (InputStream input = response.body()) {
                bytes = readBounded(input, maxBytes);
            }
            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse("application/octet-stream");
            return new FetchedArtifact(uri, bytes, contentType);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CAPABILITY_IMPORT_FETCH_INTERRUPTED", error);
        } catch (IOException error) {
            throw new IllegalStateException("CAPABILITY_IMPORT_FETCH_FAILED", error);
        }
    }

    private byte[] readBounded(InputStream input, long maxBytes) throws IOException {
        long limit = Math.max(1L, maxBytes);
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                (int) Math.min(limit, 8_192L));
        byte[] buffer = new byte[8_192];
        long total = 0L;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > limit) {
                throw new IllegalArgumentException("CAPABILITY_IMPORT_ARTIFACT_TOO_LARGE");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    public record FetchedArtifact(URI uri, byte[] bytes, String contentType) {
        public String utf8() {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
