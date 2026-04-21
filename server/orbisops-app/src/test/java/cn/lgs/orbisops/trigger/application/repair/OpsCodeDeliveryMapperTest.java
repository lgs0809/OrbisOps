package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.api.dto.OpsCodeDeliveryRequestDTO;
import cn.lgs.orbisops.domain.repair.model.CodeDelivery;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryCapabilities;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsCodeDeliveryMapperTest {

    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
    private final OpsCodeDeliveryMapper mapper = new OpsCodeDeliveryMapper();

    @Test
    void mapsRequestEntityListAndCapabilities() {
        OpsCodeDeliveryRequestDTO request = OpsCodeDeliveryRequestDTO.builder()
                .mode("GITHUB_PR").title("review").baseBranch("main").build();
        CodeDelivery delivery = new CodeDelivery(
                "delivery-1", "repair-1", "project-1", "service-1", CodeDeliveryMode.GITHUB_PR,
                "ops-repair/service-1/repair-1", COMMIT, "https://github.test/pr/1",
                "QUEUED", "", "alice", "now", "now");

        assertEquals(CodeDeliveryMode.GITHUB_PR, mapper.candidate(request).mode());
        assertEquals(CodeDeliveryMode.LOCAL_BRANCH, mapper.candidate(null).mode());
        assertEquals("delivery-1", mapper.view(delivery).getDeliveryId());
        assertEquals(1, mapper.views(List.of(delivery)).size());
        assertTrue(mapper.views(null).isEmpty());
        assertEquals(true, mapper.capabilities(
                new CodeDeliveryCapabilities(true, true, true, false)).get("githubPullRequest"));
    }
}
