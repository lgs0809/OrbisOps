package cn.lgs.orbisops.domain.repair;

import cn.lgs.orbisops.domain.repair.model.ControlledCodeEffect;
import cn.lgs.orbisops.domain.repair.service.ControlledCodePolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlledCodePolicyTest {

    private final ControlledCodePolicy policy = new ControlledCodePolicy();

    @Test
    void normalizesPathsLimitsWriterAndSecretMasking() {
        assertEquals("src/App.java", policy.readablePath("src/./App.java"));
        assertEquals(1, policy.readStart(-1));
        assertEquals(1000, policy.readLimit(5000));
        assertEquals(200, policy.grepLimit(500));
        assertEquals(500, policy.globLimit(900));
        assertEquals(1000, policy.timeoutMs(1));
        assertEquals("run:run-1", policy.writerIdentity("run-1", "alice"));
        assertEquals("actor:alice", policy.writerIdentity("", "alice"));
        assertEquals("db:\n  password:***\n  host: 10.0.0.1",
                policy.maskSecrets("db:\n  password: abc123\n  host: 10.0.0.1"));
    }

    @Test
    void rejectsEscapingGitSensitiveAndOversizedWrites() {
        assertThrows(SecurityException.class, () -> policy.readablePath("../secret"));
        assertThrows(SecurityException.class, () -> policy.readablePath(".git/config"));
        assertThrows(SecurityException.class, () -> policy.readablePath("id_rsa"));
        assertThrows(SecurityException.class, () -> policy.writablePath(".env.production"));
        assertThrows(IllegalArgumentException.class,
                () -> policy.content("x".repeat(ControlledCodePolicy.MAX_FILE_BYTES + 1)));
    }

    @Test
    void allowsOnlyEffectBoundCommandGrammar() {
        assertEquals(List.of("pwd"), policy.command("pwd", ControlledCodeEffect.READ_ONLY, ""));
        assertEquals(List.of("mvn", "test"),
                policy.command("mvn test", ControlledCodeEffect.TEST_OR_BUILD, "repair-1"));
        assertEquals(List.of("git", "status"),
                policy.command("git status", ControlledCodeEffect.READ_ONLY, ""));
        assertEquals(List.of("git", "commit", "-m", "fix"),
                policy.command("git commit -m fix", ControlledCodeEffect.WRITE_REPAIR_WORKSPACE, "repair-1"));

        assertThrows(SecurityException.class,
                () -> policy.command("git push origin main", ControlledCodeEffect.WRITE_REPAIR_WORKSPACE, "repair-1"));
        assertThrows(SecurityException.class,
                () -> policy.command("mvn deploy", ControlledCodeEffect.TEST_OR_BUILD, "repair-1"));
        assertThrows(SecurityException.class,
                () -> policy.command("cat a | grep x", ControlledCodeEffect.READ_ONLY, ""));
        assertThrows(SecurityException.class,
                () -> policy.command("find . -delete", ControlledCodeEffect.READ_ONLY, ""));
        assertThrows(SecurityException.class,
                () -> policy.command("rg --pre helper secret", ControlledCodeEffect.READ_ONLY, ""));
        assertThrows(SecurityException.class,
                () -> policy.command("sed -i s/a/b/ App.java", ControlledCodeEffect.READ_ONLY, ""));
        assertThrows(SecurityException.class,
                () -> policy.command("mvn test", ControlledCodeEffect.TEST_OR_BUILD, ""));
    }

    @Test
    void readBeforeWriteAndProofConditionsAreFailClosed() {
        String content = "hello";
        policy.requireReadBeforeWrite(policy.sha256(content), content);
        assertThrows(SecurityException.class,
                () -> policy.requireReadBeforeWrite(policy.sha256("old"), content));
        assertEquals(2, policy.occurrences("one one", "one"));
        assertTrue(policy.proofRequired(
                ControlledCodeEffect.TEST_OR_BUILD, 0, "cp-1", 1, "hash-1"));
        assertTrue(policy.proofRequired(
                ControlledCodeEffect.VERIFY_STEP, 0, "cp-1", 1, "hash-1"));
        assertFalse(policy.proofRequired(
                ControlledCodeEffect.TEST_OR_BUILD, 1, "cp-1", 1, "hash-1"));
    }
}
