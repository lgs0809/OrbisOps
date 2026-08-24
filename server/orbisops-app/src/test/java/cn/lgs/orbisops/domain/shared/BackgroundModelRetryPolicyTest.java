package cn.lgs.orbisops.domain.shared;

import cn.lgs.orbisops.domain.shared.service.BackgroundModelRetryPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BackgroundModelRetryPolicyTest {
    @Test void retryIsStableJitteredAndCappedWithoutAnAttemptQuota() {
        long previous=0;
        for(int attempt=1;attempt<=40;attempt++) {
            long delay=BackgroundModelRetryPolicy.delayMillis("same-job",attempt,30_000,0);
            assertTrue(delay>=30_000 && delay<=3_600_000);
            assertEquals(delay,BackgroundModelRetryPolicy.delayMillis("same-job",attempt,30_000,0));
            assertTrue(delay>=previous);previous=delay;
        }
        assertEquals(3_600_000,BackgroundModelRetryPolicy.delayMillis("same-job",Integer.MAX_VALUE,30_000,0));
        assertNotEquals(BackgroundModelRetryPolicy.delayMillis("a",1,30_000,0),BackgroundModelRetryPolicy.delayMillis("b",1,30_000,0));
    }
    @Test void providerCooldownOnlyExtendsTheLocalDelay() {
        assertEquals(7_200_000,BackgroundModelRetryPolicy.delayMillis("limited",20,30_000,7_200_000));
        assertTrue(BackgroundModelRetryPolicy.delayMillis("limited",3,30_000,1)>=120_000);
    }
}
