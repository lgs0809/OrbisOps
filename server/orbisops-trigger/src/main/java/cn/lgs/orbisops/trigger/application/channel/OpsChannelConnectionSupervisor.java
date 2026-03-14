package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.provider.ChannelConnectionDriver;
import cn.lgs.orbisops.application.channel.provider.ChannelConnectionMode;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderAdapter;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderConfiguration;
import cn.lgs.orbisops.application.channel.provider.ChannelType;
import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reconciles persisted active Channel configuration with provider-owned connection drivers.
 * It never owns WebSocket threads, parsing, retries, or business routing.
 */
@Component
public final class OpsChannelConnectionSupervisor {

    private final IChannelRepository repository;
    private final Map<ChannelType, ChannelProviderAdapter<?>> adapters;
    private final Map<ChannelType, ChannelConnectionDriver<?>> drivers;
    private final Map<String, ChannelType> managed = new ConcurrentHashMap<>();

    public OpsChannelConnectionSupervisor(IChannelRepository repository,
                                          List<ChannelProviderAdapter<?>> adapters,
                                          List<ChannelConnectionDriver<?>> drivers) {
        if (repository == null) throw new IllegalArgumentException("CHANNEL_REPOSITORY_REQUIRED");
        this.repository = repository;
        this.adapters = indexAdapters(adapters);
        this.drivers = indexDrivers(drivers);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        reconcile();
    }

    @Scheduled(fixedDelayString = "${orbisops.channel.connection.reconcile-ms:30000}")
    public void reconcile() {
        List<ChannelRecord> records;
        try {
            records = repository.findAll();
        } catch (RuntimeException unavailable) {
            return;
        }
        Set<String> desired = new HashSet<>();
        for (ChannelRecord channel : records) {
            if (channel == null || channel.status() != ChannelStatus.ACTIVE) continue;
            ChannelType type;
            try {
                type = ChannelType.parse(channel.channelType());
            } catch (RuntimeException unsupported) {
                continue;
            }
            ChannelProviderAdapter<?> adapter = adapters.get(type);
            ChannelConnectionDriver<?> driver = drivers.get(type);
            if (adapter == null || driver == null) continue;
            try {
                ChannelProviderConfiguration configuration = configuration(adapter, channel);
                if (!persistent(configuration.connectionMode())) continue;
                desired.add(channel.channelId());
                start(driver, configuration);
                managed.put(channel.channelId(), type);
            } catch (RuntimeException providerFailure) {
                // Provider-specific failures are reflected by driver/preflight health and never stop global reconciliation.
            }
        }
        for (Map.Entry<String, ChannelType> entry : Map.copyOf(managed).entrySet()) {
            if (desired.contains(entry.getKey())) continue;
            ChannelConnectionDriver<?> driver = drivers.get(entry.getValue());
            try {
                if (driver != null) driver.stop(entry.getKey());
            } catch (RuntimeException ignored) {
                // Another provider must continue reconciling even if shutdown of one connection fails.
            } finally {
                managed.remove(entry.getKey());
            }
        }
    }

    private boolean persistent(ChannelConnectionMode mode) {
        return mode == ChannelConnectionMode.LONG_CONNECTION || mode == ChannelConnectionMode.HYBRID;
    }

    private Map<ChannelType, ChannelProviderAdapter<?>> indexAdapters(List<ChannelProviderAdapter<?>> values) {
        Map<ChannelType, ChannelProviderAdapter<?>> result = new HashMap<>();
        if (values != null) values.forEach(value -> result.put(value.type(), value));
        return Map.copyOf(result);
    }

    private Map<ChannelType, ChannelConnectionDriver<?>> indexDrivers(List<ChannelConnectionDriver<?>> values) {
        Map<ChannelType, ChannelConnectionDriver<?>> result = new HashMap<>();
        if (values != null) values.forEach(value -> result.put(value.type(), value));
        return Map.copyOf(result);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ChannelProviderConfiguration configuration(ChannelProviderAdapter adapter, ChannelRecord channel) {
        ChannelProviderConfiguration configuration = (ChannelProviderConfiguration) adapter.configuration(channel);
        adapter.validateConfiguration(configuration);
        return configuration;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void start(ChannelConnectionDriver driver, ChannelProviderConfiguration configuration) {
        driver.start(configuration);
    }
}
