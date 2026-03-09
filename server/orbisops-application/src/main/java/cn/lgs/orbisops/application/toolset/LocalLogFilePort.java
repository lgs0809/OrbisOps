package cn.lgs.orbisops.application.toolset;

import java.util.List;

/** Infrastructure boundary for bounded local log file reads. */
public interface LocalLogFilePort {

    List<String> tail(String file, int limit);

    List<String> search(String file, String pattern, int limit);
}
