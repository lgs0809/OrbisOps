package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.mcp.McpToolCatalogStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class JdbcMcpToolCatalogStore implements McpToolCatalogStore {
    private final JdbcTemplate jdbc;
    public JdbcMcpToolCatalogStore(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public Optional<Snapshot> find(String identity) {
        return read(identity,false);
    }
    private Optional<Snapshot> read(String identity,boolean lock) {
        return jdbc.query("SELECT * FROM ai_ops_mcp_remote_catalog WHERE identity_hash=?"+(lock?" FOR UPDATE":""),
            (r,n)->new Snapshot(new Scope(r.getString("identity_hash"),r.getString("project_id"),r.getString("server_id")),
                r.getLong("generation"),r.getString("content_hash"),r.getString("tools_json"),
                instant(r.getTimestamp("checked_at")),instant(r.getTimestamp("succeeded_at")),r.getString("error_code")),identity).stream().findFirst();
    }
    private void register(Scope s) {
        jdbc.update("INSERT INTO ai_ops_mcp_remote_catalog(identity_hash,project_id,server_id) VALUES(?,?,?) ON DUPLICATE KEY UPDATE identity_hash=VALUES(identity_hash)",s.identity(),s.projectId(),s.serverId());
    }
    @Override @Transactional(transactionManager="mysqlTransactionManager",isolation=Isolation.READ_COMMITTED)
    public Snapshot publish(Scope scope,long expected,String hash,String json,Instant started) {
        register(scope);
        var current=read(scope.identity(),true).orElseThrow();
        if (current.generation()!=expected || current.checkedAt()!=null && current.checkedAt().isAfter(started)) return current;
        long generation=current.generation();
        if (!hash.equals(current.contentHash())) {
            generation++;
            jdbc.update("INSERT INTO ai_ops_mcp_remote_catalog_version(identity_hash,generation,content_hash,tools_json) VALUES(?,?,?,?)",scope.identity(),generation,hash,json);
        }
        jdbc.update("UPDATE ai_ops_mcp_remote_catalog SET generation=?,content_hash=?,tools_json=?,checked_at=?,succeeded_at=?,error_code='' WHERE identity_hash=?",
                generation,hash,json,Timestamp.from(started),Timestamp.from(started),scope.identity());
        return read(scope.identity(),false).orElseThrow();
    }
    @Override @Transactional(transactionManager="mysqlTransactionManager",isolation=Isolation.READ_COMMITTED)
    public void failed(Scope scope,Instant started,String errorCode) {
        register(scope);
        jdbc.update("UPDATE ai_ops_mcp_remote_catalog SET checked_at=?,error_code=? WHERE identity_hash=? AND (checked_at IS NULL OR checked_at<=?)",
            Timestamp.from(started),errorCode,scope.identity(),Timestamp.from(started));
    }
    @Override public List<Status> status(String projectId) {
        return jdbc.query("SELECT identity_hash,server_id,generation,JSON_LENGTH(tools_json),checked_at,succeeded_at,error_code FROM ai_ops_mcp_remote_catalog WHERE project_id=? ORDER BY server_id,checked_at DESC",
            (r,n)->new Status(r.getString(1),r.getString(2),r.getLong(3),r.getInt(4),instant(r.getTimestamp(5)),instant(r.getTimestamp(6)),r.getString(7)),projectId);
    }
    @Override public List<Scope> scopesAfter(String cursor,int limit) {
        return jdbc.query("SELECT identity_hash,project_id,server_id FROM ai_ops_mcp_remote_catalog WHERE identity_hash>? ORDER BY identity_hash LIMIT ?",
            (r,n)->new Scope(r.getString(1),r.getString(2),r.getString(3)),cursor,Math.max(1,Math.min(200,limit)));
    }
    private static Instant instant(Timestamp timestamp) { return timestamp==null?null:timestamp.toInstant(); }
}
