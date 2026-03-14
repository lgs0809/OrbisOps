package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.channel.ChannelRuntimeReadPort;
import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelAccessPolicy;
import cn.lgs.orbisops.domain.channel.model.ChannelIdentityRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelMessageRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStoreStatus;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import cn.lgs.orbisops.types.execution.ExecutionType;
import cn.lgs.orbisops.types.execution.ExecutionVersionPolicy;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class OpsChannelRepository implements IChannelRepository, ChannelRuntimeReadPort {

    private final JdbcTemplate jdbcTemplate;

    public OpsChannelRepository(@Qualifier("mysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    public void ensureSchema() {
        JdbcTemplate template = requiredTemplate();
        template.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_channel (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, channel_id VARCHAR(80) NOT NULL, project_id VARCHAR(128) NOT NULL,
                  execution_kind VARCHAR(16) NOT NULL DEFAULT 'NONE',
                  agent_id VARCHAR(128) NOT NULL DEFAULT '', agent_binding_mode VARCHAR(24) NOT NULL DEFAULT 'LATEST_PUBLISHED',
                  agent_version INT NULL, agent_definition_hash VARCHAR(64) NOT NULL DEFAULT '',
                  name VARCHAR(128) NOT NULL, channel_type VARCHAR(32) NOT NULL,
                  credential_ref VARCHAR(256) NOT NULL DEFAULT '', config_json MEDIUMTEXT NULL,
                  access_policy VARCHAR(32) NOT NULL DEFAULT 'DENY_UNKNOWN', status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
                  created_by VARCHAR(128) NOT NULL DEFAULT '', create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id), UNIQUE KEY uk_ops_channel_id (channel_id), KEY idx_ops_channel_project (project_id,status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent消息渠道配置'
                """);
        ensureColumn("execution_kind", "VARCHAR(16) NOT NULL DEFAULT 'WORKFLOW'");
        ensureColumn("agent_binding_mode", "VARCHAR(24) NOT NULL DEFAULT 'LATEST_PUBLISHED'");
        ensureColumn("agent_version", "INT NULL");
        ensureColumn("agent_definition_hash", "VARCHAR(64) NOT NULL DEFAULT ''");
        ensureColumn("access_policy", "VARCHAR(32) NOT NULL DEFAULT 'DENY_UNKNOWN'");
        template.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_channel_session (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, channel_session_id VARCHAR(80) NOT NULL, channel_id VARCHAR(80) NOT NULL,
                  project_id VARCHAR(128) NOT NULL, agent_id VARCHAR(128) NOT NULL, external_conversation_id VARCHAR(256) NOT NULL,
                  sender_id VARCHAR(256) NOT NULL, session_id VARCHAR(100) NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id), UNIQUE KEY uk_channel_session_id (channel_session_id),
                  UNIQUE KEY uk_channel_external_session (channel_id,external_conversation_id,sender_id), KEY idx_channel_internal_session (session_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道会话映射'
                """);
        template.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_channel_message (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, message_id VARCHAR(80) NOT NULL, channel_id VARCHAR(80) NOT NULL,
                  project_id VARCHAR(128) NOT NULL, external_message_id VARCHAR(256) NOT NULL, external_conversation_id VARCHAR(256) NOT NULL,
                  sender_id VARCHAR(256) NOT NULL, session_id VARCHAR(100) NOT NULL DEFAULT '', run_id VARCHAR(100) NOT NULL DEFAULT '',
                  direction VARCHAR(16) NOT NULL, status VARCHAR(24) NOT NULL, payload_json MEDIUMTEXT NULL, error_message TEXT NULL,
                  processing_token VARCHAR(80) NOT NULL DEFAULT '', lease_expires_at DATETIME(3) NULL,
                  attempt_count INT NOT NULL DEFAULT 0,
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id), UNIQUE KEY uk_channel_message_id (message_id), UNIQUE KEY uk_channel_external_message (channel_id,external_message_id),
                  KEY idx_channel_message_run (project_id,run_id), KEY idx_channel_message_status (status,create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道消息去重和投递状态'
                """);
        ensureMessageColumn("processing_token", "VARCHAR(80) NOT NULL DEFAULT ''");
        ensureMessageColumn("lease_expires_at", "DATETIME(3) NULL");
        ensureMessageColumn("attempt_count", "INT NOT NULL DEFAULT 0");
        template.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_channel_conversation_lease (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  channel_id VARCHAR(80) NOT NULL, external_conversation_id VARCHAR(256) NOT NULL,
                  sender_id VARCHAR(256) NOT NULL, lock_token VARCHAR(80) NOT NULL,
                  lease_expires_at DATETIME(3) NOT NULL,
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_channel_conversation_lease (channel_id,external_conversation_id,sender_id),
                  KEY idx_channel_conversation_lease_expiry (lease_expires_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Channel跨实例会话顺序租约'
                """);
        template.execute("""
                CREATE TABLE IF NOT EXISTS ai_ops_channel_identity (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, mapping_id VARCHAR(80) NOT NULL,
                  channel_id VARCHAR(80) NOT NULL, project_id VARCHAR(128) NOT NULL, external_sender_id VARCHAR(256) NOT NULL,
                  platform_user_id VARCHAR(128) NOT NULL, username VARCHAR(128) NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
                  version BIGINT NOT NULL DEFAULT 1, created_by VARCHAR(128) NOT NULL DEFAULT '',
                  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id), UNIQUE KEY uk_channel_identity_mapping (mapping_id),
                  UNIQUE KEY uk_channel_identity_sender (channel_id,external_sender_id),
                  KEY idx_channel_identity_project (project_id,channel_id,status),
                  KEY idx_channel_identity_user (project_id,platform_user_id,status)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部Channel发送者到平台用户的受控绑定'
                """);
    }

    @Override
    public void create(ChannelRecord channel) {
        ExecutionBinding execution = channel.inboundExecution();
        int inserted = requiredTemplate().update("""
                INSERT INTO ai_ops_channel
                  (channel_id,project_id,execution_kind,agent_id,agent_binding_mode,agent_version,agent_definition_hash,
                   name,channel_type,credential_ref,config_json,access_policy,status,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, channel.channelId(), channel.projectId(), execution.type().name(), execution.workflowId(),
                execution.versionPolicy().name(), execution.version(), execution.definitionHash(), channel.name(),
                channel.channelType(), channel.credentialRef(), encodeConfig(channel.config()), channel.accessPolicy().name(),
                channel.status().name(), channel.createdBy());
        if (inserted != 1) throw new IllegalStateException("CHANNEL_CREATE_FAILED");
    }

    @Override
    public boolean update(ChannelRecord channel) {
        ExecutionBinding execution = channel.inboundExecution();
        return requiredTemplate().update("""
                UPDATE ai_ops_channel SET execution_kind=?,agent_id=?,agent_binding_mode=?,agent_version=?,agent_definition_hash=?,
                  name=?,credential_ref=?,config_json=?,access_policy=?,status=?,update_time=CURRENT_TIMESTAMP
                WHERE project_id=? AND channel_id=?
                """, execution.type().name(), execution.workflowId(), execution.versionPolicy().name(), execution.version(),
                execution.definitionHash(), channel.name(), channel.credentialRef(), encodeConfig(channel.config()),
                channel.accessPolicy().name(), channel.status().name(), channel.projectId(), channel.channelId()) == 1;
    }

    @Override
    public Optional<ChannelRecord> findById(String channelId) {
        List<ChannelRecord> rows = requiredTemplate().query(
                "SELECT * FROM ai_ops_channel WHERE channel_id=? LIMIT 1", this::channel, channelId);
        return rows.stream().findFirst();
    }

    @Override
    public List<ChannelRecord> findAll() {
        return requiredTemplate().query(
                "SELECT * FROM ai_ops_channel ORDER BY id DESC", this::channel);
    }

    @Override
    public List<ChannelRecord> findByProject(String projectId) {
        return requiredTemplate().query(
                "SELECT * FROM ai_ops_channel WHERE project_id=? ORDER BY id DESC", this::channel, projectId);
    }

    @Override
    public boolean insertInbound(ChannelMessageRecord message) {
        return requiredTemplate().update("""
                INSERT IGNORE INTO ai_ops_channel_message
                  (message_id,channel_id,project_id,external_message_id,external_conversation_id,sender_id,direction,status,payload_json)
                VALUES (?,?,?,?,?,?,'INBOUND','RECEIVED',?)
                """, message.messageId(), message.channelId(), message.projectId(), message.externalMessageId(),
                message.externalConversationId(), message.senderId(), message.payloadJson()) == 1;
    }

    @Override
    public Optional<ChannelMessageRecord> findInbound(String channelId, String externalMessageId) {
        return requiredTemplate().query("""
                SELECT message_id,channel_id,project_id,external_message_id,external_conversation_id,sender_id,
                       session_id,run_id,direction,status,payload_json,error_message,create_time,update_time
                FROM ai_ops_channel_message
                WHERE channel_id=? AND external_message_id=? AND direction='INBOUND' LIMIT 1
                """, this::message, channelId, externalMessageId).stream().findFirst();
    }

    @Override
    public List<ChannelMessageRecord> findQueuedInbound(int limit) {
        return requiredTemplate().query("""
                SELECT message_id,channel_id,project_id,external_message_id,external_conversation_id,sender_id,
                       session_id,run_id,direction,status,payload_json,error_message,create_time,update_time
                FROM ai_ops_channel_message
                WHERE direction='INBOUND' AND status='QUEUED'
                ORDER BY id ASC LIMIT ?
                """, this::message, Math.max(1, Math.min(limit, 100)));
    }

    @Override
    public boolean tryAcquireConversationLease(String channelId,
                                               String externalConversationId,
                                               String senderId,
                                               String lockToken,
                                               Instant leaseExpiresAt) {
        JdbcTemplate template = requiredTemplate();
        int updated = template.update("""
                UPDATE ai_ops_channel_conversation_lease
                SET lock_token=?,lease_expires_at=?,update_time=CURRENT_TIMESTAMP
                WHERE channel_id=? AND external_conversation_id=? AND sender_id=?
                  AND lease_expires_at<CURRENT_TIMESTAMP(3)
                """, lockToken, Timestamp.from(leaseExpiresAt), channelId, externalConversationId, senderId);
        if (updated == 1) return true;
        int inserted = template.update("""
                INSERT IGNORE INTO ai_ops_channel_conversation_lease
                  (channel_id,external_conversation_id,sender_id,lock_token,lease_expires_at)
                VALUES (?,?,?,?,?)
                """, channelId, externalConversationId, senderId, lockToken, Timestamp.from(leaseExpiresAt));
        if (inserted == 1) return true;
        Integer owned = template.queryForObject("""
                SELECT COUNT(1) FROM ai_ops_channel_conversation_lease
                WHERE channel_id=? AND external_conversation_id=? AND sender_id=? AND lock_token=?
                  AND lease_expires_at>=CURRENT_TIMESTAMP(3)
                """, Integer.class, channelId, externalConversationId, senderId, lockToken);
        return owned != null && owned == 1;
    }

    @Override
    public void releaseConversationLease(String channelId,
                                         String externalConversationId,
                                         String senderId,
                                         String lockToken) {
        requiredTemplate().update("""
                DELETE FROM ai_ops_channel_conversation_lease
                WHERE channel_id=? AND external_conversation_id=? AND sender_id=? AND lock_token=?
                """, channelId, externalConversationId, senderId, lockToken);
    }

    @Override
    public boolean isOldestUnfinishedInbound(String channelId,
                                             String externalConversationId,
                                             String senderId,
                                             String externalMessageId) {
        Integer count = requiredTemplate().queryForObject("""
                SELECT COUNT(1)
                FROM ai_ops_channel_message current_message
                JOIN ai_ops_channel_message earlier
                  ON earlier.channel_id=current_message.channel_id
                 AND earlier.external_conversation_id=current_message.external_conversation_id
                 AND earlier.sender_id=current_message.sender_id
                 AND earlier.direction='INBOUND'
                 AND earlier.id<current_message.id
                 AND earlier.status IN ('RECEIVED','QUEUED','PROCESSING','RECOVERY_REQUIRED')
                WHERE current_message.channel_id=? AND current_message.external_message_id=?
                  AND current_message.external_conversation_id=? AND current_message.sender_id=?
                """, Integer.class, channelId, externalMessageId, externalConversationId, senderId);
        return count != null && count == 0;
    }

    @Override
    public boolean tryAcquireInboundLease(String channelId,
                                          String externalMessageId,
                                          String lockToken,
                                          Instant leaseExpiresAt) {
        return requiredTemplate().update("""
                UPDATE ai_ops_channel_message
                SET status='PROCESSING',processing_token=?,lease_expires_at=?,attempt_count=attempt_count+1,
                    update_time=CURRENT_TIMESTAMP
                WHERE channel_id=? AND external_message_id=? AND direction='INBOUND' AND status='QUEUED'
                """, lockToken, Timestamp.from(leaseExpiresAt), channelId, externalMessageId) == 1;
    }

    @Override
    public boolean renewInboundLeases(String channelId,
                                      String externalConversationId,
                                      String senderId,
                                      String externalMessageId,
                                      String lockToken,
                                      Instant leaseExpiresAt) {
        int message = requiredTemplate().update("""
                UPDATE ai_ops_channel_message SET lease_expires_at=?,update_time=CURRENT_TIMESTAMP
                WHERE channel_id=? AND external_message_id=? AND direction='INBOUND'
                  AND status='PROCESSING' AND processing_token=?
                """, Timestamp.from(leaseExpiresAt), channelId, externalMessageId, lockToken);
        int conversation = requiredTemplate().update("""
                UPDATE ai_ops_channel_conversation_lease SET lease_expires_at=?,update_time=CURRENT_TIMESTAMP
                WHERE channel_id=? AND external_conversation_id=? AND sender_id=? AND lock_token=?
                """, Timestamp.from(leaseExpiresAt), channelId, externalConversationId, senderId, lockToken);
        return message == 1 && conversation == 1;
    }

    @Override
    public boolean markClaimedInboundCompleted(String channelId,
                                               String externalMessageId,
                                               String lockToken,
                                               String status,
                                               String runId,
                                               String sessionId) {
        return requiredTemplate().update("""
                UPDATE ai_ops_channel_message
                SET status=?,run_id=?,session_id=?,processing_token='',lease_expires_at=NULL,error_message=NULL,
                    update_time=CURRENT_TIMESTAMP
                WHERE channel_id=? AND external_message_id=? AND direction='INBOUND'
                  AND status='PROCESSING' AND processing_token=?
                """, status, runId, sessionId, channelId, externalMessageId, lockToken) == 1;
    }

    @Override
    public boolean markClaimedInboundFailed(String channelId,
                                            String externalMessageId,
                                            String lockToken,
                                            String errorMessage) {
        return requiredTemplate().update("""
                UPDATE ai_ops_channel_message
                SET status='FAILED',error_message=?,processing_token='',lease_expires_at=NULL,update_time=CURRENT_TIMESTAMP
                WHERE channel_id=? AND external_message_id=? AND direction='INBOUND'
                  AND status='PROCESSING' AND processing_token=?
                """, errorMessage, channelId, externalMessageId, lockToken) == 1;
    }

    @Override
    public List<ChannelMessageRecord> quarantineExpiredInboundLeases(int limit) {
        List<ChannelMessageRecord> expired = requiredTemplate().query("""
                SELECT message_id,channel_id,project_id,external_message_id,external_conversation_id,sender_id,
                       session_id,run_id,direction,status,payload_json,error_message,create_time,update_time
                FROM ai_ops_channel_message
                WHERE direction='INBOUND' AND status='PROCESSING' AND lease_expires_at<CURRENT_TIMESTAMP(3)
                ORDER BY id ASC LIMIT ?
                """, this::message, Math.max(1, Math.min(limit, 100)));
        return expired.stream().filter(row -> requiredTemplate().update("""
                UPDATE ai_ops_channel_message
                SET status='RECOVERY_REQUIRED',error_message='CHANNEL_INBOUND_OUTCOME_UNKNOWN',processing_token='',
                    lease_expires_at=NULL,update_time=CURRENT_TIMESTAMP
                WHERE message_id=? AND status='PROCESSING' AND lease_expires_at<CURRENT_TIMESTAMP(3)
                """, row.messageId()) == 1).toList();
    }

    @Override
    public boolean requeueRecoveryRequired(String projectId, String channelId, String externalMessageId) {
        return requiredTemplate().update("""
                UPDATE ai_ops_channel_message
                SET status='QUEUED',processing_token='',lease_expires_at=NULL,error_message=NULL,update_time=CURRENT_TIMESTAMP
                WHERE project_id=? AND channel_id=? AND external_message_id=? AND direction='INBOUND'
                  AND status='RECOVERY_REQUIRED'
                """, projectId, channelId, externalMessageId) == 1;
    }

    @Override
    public boolean cancelRecoveryRequired(String projectId, String channelId, String externalMessageId) {
        return requiredTemplate().update("""
                UPDATE ai_ops_channel_message
                SET status='CANCELLED',processing_token='',lease_expires_at=NULL,update_time=CURRENT_TIMESTAMP
                WHERE project_id=? AND channel_id=? AND external_message_id=? AND direction='INBOUND'
                  AND status='RECOVERY_REQUIRED'
                """, projectId, channelId, externalMessageId) == 1;
    }

    @Override
    public void markInboundQueued(String channelId, String externalMessageId, String runId, String sessionId) {
        updateInbound(channelId, externalMessageId, "QUEUED", runId, sessionId);
    }

    @Override
    public void markInboundCompleted(String channelId, String externalMessageId, String status, String runId, String sessionId) {
        updateInbound(channelId, externalMessageId, status, runId, sessionId);
    }

    private void updateInbound(String channelId, String externalMessageId, String status, String runId, String sessionId) {
        int updated = requiredTemplate().update("""
                UPDATE ai_ops_channel_message SET status=?,run_id=?,session_id=?,update_time=CURRENT_TIMESTAMP
                WHERE channel_id=? AND external_message_id=?
                """, status, runId, sessionId, channelId, externalMessageId);
        if (updated != 1) throw new IllegalStateException("CHANNEL_MESSAGE_STATE_CONFLICT");
    }

    @Override
    public void markInboundFailed(String channelId, String externalMessageId, String errorMessage) {
        int updated = requiredTemplate().update("""
                UPDATE ai_ops_channel_message SET status='FAILED',error_message=?,update_time=CURRENT_TIMESTAMP
                WHERE channel_id=? AND external_message_id=?
                """, errorMessage, channelId, externalMessageId);
        if (updated != 1) throw new IllegalStateException("CHANNEL_MESSAGE_STATE_CONFLICT");
    }

    @Override
    public void insertOutbound(ChannelMessageRecord message) {
        int inserted = requiredTemplate().update("""
                INSERT INTO ai_ops_channel_message
                  (message_id,channel_id,project_id,external_message_id,external_conversation_id,sender_id,session_id,run_id,direction,status,payload_json)
                VALUES (?,?,?,?,?,?,?,?, 'OUTBOUND','SENDING',?)
                """, message.messageId(), message.channelId(), message.projectId(), message.externalMessageId(),
                message.externalConversationId(), message.senderId(), message.sessionId(), message.runId(), message.payloadJson());
        if (inserted != 1) throw new IllegalStateException("CHANNEL_OUTBOUND_CREATE_FAILED");
    }

    @Override
    public void markOutboundCompleted(String messageId, String status, String payloadJson) {
        int updated = requiredTemplate().update("""
                UPDATE ai_ops_channel_message SET status=?,payload_json=?,update_time=CURRENT_TIMESTAMP WHERE message_id=?
                """, status, payloadJson, messageId);
        if (updated != 1) throw new IllegalStateException("CHANNEL_OUTBOUND_STATE_CONFLICT");
    }

    @Override
    public void markOutboundFailed(String messageId, String errorMessage) {
        int updated = requiredTemplate().update("""
                UPDATE ai_ops_channel_message SET status='FAILED',error_message=?,update_time=CURRENT_TIMESTAMP WHERE message_id=?
                """, errorMessage, messageId);
        if (updated != 1) throw new IllegalStateException("CHANNEL_OUTBOUND_STATE_CONFLICT");
    }

    @Override
    public void bindOutboundExternalMessageId(String messageId, String externalMessageId) {
        if (externalMessageId == null || externalMessageId.isBlank()) return;
        int updated = requiredTemplate().update("""
                UPDATE ai_ops_channel_message SET external_message_id=?,update_time=CURRENT_TIMESTAMP
                WHERE message_id=? AND direction='OUTBOUND'
                """, externalMessageId.trim(), messageId);
        if (updated != 1) throw new IllegalStateException("CHANNEL_OUTBOUND_STATE_CONFLICT");
    }

    @Override
    public Optional<ChannelMessageRecord> findLatestOutboundByRun(String projectId,
                                                                  String channelId,
                                                                  String runId,
                                                                  String senderId) {
        return requiredTemplate().query("""
                SELECT message_id,channel_id,project_id,external_message_id,external_conversation_id,sender_id,
                       session_id,run_id,direction,status,payload_json,error_message,create_time,update_time
                FROM ai_ops_channel_message
                WHERE project_id=? AND channel_id=? AND run_id=? AND direction='OUTBOUND' AND sender_id=?
                ORDER BY id DESC LIMIT 1
                """, this::message, projectId, channelId, runId, senderId).stream().findFirst();
    }

    @Override
    public List<ChannelMessageRecord> findMessages(String projectId, String channelId, int limit) {
        return requiredTemplate().query("""
                SELECT message_id,channel_id,project_id,external_message_id,external_conversation_id,sender_id,
                       session_id,run_id,direction,status,payload_json,error_message,create_time,update_time
                FROM ai_ops_channel_message WHERE project_id=? AND channel_id=? ORDER BY id DESC LIMIT ?
                """, this::message, projectId, channelId, Math.max(1, Math.min(limit, 200)));
    }

    @Override
    public String resolveSession(String channelId,
                                 String projectId,
                                 String executionKey,
                                 String externalConversationId,
                                 String senderId,
                                 String candidateSessionId) {
        JdbcTemplate template = requiredTemplate();
        List<String> existing = template.query("""
                SELECT session_id FROM ai_ops_channel_session
                WHERE channel_id=? AND external_conversation_id=? AND sender_id=? LIMIT 1
                """, (resultSet, rowNum) -> resultSet.getString("session_id"), channelId, externalConversationId, senderId);
        if (!existing.isEmpty()) return existing.get(0);
        template.update("""
                INSERT INTO ai_ops_channel_session
                  (channel_session_id,channel_id,project_id,agent_id,external_conversation_id,sender_id,session_id,status)
                VALUES (UUID(),?,?,?,?,?,?,'ACTIVE')
                ON DUPLICATE KEY UPDATE update_time=CURRENT_TIMESTAMP
                """, channelId, projectId, executionKey, externalConversationId, senderId, candidateSessionId);
        return template.queryForObject("""
                SELECT session_id FROM ai_ops_channel_session
                WHERE channel_id=? AND external_conversation_id=? AND sender_id=? LIMIT 1
                """, String.class, channelId, externalConversationId, senderId);
    }

    @Override
    public ChannelStoreStatus status(String projectId) {
        JdbcTemplate template = requiredTemplate();
        return new ChannelStoreStatus(count(template,
                "SELECT COUNT(1) FROM ai_ops_channel WHERE project_id=? AND status='ACTIVE'", projectId), count(template,
                "SELECT COUNT(1) FROM ai_ops_channel_message WHERE project_id=? AND status IN ('FAILED','RECOVERY_REQUIRED')", projectId));
    }

    @Override
    public ChannelStoreStatus statusAll() {
        JdbcTemplate template = requiredTemplate();
        return new ChannelStoreStatus(count(template,
                "SELECT COUNT(1) FROM ai_ops_channel WHERE status='ACTIVE'"), count(template,
                "SELECT COUNT(1) FROM ai_ops_channel_message WHERE status IN ('FAILED','RECOVERY_REQUIRED')"));
    }

    @Override
    public Optional<ChannelIdentityRecord> findIdentity(String channelId, String externalSenderId) {
        return requiredTemplate().query("""
                SELECT * FROM ai_ops_channel_identity WHERE channel_id=? AND external_sender_id=? LIMIT 1
                """, this::identity, channelId, externalSenderId).stream().findFirst();
    }

    @Override
    public List<ChannelIdentityRecord> findIdentities(String projectId, String channelId) {
        return requiredTemplate().query("""
                SELECT * FROM ai_ops_channel_identity WHERE project_id=? AND channel_id=? ORDER BY id DESC
                """, this::identity, projectId, channelId);
    }

    @Override
    public void createIdentity(ChannelIdentityRecord identity) {
        int inserted = requiredTemplate().update("""
                INSERT INTO ai_ops_channel_identity
                  (mapping_id,channel_id,project_id,external_sender_id,platform_user_id,username,status,version,created_by)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, identity.mappingId(), identity.channelId(), identity.projectId(), identity.externalSenderId(),
                identity.platformUserId(), identity.username(), identity.status().name(), identity.version(), identity.createdBy());
        if (inserted != 1) throw new IllegalStateException("CHANNEL_IDENTITY_CREATE_FAILED");
    }

    @Override
    public boolean updateIdentity(ChannelIdentityRecord identity, long expectedVersion) {
        return requiredTemplate().update("""
                UPDATE ai_ops_channel_identity
                SET platform_user_id=?,username=?,status=?,version=version+1,update_time=CURRENT_TIMESTAMP
                WHERE project_id=? AND channel_id=? AND external_sender_id=? AND version=?
                """, identity.platformUserId(), identity.username(), identity.status().name(), identity.projectId(), identity.channelId(),
                identity.externalSenderId(), expectedVersion) == 1;
    }

    private int count(JdbcTemplate template, String sql, Object... arguments) {
        Integer value = template.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private ChannelRecord channel(ResultSet rs, int rowNum) throws SQLException {
        String workflowId = text(rs.getString("agent_id"));
        ExecutionType type = ExecutionType.parse(rs.getString("execution_kind"),
                workflowId.isBlank() ? ExecutionType.NONE : ExecutionType.WORKFLOW);
        ExecutionBinding execution = switch (type) {
            case NONE -> ExecutionBinding.none();
            case REACT -> ExecutionBinding.react();
            case WORKFLOW -> ExecutionBinding.workflow(
                    workflowId,
                    ExecutionVersionPolicy.parse(rs.getString("agent_binding_mode"), ExecutionVersionPolicy.LATEST_PUBLISHED),
                    integer(rs, "agent_version"),
                    rs.getString("agent_definition_hash"));
        };
        return new ChannelRecord(
                rs.getString("channel_id"), rs.getString("project_id"), execution,
                rs.getString("name"), rs.getString("channel_type"), rs.getString("credential_ref"),
                decodeConfig(rs.getString("config_json")),
                ChannelAccessPolicy.parse(rs.getString("access_policy"), ChannelAccessPolicy.DENY_UNKNOWN),
                cn.lgs.orbisops.domain.channel.model.ChannelStatus.require(rs.getString("status")),
                rs.getString("created_by"), instant(rs, "create_time"), instant(rs, "update_time"));
    }

    private ChannelMessageRecord message(ResultSet rs, int rowNum) throws SQLException {
        return new ChannelMessageRecord(
                rs.getString("message_id"), rs.getString("channel_id"), rs.getString("project_id"),
                rs.getString("external_message_id"), rs.getString("external_conversation_id"), rs.getString("sender_id"),
                rs.getString("session_id"), rs.getString("run_id"), rs.getString("direction"), rs.getString("status"),
                rs.getString("payload_json"), rs.getString("error_message"), instant(rs, "create_time"), instant(rs, "update_time"));
    }

    private ChannelIdentityRecord identity(ResultSet rs, int rowNum) throws SQLException {
        return new ChannelIdentityRecord(
                rs.getString("mapping_id"), rs.getString("channel_id"), rs.getString("project_id"),
                rs.getString("external_sender_id"), rs.getString("platform_user_id"), rs.getString("username"),
                cn.lgs.orbisops.domain.channel.model.ChannelStatus.require(rs.getString("status")),
                rs.getLong("version"), rs.getString("created_by"),
                instant(rs, "create_time"), instant(rs, "update_time"));
    }

    private String encodeConfig(Map<String, Object> config) {
        return JSON.toJSONString(config == null ? Map.of() : config);
    }

    private Map<String, Object> decodeConfig(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            JSONObject object = JSON.parseObject(json);
            if (object == null) return Map.of();
            Map<String, Object> result = new LinkedHashMap<>();
            object.forEach(result::put);
            return result;
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private void ensureColumn(String column, String definition) {
        Integer count = requiredTemplate().queryForObject("""
                SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_ops_channel' AND COLUMN_NAME=?
                """, Integer.class, column);
        if (count == null || count == 0) {
            requiredTemplate().execute("ALTER TABLE ai_ops_channel ADD COLUMN " + column + " " + definition);
        }
    }

    private void ensureMessageColumn(String column, String definition) {
        Integer count = requiredTemplate().queryForObject("""
                SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_ops_channel_message' AND COLUMN_NAME=?
                """, Integer.class, column);
        if (count == null || count == 0) {
            requiredTemplate().execute("ALTER TABLE ai_ops_channel_message ADD COLUMN " + column + " " + definition);
        }
    }

    private JdbcTemplate requiredTemplate() {
        if (jdbcTemplate == null) throw new IllegalStateException("CHANNEL_STORE_UNAVAILABLE");
        return jdbcTemplate;
    }
}
