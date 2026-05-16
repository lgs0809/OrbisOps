package cn.lgs.orbisops;

import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@Configurable
@EnableTransactionManagement
@EnableScheduling
public class OrbisOpsApplication {

    public static void main(String[] args){
        SpringApplication.run(OrbisOpsApplication.class, args);
    }

}
