CREATE TABLE IF NOT EXISTS ai_ops_landing_verification (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    verification_id VARCHAR(80) NOT NULL,
    landing_run_id VARCHAR(80) NOT NULL,
    project_id VARCHAR(80) NOT NULL,
    package_id VARCHAR(80) NOT NULL,
    approved_version INT NOT NULL,
    approved_package_hash VARCHAR(64) NOT NULL,
    passed TINYINT(1) NOT NULL,
    proof_json LONGTEXT NOT NULL,
    observed_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_landing_verification (verification_id),
    KEY idx_landing_verification_run (landing_run_id, id),
    KEY idx_landing_verification_package (project_id, package_id, approved_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
