package cn.lgs.orbisops.trigger.ops.channel.slack;

import cn.lgs.orbisops.application.channel.ChannelAttachmentAssetPort;
import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpsSlackAttachmentServiceTest {

    @Test
    void downloadsAuthenticatedSlackFileThenPersistsOnlyOpaqueObjectReference() {
        OpsSecretResolver secrets = mock(OpsSecretResolver.class);
        OpsSlackFileMetadataClient metadata = mock(OpsSlackFileMetadataClient.class);
        ChannelAttachmentAssetPort assets = mock(ChannelAttachmentAssetPort.class);
        when(secrets.resolve("credential-ref")).thenReturn("credential-value");
        when(assets.store(any())).thenReturn(new ChannelAttachmentAssetPort.StoredAttachment(
                "object://channel/11111111-1111-1111-1111-111111111111", "a".repeat(64), 3));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://files.slack.com/files-pri/T1-F1/download/a.txt"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer credential-value"))
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.APPLICATION_OCTET_STREAM));
        OpsSlackAttachmentService service = new OpsSlackAttachmentService(secrets, metadata, assets, builder.build());
        var descriptor = new OpsSlackProtocolCodec.SlackFileDescriptor(
                "F1", "a.txt", "text/plain", 3,
                "https://files.slack.com/files-pri/T1-F1/download/a.txt", false);

        var result = service.ingest(configuration(), List.of(descriptor));

        assertEquals(1, result.size());
        assertEquals("object://channel/11111111-1111-1111-1111-111111111111", result.get(0).contentRef());
        assertEquals("a".repeat(64), result.get(0).contentHash());
        verify(assets).store(any());
        server.verify();
    }

    @Test
    void resolvesPartialSlackConnectFileBeforeDownload() {
        OpsSecretResolver secrets = mock(OpsSecretResolver.class);
        OpsSlackFileMetadataClient metadata = mock(OpsSlackFileMetadataClient.class);
        ChannelAttachmentAssetPort assets = mock(ChannelAttachmentAssetPort.class);
        when(secrets.resolve("credential-ref")).thenReturn("credential-value");
        var partial = new OpsSlackProtocolCodec.SlackFileDescriptor(
                "F2", "F2", "application/octet-stream", 0, "", true);
        var resolved = new OpsSlackProtocolCodec.SlackFileDescriptor(
                "F2", "incident.log", "text/plain", 2,
                "https://files.slack.com/files-pri/T1-F2/download/incident.log", false);
        when(metadata.resolve("credential-value", partial)).thenReturn(resolved);
        when(assets.store(any())).thenReturn(new ChannelAttachmentAssetPort.StoredAttachment(
                "object://channel/22222222-2222-2222-2222-222222222222", "b".repeat(64), 2));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(resolved.downloadUrl()))
                .andRespond(withSuccess(new byte[]{7, 8}, MediaType.APPLICATION_OCTET_STREAM));
        OpsSlackAttachmentService service = new OpsSlackAttachmentService(secrets, metadata, assets, builder.build());

        var result = service.ingest(configuration(), List.of(partial));

        assertEquals("incident.log", result.get(0).fileName());
        verify(metadata).resolve("credential-value", partial);
        server.verify();
    }

    @Test
    void rejectsUntrustedDownloadHostAndOversizedMetadataBeforeNetworkIo() {
        OpsSecretResolver secrets = mock(OpsSecretResolver.class);
        OpsSlackFileMetadataClient metadata = mock(OpsSlackFileMetadataClient.class);
        ChannelAttachmentAssetPort assets = mock(ChannelAttachmentAssetPort.class);
        when(secrets.resolve("credential-ref")).thenReturn("credential-value");
        OpsSlackAttachmentService service = new OpsSlackAttachmentService(
                secrets, metadata, assets, RestClient.builder().build());

        assertThrows(SecurityException.class, () -> service.ingest(configuration(), List.of(
                new OpsSlackProtocolCodec.SlackFileDescriptor(
                        "F3", "x.txt", "text/plain", 1, "https://evil.example/x", false))));
        assertThrows(IllegalArgumentException.class, () -> service.ingest(configuration(), List.of(
                new OpsSlackProtocolCodec.SlackFileDescriptor(
                        "F4", "huge.bin", "application/octet-stream", 50L * 1024 * 1024 + 1,
                        "https://files.slack.com/files-pri/T-F4/download/huge.bin", false))));
    }

    private OpsSlackChannelConfiguration configuration() {
        return new OpsSlackChannelConfiguration(
                "channel-1", "project-1", "credential-ref", "app-credential-ref",
                ChannelConnectionMode.LONG_CONNECTION, true);
    }
}
