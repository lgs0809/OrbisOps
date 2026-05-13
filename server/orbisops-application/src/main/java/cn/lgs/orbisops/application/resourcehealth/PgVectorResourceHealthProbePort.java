package cn.lgs.orbisops.application.resourcehealth;

public interface PgVectorResourceHealthProbePort {

    ResourceHealthCheck probe(String tableName);
}
