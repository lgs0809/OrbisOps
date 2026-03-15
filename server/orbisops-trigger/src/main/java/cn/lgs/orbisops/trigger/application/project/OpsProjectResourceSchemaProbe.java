package cn.lgs.orbisops.trigger.application.project;

import java.util.Map;

/** Protocol-specific read-only schema discovery for a project resource. */
public interface OpsProjectResourceSchemaProbe {

    boolean supports(String resourceType);

    Map<String, Object> scan(String resourceType,
                             String endpoint,
                             Map<String, Object> credential) throws Exception;
}
