package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.provider.ChannelConnectionDriver;
import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderAdapter;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderConfiguration;
import cn.lgs.orbisops.application.channel.provider.ChannelType;
import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelAccessPolicy;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChannelConnectionSupervisorTest {

    private IChannelRepository repository;
    private ChannelProviderAdapter adapter;
    private ChannelConnectionDriver driver;
    private ChannelProviderConfiguration configuration;
    private OpsChannelConnectionSupervisor supervisor;

    @BeforeEach
    void setUp() {
        repository = mock(IChannelRepository.class);
        adapter = mock(ChannelProviderAdapter.class);
        driver = mock(ChannelConnectionDriver.class);
        configuration = mock(ChannelProviderConfiguration.class);
        when(adapter.type()).thenReturn(ChannelType.FEISHU);
        when(driver.type()).thenReturn(ChannelType.FEISHU);
        when(adapter.configuration(any())).thenReturn(configuration);
        when(configuration.connectionMode()).thenReturn(ChannelConnectionMode.LONG_CONNECTION);
        supervisor = new OpsChannelConnectionSupervisor(repository, List.of(adapter), List.of(driver));
    }

    @Test
    void startsPersistedActiveLongConnectionAndStopsItWhenNoLongerDesired() {
        when(repository.findAll()).thenReturn(List.of(channel("channel-1")), List.of());

        supervisor.reconcile();
        supervisor.reconcile();

        verify(driver).start(configuration);
        verify(driver).stop("channel-1");
    }

    @Test
    void restartsManagedConnectionWhenProviderConfigurationChanges() {
        ChannelProviderConfiguration first = mock(ChannelProviderConfiguration.class);
        ChannelProviderConfiguration second = mock(ChannelProviderConfiguration.class);
        when(first.connectionMode()).thenReturn(ChannelConnectionMode.LONG_CONNECTION);
        when(second.connectionMode()).thenReturn(ChannelConnectionMode.LONG_CONNECTION);
        when(adapter.configuration(any())).thenReturn(first, second);
        when(repository.findAll()).thenReturn(List.of(channel("channel-1")));

        supervisor.reconcile();
        supervisor.reconcile();

        verify(driver).start(first);
        verify(driver).stop("channel-1");
        verify(driver).start(second);
    }

    @Test
    void unchangedConfigurationIsNotRestartedOnEveryReconcile() {
        when(repository.findAll()).thenReturn(List.of(channel("channel-1")));

        supervisor.reconcile();
        supervisor.reconcile();

        verify(driver, times(1)).start(configuration);
        verify(driver, times(0)).stop("channel-1");
    }

    @Test
    void shutdownStopsManagedConnectionsAndReleasesProviderTransportResources() {
        when(repository.findAll()).thenReturn(List.of(channel("channel-1")));

        supervisor.reconcile();
        supervisor.shutdown();

        verify(driver).stop("channel-1");
        verify(driver).shutdown();
    }

    @Test
    void providerFailureDoesNotPreventOtherChannelsFromReconciling() {
        ChannelProviderConfiguration first = mock(ChannelProviderConfiguration.class);
        ChannelProviderConfiguration second = mock(ChannelProviderConfiguration.class);
        when(first.connectionMode()).thenReturn(ChannelConnectionMode.LONG_CONNECTION);
        when(second.connectionMode()).thenReturn(ChannelConnectionMode.LONG_CONNECTION);
        when(adapter.configuration(any())).thenAnswer(invocation -> {
            ChannelRecord channel = invocation.getArgument(0);
            return "channel-1".equals(channel.channelId()) ? first : second;
        });
        doThrow(new IllegalStateException("provider-1 failed")).when(driver).start(first);
        when(repository.findAll()).thenReturn(List.of(channel("channel-1"), channel("channel-2")));

        supervisor.reconcile();

        verify(driver).start(first);
        verify(driver).start(second);
    }

    @Test
    void ignoresDisabledAndNonPersistentChannels() {
        ChannelRecord disabled = new ChannelRecord("disabled", "project-1", ExecutionBinding.react(), "Disabled",
                "FEISHU", "credential-ref", Map.of(), ChannelAccessPolicy.DENY_UNKNOWN, ChannelStatus.DISABLED,
                "admin", null, null);
        ChannelProviderConfiguration webhook = mock(ChannelProviderConfiguration.class);
        when(webhook.connectionMode()).thenReturn(ChannelConnectionMode.WEBHOOK);
        when(adapter.configuration(any())).thenReturn(webhook);
        when(repository.findAll()).thenReturn(List.of(disabled, channel("channel-1")));

        supervisor.reconcile();

        verify(driver, times(0)).start(any());
    }

    private ChannelRecord channel(String channelId) {
        return new ChannelRecord(channelId, "project-1", ExecutionBinding.react(), channelId,
                "FEISHU", "credential-ref", Map.of(), ChannelAccessPolicy.DENY_UNKNOWN, ChannelStatus.ACTIVE,
                "admin", null, null);
    }
}
