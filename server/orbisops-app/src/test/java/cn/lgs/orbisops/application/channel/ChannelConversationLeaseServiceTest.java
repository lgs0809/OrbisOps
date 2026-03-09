package cn.lgs.orbisops.application.channel;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelConversationLeaseServiceTest {

    @Test
    void lostLeaseMakesTerminalWritesExplicitAndReleaseIdempotent() {
        ChannelConversationLeasePort port = acquirablePort();
        when(port.renew(anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(false);
        ChannelConversationLeaseService service = new ChannelConversationLeaseService(port);
        ChannelConversationLeaseService.LeaseHandle handle = acquire(service);

        assertEquals(1, service.renewActive(Duration.ofMinutes(1)).size());
        assertFalse(service.complete(handle, "COMPLETED", "run-1", "session-1"));
        assertFalse(service.fail(handle, "failed"));
        service.release(handle);
        service.release(handle);

        verify(port, times(1)).releaseConversation("channel-1", "conversation-1", "sender-1", handle.lockToken());
        verify(port, never()).complete(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(port, never()).fail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void renewedLeaseCanCompleteThroughOriginalHandle() {
        ChannelConversationLeasePort port = acquirablePort();
        when(port.renew(anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(true);
        when(port.complete(anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        ChannelConversationLeaseService service = new ChannelConversationLeaseService(port);
        ChannelConversationLeaseService.LeaseHandle handle = acquire(service);

        assertTrue(service.renewActive(Duration.ofMinutes(1)).isEmpty());
        assertTrue(service.complete(handle, "COMPLETED", "run-1", "session-1"));
        service.release(handle);
        service.release(handle);

        verify(port, times(1)).releaseConversation("channel-1", "conversation-1", "sender-1", handle.lockToken());
    }

    private ChannelConversationLeasePort acquirablePort() {
        ChannelConversationLeasePort port = mock(ChannelConversationLeasePort.class);
        when(port.tryAcquireConversation(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(true);
        when(port.isOldestUnfinishedInbound(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        when(port.tryAcquireInbound(anyString(), anyString(), anyString(), any())).thenReturn(true);
        return port;
    }

    private ChannelConversationLeaseService.LeaseHandle acquire(ChannelConversationLeaseService service) {
        Optional<ChannelConversationLeaseService.LeaseHandle> handle = service.acquire(
                new ChannelConversationLeaseService.AcquireRequest(
                        "project-1", "channel-1", "conversation-1", "sender-1", "message-1"),
                Duration.ofMinutes(1));
        return handle.orElseThrow();
    }
}
