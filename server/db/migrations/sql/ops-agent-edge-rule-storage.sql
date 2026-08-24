-- The bounded rule AST is serialized on graph edges. A valid compound rule can
-- exceed the legacy 512-character expression column; retain every existing row.
-- Widen only legacy string columns, leaving a larger text type unchanged.
SET @ops_edge_rule_ddl = IF(EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_ops_agent_edge'
      AND COLUMN_NAME = 'condition_expr' AND DATA_TYPE IN ('varchar', 'char')
), 'ALTER TABLE ai_ops_agent_edge MODIFY condition_expr TEXT NOT NULL DEFAULT (''always'') COMMENT ''条件表达式或有界规则AST''', 'SELECT 1');
PREPARE ops_edge_rule_statement FROM @ops_edge_rule_ddl;
EXECUTE ops_edge_rule_statement;
DEALLOCATE PREPARE ops_edge_rule_statement;
