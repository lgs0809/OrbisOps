package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;

/** Maintains execution ownership independently of slow model work. Persistence still fences every write. */
public interface SkillEvolutionLeasePort {
    Scope maintain(SkillEvolutionJobSnapshot claim);
    interface Scope extends AutoCloseable {
        void requireOwned();
        @Override void close();
    }
    static SkillEvolutionLeasePort unmanaged() {
        return claim -> new Scope() { public void requireOwned() { } public void close() { } };
    }
}
