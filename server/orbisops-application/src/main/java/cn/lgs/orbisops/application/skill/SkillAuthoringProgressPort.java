package cn.lgs.orbisops.application.skill;

import java.util.Map;
import java.util.Optional;

/** Review checkpoints on the existing frozen proposal; no additional job or agent. */
public interface SkillAuthoringProgressPort {
    Optional<Map<String,Object>> read(int index,String inputHash);
    Map<String,Object> save(int index,String inputHash,Map<String,Object> review);
}
