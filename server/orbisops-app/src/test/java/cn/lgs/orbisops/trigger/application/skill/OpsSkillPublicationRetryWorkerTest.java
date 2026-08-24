package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpsSkillPublicationRetryWorkerTest {
    @Test void modelTimeoutIsPersistedForLaterRetryWithoutRepeatingAuthoring() {
        var retries=mock(SkillPublicationRetryPort.class);var releases=mock(SkillReleaseApplicationService.class);
        var claim=new SkillPublicationRetryPort.Claim("retained-candidate","token",1);
        when(retries.claim()).thenReturn(Optional.of(claim));
        when(releases.resumeRetained(eq("retained-candidate"),any())).thenThrow(new SkillContentReviewUnavailableException(new java.net.SocketTimeoutException()));
        var worker=new OpsSkillPublicationRetryWorker(retries,releases);worker.runOnce();worker.stop();
        verify(retries).defer(claim,"SKILL_CONTENT_REVIEW_UNAVAILABLE");verify(retries,never()).complete(any(),any());
    }
    @Test void databaseOutageDoesNotKillTheNextWorkerTick() {
        var retries=mock(SkillPublicationRetryPort.class);var releases=mock(SkillReleaseApplicationService.class);
        when(retries.claim()).thenThrow(new IllegalStateException("db down")).thenReturn(Optional.empty());
        var worker=new OpsSkillPublicationRetryWorker(retries,releases);
        assertDoesNotThrow(worker::runOnce);assertDoesNotThrow(worker::runOnce);worker.stop();
        verify(retries,times(2)).claim();verifyNoInteractions(releases);
    }
}
