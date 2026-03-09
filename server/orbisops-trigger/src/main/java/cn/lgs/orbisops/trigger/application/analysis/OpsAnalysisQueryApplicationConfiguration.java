package cn.lgs.orbisops.trigger.application.analysis;

import cn.lgs.orbisops.application.analysis.MySqlSlowSqlQueryApplicationService;
import cn.lgs.orbisops.application.analysis.MySqlSlowSqlQueryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition root for typed operational analysis query services. */
@Configuration
public class OpsAnalysisQueryApplicationConfiguration {

    @Bean
    public MySqlSlowSqlQueryApplicationService mySqlSlowSqlQueryApplicationService(
            MySqlSlowSqlQueryPort queryPort) {
        return new MySqlSlowSqlQueryApplicationService(queryPort);
    }
}
