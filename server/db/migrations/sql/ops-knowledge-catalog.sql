-- 运维知识库目录和项目授权关系。增量执行，不影响既有 RAG 文档表和 PgVector 表。

CREATE TABLE IF NOT EXISTS ai_ops_knowledge_base (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  kb_id VARCHAR(128) NOT NULL COMMENT '知识库ID',
  project_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '项目ID，GLOBAL为空',
  kb_name VARCHAR(160) NOT NULL COMMENT '知识库名称',
  scope VARCHAR(24) NOT NULL COMMENT 'GLOBAL/PROJECT',
  description TEXT NULL COMMENT '说明',
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
  document_count BIGINT NOT NULL DEFAULT 0 COMMENT '文档数快照',
  chunk_count BIGINT NOT NULL DEFAULT 0 COMMENT '结构化片段数快照',
  source_type VARCHAR(64) NOT NULL DEFAULT 'DB' COMMENT '来源类型',
  retrieval_policy_json MEDIUMTEXT NULL COMMENT '检索策略JSON',
  create_by VARCHAR(128) NOT NULL DEFAULT '' COMMENT '创建人',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_scope_project_kb (scope, project_id, kb_id),
  KEY idx_project_status (project_id, status),
  KEY idx_scope_status (scope, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维知识库目录表';

CREATE TABLE IF NOT EXISTS ai_ops_project_knowledge_base (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  project_id VARCHAR(128) NOT NULL COMMENT '项目ID',
  global_kb_id VARCHAR(128) NOT NULL COMMENT '启用的通用知识库ID',
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
  enabled_by VARCHAR(128) NOT NULL DEFAULT '' COMMENT '启用人',
  enabled_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '启用时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_global_kb (project_id, global_kb_id),
  KEY idx_project_status (project_id, status),
  KEY idx_global_kb (global_kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目启用通用知识库关系表';

CREATE TABLE IF NOT EXISTS ai_ops_knowledge_retrieval_policy (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  kb_id VARCHAR(128) NOT NULL COMMENT '知识库ID',
  kb_scope VARCHAR(24) NOT NULL COMMENT 'GLOBAL/PROJECT',
  project_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '项目ID，GLOBAL为空',
  chunk_size INT NOT NULL DEFAULT 3000 COMMENT '结构单元超长时的最大分段字符数',
  overlap_size INT NOT NULL DEFAULT 0 COMMENT '仅用于不可再按结构拆分的超长块重叠字符数',
  top_k INT NOT NULL DEFAULT 5 COMMENT '召回数量',
  rerank_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用 rerank',
  embedding_model_id VARCHAR(160) NOT NULL DEFAULT '' COMMENT 'Embedding 模型',
  metadata_filter_json MEDIUMTEXT NULL COMMENT 'metadata 过滤条件',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_scope_project_kb_policy (kb_scope, project_id, kb_id),
  KEY idx_project_scope (project_id, kb_scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维知识库解析与检索策略表';

CREATE TABLE IF NOT EXISTS ai_ops_knowledge_document (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  document_id VARCHAR(128) NOT NULL COMMENT '文档ID',
  kb_id VARCHAR(128) NOT NULL COMMENT '知识库ID',
  kb_scope VARCHAR(24) NOT NULL COMMENT 'GLOBAL/PROJECT',
  project_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '项目ID，GLOBAL为空',
  file_name VARCHAR(512) NOT NULL DEFAULT '' COMMENT '原始文件名',
  display_name VARCHAR(512) NOT NULL DEFAULT '' COMMENT '展示名称',
  source_type VARCHAR(64) NOT NULL DEFAULT 'UPLOAD' COMMENT '来源类型',
  document_type VARCHAR(64) NOT NULL DEFAULT '' COMMENT '文档类型',
  file_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小',
  parse_status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED' COMMENT '解析状态',
  vector_status VARCHAR(32) NOT NULL DEFAULT 'ASYNC' COMMENT '向量化状态',
  chunk_count INT NOT NULL DEFAULT 0 COMMENT '已解析片段数',
  structure_preserved TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否保留结构',
  ingestion_job_id VARCHAR(80) NOT NULL DEFAULT '' COMMENT '入库任务ID',
  metadata_json MEDIUMTEXT NULL COMMENT '元数据',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_document_id (document_id),
  KEY idx_scope_project_kb (kb_scope, project_id, kb_id),
  KEY idx_job_id (ingestion_job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维知识库文档目录表';

CREATE TABLE IF NOT EXISTS ai_ops_knowledge_chunk (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增ID',
  chunk_id VARCHAR(160) NOT NULL COMMENT '片段ID',
  document_id VARCHAR(128) NOT NULL COMMENT '文档ID',
  kb_id VARCHAR(128) NOT NULL COMMENT '知识库ID',
  kb_scope VARCHAR(24) NOT NULL COMMENT 'GLOBAL/PROJECT',
  project_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '项目ID，GLOBAL为空',
  chunk_index INT NOT NULL DEFAULT 0 COMMENT '片段序号',
  chunk_strategy VARCHAR(80) NOT NULL DEFAULT '' COMMENT '切分策略',
  content_preview TEXT NULL COMMENT '内容预览',
  parse_status VARCHAR(32) NOT NULL DEFAULT 'READY' COMMENT '解析状态',
  vector_status VARCHAR(32) NOT NULL DEFAULT 'VECTOR_PIPELINE' COMMENT '向量状态',
  metadata_json MEDIUMTEXT NULL COMMENT '元数据',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_chunk_id (chunk_id),
  KEY idx_document_id (document_id),
  KEY idx_scope_project_kb (kb_scope, project_id, kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维知识库结构化片段目录表';
