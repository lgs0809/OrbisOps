package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.ContextMemorySnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ContextMemoryApplicationFacadeTest {

    @Test
    void delegatesAllTypedUseCasesToSpecializedApplicationServices() {
        ContextMemoryStoreApplicationService store = mock(ContextMemoryStoreApplicationService.class);
        ContextMemoryQueryApplicationService query = mock(ContextMemoryQueryApplicationService.class);
        ContextMemoryAdminApplicationService admin = mock(ContextMemoryAdminApplicationService.class);
        ContextMemorySceneQueryApplicationService scene = mock(ContextMemorySceneQueryApplicationService.class);
        ContextMemoryApplicationFacade facade = new ContextMemoryApplicationFacade(store, query, admin, scene);
        ContextMemoryQuery genericQuery = new ContextMemoryQuery("PROJECT", "demo-project", "", "ACTIVE", 10);
        ContextMemorySceneQuery sceneQuery = new ContextMemorySceneQuery("CHAT", "user-1", "demo-project", 4);
        ContextMemoryMutationCommand command = command();
        ContextMemorySnapshot snapshot = snapshot("ctx-1");
        ColdMemoryItemSnapshot extracted = extracted();
        when(query.search(genericQuery)).thenReturn(List.of(snapshot));
        when(query.require("ctx-1")).thenReturn(snapshot);
        when(admin.create(command)).thenReturn(snapshot);
        when(admin.update("ctx-1", command)).thenReturn(snapshot);
        when(admin.updateStatus("ctx-1", "ARCHIVED")).thenReturn(snapshot);
        when(scene.query(sceneQuery)).thenReturn(List.of(snapshot));

        assertEquals(List.of(snapshot), facade.search(genericQuery));
        assertEquals(snapshot, facade.require("ctx-1"));
        assertEquals(snapshot, facade.create(command));
        assertEquals(snapshot, facade.update("ctx-1", command));
        assertEquals(snapshot, facade.updateStatus("ctx-1", "ARCHIVED"));
        assertEquals(List.of(snapshot), facade.queryScene(sceneQuery));
        facade.saveExtractedItems(List.of(extracted));

        verify(query).search(genericQuery);
        verify(query).require("ctx-1");
        verify(admin).create(command);
        verify(admin).update("ctx-1", command);
        verify(admin).updateStatus("ctx-1", "ARCHIVED");
        verify(scene).query(sceneQuery);
        verify(store).saveExtractedItems(List.of(extracted));
    }

    @Test
    void missingReadServicesKeepListQueriesOpenAndIdentityLookupClosed() {
        ContextMemoryApplicationFacade facade = new ContextMemoryApplicationFacade(
                null,
                null,
                null,
                null);

        assertEquals(List.of(), facade.search(new ContextMemoryQuery("", "", "", "", 10)));
        assertEquals(List.of(), facade.queryScene(new ContextMemorySceneQuery("CHAT", "user", "project", 4)));
        facade.saveExtractedItems(List.of(extracted()));
        assertThrows(IllegalStateException.class, () -> facade.require("ctx-1"));
        assertThrows(IllegalStateException.class, () -> facade.create(command()));
        assertThrows(IllegalStateException.class, () -> facade.update("ctx-1", command()));
        assertThrows(IllegalStateException.class, () -> facade.updateStatus("ctx-1", "ACTIVE"));
    }

    @Test
    void missingOptionalStoreDoesNotAffectOtherDelegates() {
        ContextMemoryQueryApplicationService query = mock(ContextMemoryQueryApplicationService.class);
        ContextMemoryAdminApplicationService admin = mock(ContextMemoryAdminApplicationService.class);
        ContextMemorySceneQueryApplicationService scene = mock(ContextMemorySceneQueryApplicationService.class);
        ContextMemoryApplicationFacade facade = new ContextMemoryApplicationFacade(null, query, admin, scene);

        facade.saveExtractedItems(List.of(extracted()));

        verifyNoInteractions(query, admin, scene);
    }

    private ContextMemoryMutationCommand command() {
        return new ContextMemoryMutationCommand(
                "ctx-1",
                "PROJECT",
                "demo-project",
                "PROJECT_CONTEXT",
                "title",
                "summary",
                "content",
                "[]",
                "ACTIVE",
                true,
                BigDecimal.valueOf(0.8D),
                "manual",
                "session-1",
                "source-hash",
                "user-1");
    }

    private ContextMemorySnapshot snapshot(String memoryId) {
        return new ContextMemorySnapshot(
                null,
                memoryId,
                "PROJECT",
                "demo-project",
                "PROJECT_CONTEXT",
                "title",
                "summary",
                "content",
                "[]",
                "ACTIVE",
                BigDecimal.valueOf(0.8D),
                "manual",
                "session-1",
                "source-hash",
                "user-1",
                "",
                "",
                "");
    }

    private ColdMemoryItemSnapshot extracted() {
        return new ColdMemoryItemSnapshot(
                "session-1",
                "user-1",
                "PROJECT_CONTEXT",
                "content",
                BigDecimal.valueOf(0.8D),
                "[]",
                "user",
                "source-hash",
                java.util.Map.of("projectId", "demo-project"),
                "");
    }
}
