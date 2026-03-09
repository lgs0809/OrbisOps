package cn.lgs.orbisops.domain.agent.service;

import cn.lgs.orbisops.domain.agent.model.valobj.AiAgentTaskScheduleVO;

import java.util.List;

/**
 * 智能体执行任务
 * 2025/9/13 16:08
 */
public interface ITaskService {

    List<AiAgentTaskScheduleVO> queryAllValidTaskSchedule();

    List<Long> queryAllInvalidTaskScheduleIds();

}
