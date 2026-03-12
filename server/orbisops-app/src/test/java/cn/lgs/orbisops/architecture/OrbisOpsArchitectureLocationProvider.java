package cn.lgs.orbisops.architecture;

import cn.lgs.orbisops.OrbisOpsApplication;
import cn.lgs.orbisops.api.dto.AdminUserResponseDTO;
import cn.lgs.orbisops.application.channel.provider.ChannelProviderAdapter;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.infrastructure.adapter.repository.OpsChannelRepository;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionLifecycleCoordinator;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.LocationProvider;

import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Imports every OrbisOps reactor module from the code source that actually backs
 * the current test JVM. This keeps architecture tests reproducible after a clean
 * build instead of depending on sibling modules having been packaged previously.
 */
public final class OrbisOpsArchitectureLocationProvider implements LocationProvider {

    private static final Class<?>[] MODULE_MARKERS = {
            OrbisOpsApplication.class,
            ExecutionBinding.class,
            ChannelRecord.class,
            ChannelProviderAdapter.class,
            OpsChannelRepository.class,
            AdminUserResponseDTO.class,
            OpsWorkSessionLifecycleCoordinator.class
    };

    @Override
    public Set<Location> get(Class<?> testClass) {
        Set<Location> locations = new LinkedHashSet<>();
        for (Class<?> marker : MODULE_MARKERS) {
            URL codeSource = marker.getProtectionDomain().getCodeSource().getLocation();
            locations.add(Location.of(codeSource));
        }
        return Set.copyOf(locations);
    }
}
