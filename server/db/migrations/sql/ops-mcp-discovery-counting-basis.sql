-- Retain old selections verbatim. A NULL basis means the original counter contract
-- was not recorded; recovery must not fill it with today's settings.
SET @ops_mcp_basis_ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE ai_ops_mcp_discovery_selection ADD COLUMN counting_basis_json JSON NULL',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ai_ops_mcp_discovery_selection'
    AND column_name = 'counting_basis_json'
);
PREPARE ops_mcp_basis_stmt FROM @ops_mcp_basis_ddl;
EXECUTE ops_mcp_basis_stmt;
DEALLOCATE PREPARE ops_mcp_basis_stmt;
