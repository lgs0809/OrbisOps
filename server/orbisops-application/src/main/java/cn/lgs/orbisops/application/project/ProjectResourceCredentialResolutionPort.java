package cn.lgs.orbisops.application.project;

import java.util.Map;

/** Resolves stored project-resource credential references only at runtime. */
public interface ProjectResourceCredentialResolutionPort {

    Map<String, Object> resolve(Map<String, Object> storedCredential);
}
