package cn.lgs.orbisops.trigger.ops.channel.feishu;

import cn.lgs.orbisops.application.channel.ChannelAttachmentAssetPort;
import cn.lgs.orbisops.application.channel.ReceiveChannelMessageUseCase;
import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.trigger.application.channel.OpsChannelInteractiveActionDispatcher;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import com.lark.oapi.channel.LarkChannel;
import com.lark.oapi.channel.model.ResourceDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsFeishuAttachmentIngestTest {

    @Test
    void downloadsResourceThroughLarkChannelAndPersistsOnlyOpaqueObjectReference() {
        ChannelAttachmentAssetPort assets = mock(ChannelAttachmentAssetPort.class);
        OpsFeishuChannelConnectionDriver driver = driver(assets);
        LarkChannel channel = mock(LarkChannel.class);
        ResourceDescriptor resource = mock(ResourceDescriptor.class);
        when(resource.getFileKey()).thenReturn("file-key-private");
        when(resource.getType()).thenReturn("file");
        when(channel.downloadResource("file-key-private", "file"))
                .thenReturn(CompletableFuture.completedFuture(new byte[]{1, 2, 3}));
        when(assets.store(any())).thenReturn(new ChannelAttachmentAssetPort.StoredAttachment(
                "object://channel/33333333-3333-3333-3333-333333333333", "c".repeat(64), 3));

        var result = driver.downloadAttachments(configuration(), channel, "message-1", List.of(resource));

        assertEquals(1, result.size());
        assertEquals("message-1:0", result.get(0).attachmentId());
        assertEquals("object://channel/33333333-3333-3333-3333-333333333333", result.get(0).contentRef());
        assertEquals("c".repeat(64), result.get(0).contentHash());
        assertEquals("Shared attachment: feishu-file-1.bin", driver.attachmentSummary(result));
        verify(assets).store(any());
    }

    @Test
    void rejectsDownloadedResourceOverCommonChannelLimit() {
        ChannelAttachmentAssetPort assets = mock(ChannelAttachmentAssetPort.class);
        OpsFeishuChannelConnectionDriver driver = driver(assets);
        LarkChannel channel = mock(LarkChannel.class);
        ResourceDescriptor resource = mock(ResourceDescriptor.class);
        when(resource.getFileKey()).thenReturn("file-key-large");
        when(resource.getType()).thenReturn("file");
        when(channel.downloadResource("file-key-large", "file"))
                .thenReturn(CompletableFuture.completedFuture(new byte[50 * 1024 * 1024 + 1]));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> driver.downloadAttachments(configuration(), channel, "message-1", List.of(resource)));

        assertEquals("CHANNEL_ATTACHMENT_SIZE_OUT_OF_RANGE", failure.getMessage());
    }

    private OpsFeishuChannelConnectionDriver driver(ChannelAttachmentAssetPort assets) {
        return new OpsFeishuChannelConnectionDriver(
                mock(OpsSecretResolver.class),
                mock(ReceiveChannelMessageUseCase.class),
                mock(OpsChannelInteractiveActionDispatcher.class),
                assets);
    }

    private OpsFeishuChannelConfiguration configuration() {
        return new OpsFeishuChannelConfiguration(
                "channel-feishu", "project-1", "credential-ref", "cli-test",
                ChannelConnectionMode.LONG_CONNECTION, true, false, "", "");
    }
}
