package cn.lgs.orbisops.application.resourcehealth;

public interface PrometheusResourceHealthProbePort {

    ResourceHealthCheck probe(String baseUrl, String job, String instance);
}
