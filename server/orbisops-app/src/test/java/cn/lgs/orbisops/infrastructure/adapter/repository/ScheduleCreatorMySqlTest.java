package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.infrastructure.dao.IAiAgentTaskScheduleDao;
import cn.lgs.orbisops.infrastructure.dao.po.AiAgentTaskSchedule;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class ScheduleCreatorMySqlTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("schedule_creator").withUsername("fixture").withPassword("fixture");
    @Test void mapperRoundTripPreservesCreatorAcrossEditsAndReloads() throws Exception {
        var ds = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE ai_agent_task_schedule(id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id VARCHAR(128),created_by VARCHAR(128),agent_id VARCHAR(128),task_name VARCHAR(128),description VARCHAR(255),cron_expression VARCHAR(64),task_param TEXT,status INT,create_time DATETIME,update_time DATETIME)");
        var configuration = new Configuration(new Environment("test",new JdbcTransactionFactory(),ds));
        String resource="mybatis/mapper/ai_agent_task_schedule_mapper.xml";
        try(var input=getClass().getClassLoader().getResourceAsStream(resource)) {
            new XMLMapperBuilder(input,configuration,resource,configuration.getSqlFragments()).parse();
        }
        var factory=new SqlSessionFactoryBuilder().build(configuration);
        long id;
        try(var session=factory.openSession(true)) {
            var dao=session.getMapper(IAiAgentTaskScheduleDao.class);
            var row=AiAgentTaskSchedule.builder().projectId("project-a").createdBy("creator-a")
                    .agentId("workflow").taskName("synthetic").cronExpression("0 0 0 * * ?")
                    .taskParam("{}").status(0).createTime(LocalDateTime.now()).updateTime(LocalDateTime.now()).build();
            assertEquals(1,dao.insert(row)); id=row.getId();
            row.setCreatedBy("editor-b"); row.setTaskName("edited");
            assertEquals(1,dao.updateById(row));
        }
        try(var session=factory.openSession(true)) {
            var dao=session.getMapper(IAiAgentTaskScheduleDao.class);
            assertEquals("creator-a",dao.queryById(id).getCreatedBy());
            assertEquals("edited",dao.queryByProjectId("project-a").get(0).getTaskName());
            assertTrue(dao.queryByProjectId("project-b").isEmpty());
        }
    }
}
