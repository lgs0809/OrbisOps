package cn.lgs.orbisops.trigger.ops.channel.slack;

import cn.lgs.orbisops.application.channel.ChannelAttachmentAssetPort;
import cn.lgs.orbisops.application.channel.provider.ChannelAttachment;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;

/** Downloads authenticated Slack files and immediately replaces provider URLs with durable OrbisOps object refs. */
@Component
final class OpsSlackAttachmentService {

    private static final long MAX_ATTACHMENT_BYTES = 50L * 1024 * 1024;
    private static final int MAX_ATTACHMENTS = 8;

    private final OpsSecretResolver secrets;
    private final OpsSlackFileMetadataClient metadata;
    private final ChannelAttachmentAssetPort assets;
    private final RestClient http;

    @Autowired
    OpsSlackAttachmentService(OpsSecretResolver secrets,
                              OpsSlackFileMetadataClient metadata,
                              ChannelAttachmentAssetPort assets) {
        this(secrets, metadata, assets, defaultHttp());
    }

    OpsSlackAttachmentService(OpsSecretResolver secrets,
                              OpsSlackFileMetadataClient metadata,
                              ChannelAttachmentAssetPort assets,
                              RestClient http) {
        if (secrets == null) throw new IllegalArgumentException("CHANNEL_SECRET_RESOLVER_REQUIRED");
        if (metadata == null) throw new IllegalArgumentException("SLACK_FILE_METADATA_CLIENT_REQUIRED");
        if (assets == null) throw new IllegalArgumentException("CHANNEL_ATTACHMENT_ASSET_PORT_REQUIRED");
        if (http == null) throw new IllegalArgumentException("SLACK_FILE_HTTP_CLIENT_REQUIRED");
        this.secrets = secrets;
        this.metadata = metadata;
        this.assets = assets;
        this.http = http;
    }

    List<ChannelAttachment> ingest(OpsSlackChannelConfiguration configuration,
                                   List<OpsSlackProtocolCodec.SlackFileDescriptor> descriptors) {
        if (descriptors == null || descriptors.isEmpty()) return List.of();
        if (descriptors.size() > MAX_ATTACHMENTS) throw new IllegalArgumentException("CHANNEL_TOO_MANY_ATTACHMENTS");
        String botToken = secrets.resolve(configuration.credentialRef());
        if (botToken == null || botToken.isBlank()) throw new IllegalStateException("SLACK_BOT_TOKEN_UNAVAILABLE");

        List<ChannelAttachment> result = new ArrayList<>();
        for (OpsSlackProtocolCodec.SlackFileDescriptor source : descriptors) {
            OpsSlackProtocolCodec.SlackFileDescriptor descriptor = source.requiresFileInfo() || source.downloadUrl().isBlank()
                    ? metadata.resolve(botToken, source)
                    : source;
            if (descriptor.sizeBytes() > MAX_ATTACHMENT_BYTES) {
                throw new IllegalArgumentException("CHANNEL_ATTACHMENT_SIZE_OUT_OF_RANGE");
            }
            URI uri = trustedSlackFileUri(descriptor.downloadUrl());
            byte[] content = http.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + botToken)
                    .retrieve()
                    .body(byte[].class);
            byte[] safeContent = content == null ? new byte[0] : content;
            if (safeContent.length > MAX_ATTACHMENT_BYTES) {
                throw new IllegalArgumentException("CHANNEL_ATTACHMENT_SIZE_OUT_OF_RANGE");
            }
            ChannelAttachmentAssetPort.StoredAttachment stored = assets.store(
                    new ChannelAttachmentAssetPort.StoreAttachment(
                            configuration.projectId(), configuration.channelId(), descriptor.fileId(),
                            descriptor.fileName(), descriptor.mediaType(), safeContent));
            result.add(new ChannelAttachment(
                    descriptor.fileId(), descriptor.fileName(), descriptor.mediaType(), stored.sizeBytes(),
                    stored.contentRef(), stored.contentHash()));
        }
        return List.copyOf(result);
    }

    private URI trustedSlackFileUri(String raw) {
        URI uri;
        try {
            uri = URI.create(raw == null ? "" : raw.trim());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("SLACK_ATTACHMENT_URL_INVALID", invalid);
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"files.slack.com".equals(host)) {
            throw new SecurityException("SLACK_ATTACHMENT_URL_UNTRUSTED");
        }
        return uri;
    }

    private static RestClient defaultHttp() {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(client))
                .build();
    }
}
