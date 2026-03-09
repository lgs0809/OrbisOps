package cn.lgs.orbisops.application.resourcehealth;

public interface ElasticsearchResourceHealthProbePort {

    ResourceHealthCheck probe(String baseUrl, String index);
}
