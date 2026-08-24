package cn.lgs.orbisops.application.skill;

import java.util.List;

/** Read-navigation hints only: no model judgment, method, approval or task completion. */
public interface SkillEvidenceReadProgressPort {
    List<String> read(String key, String sourceHash);
    void append(String key, String sourceHash, List<String> references);

    SkillEvidenceReadProgressPort NONE = new SkillEvidenceReadProgressPort() {
        public List<String> read(String key, String sourceHash) { return List.of(); }
        public void append(String key, String sourceHash, List<String> references) { }
    };
}
