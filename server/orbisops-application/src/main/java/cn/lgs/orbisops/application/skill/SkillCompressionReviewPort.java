package cn.lgs.orbisops.application.skill;

import java.util.Map;

public interface SkillCompressionReviewPort {
    /** At most one bounded proposal and a separate review; neither call grants capabilities. */
    Map<String,Object> propose(Map<String,Object> version, java.util.List<Map<String,Object>> artifacts);
    Map<String,Object> review(Map<String,Object> version, java.util.List<Map<String,Object>> artifacts,
                             Map<String,String> changedFiles);
}
