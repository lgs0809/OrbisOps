package cn.lgs.orbisops.domain.agent.adapter.repository;

import cn.lgs.orbisops.domain.agent.model.valobj.AiClientApiHealthCheckRecord;

public interface IAiClientApiHealthCheckRepository {

    /**
     * Records probe history. A false result means history storage was unavailable;
     * it does not rewrite the independently observed probe result.
     */
    boolean save(AiClientApiHealthCheckRecord record);
}
