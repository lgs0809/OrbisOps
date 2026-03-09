package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsWorkSessionRuntimeConfigurationTest {

    @Test
    void lifecycleCoordinatorUsesSingleRuntimeAssemblyConstructor() {
        Constructor<?>[] constructors = OpsWorkSessionLifecycleCoordinator.class.getDeclaredConstructors();

        assertEquals(1, constructors.length);
        assertArrayEquals(
                new Class<?>[]{OpsWorkSessionRuntimeAssembly.class},
                constructors[0].getParameterTypes());
    }

    @Test
    void lifecycleCoordinatorDoesNotUseFieldInjectionOrConstructInternalRuntimeGraph() {
        assertFalse(OpsWorkSessionLifecycleCoordinator.class.isAnnotationPresent(Component.class));
        assertFalse(Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredFields())
                .anyMatch(field -> field.isAnnotationPresent(Autowired.class)));

        Set<String> methodNames = Arrays.stream(
                        OpsWorkSessionLifecycleCoordinator.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        assertFalse(methodNames.contains("graphNodeExecutionHooks"));
        assertFalse(methodNames.contains("analysisNodeExecutionHooks"));

        Set<String> fieldNames = Arrays.stream(
                        OpsWorkSessionLifecycleCoordinator.class.getDeclaredFields())
                .map(field -> field.getName())
                .collect(Collectors.toSet());
        assertFalse(fieldNames.contains("resourceAssembler"));
        assertFalse(fieldNames.contains("promptAssembler"));
        assertFalse(fieldNames.contains("llmInvoker"));
        assertFalse(fieldNames.contains("graphCompilerAdapter"));
        assertFalse(fieldNames.contains("nodeExecutionStrategyRegistry"));
        assertFalse(fieldNames.contains("toolLoopCoordinator"));
    }

    @Test
    void springConfigurationOwnsRuntimeAssemblyBean() {
        Configuration configuration = OpsWorkSessionRuntimeConfiguration.class
                .getAnnotation(Configuration.class);
        assertNotNull(configuration);
        assertFalse(configuration.proxyBeanMethods());

        Method assemblyMethod = Arrays.stream(
                        OpsWorkSessionRuntimeConfiguration.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Bean.class))
                .filter(method -> OpsWorkSessionRuntimeAssembly.class.equals(method.getReturnType()))
                .findFirst()
                .orElseThrow();

        assertEquals("opsWorkSessionRuntimeAssembly", assemblyMethod.getName());
        assertTrue(assemblyMethod.getParameterCount() > 20);

        Method coordinatorMethod = Arrays.stream(
                        OpsWorkSessionRuntimeConfiguration.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Bean.class))
                .filter(method -> OpsWorkSessionLifecycleCoordinator.class.equals(method.getReturnType()))
                .findFirst()
                .orElseThrow();
        assertEquals("opsWorkSessionLifecycleCoordinator", coordinatorMethod.getName());
        assertArrayEquals(
                new Class<?>[]{OpsWorkSessionRuntimeAssembly.class},
                coordinatorMethod.getParameterTypes());
    }
}
