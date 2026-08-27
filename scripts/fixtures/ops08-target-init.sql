-- Rendered only by seed-landing-targets.py with private generated credentials.
-- Existing rows, including manually changed versions and execution receipts, are retained.
CREATE DATABASE IF NOT EXISTS ops_acceptance_business_prepare CHARACTER SET utf8mb4;
CREATE TABLE IF NOT EXISTS ops_acceptance_business_prepare.acceptance_customer
 LIKE ops_acceptance_business_a.acceptance_customer;
CREATE TABLE IF NOT EXISTS ops_acceptance_business_prepare.acceptance_service
 LIKE ops_acceptance_business_a.acceptance_service;
CREATE TABLE IF NOT EXISTS ops_acceptance_business_prepare.acceptance_order
 LIKE ops_acceptance_business_a.acceptance_order;
CREATE TABLE IF NOT EXISTS ops_acceptance_business_prepare.ops04_request
 LIKE ops_acceptance_business_a.ops04_request;
INSERT IGNORE INTO ops_acceptance_business_prepare.acceptance_customer
 SELECT * FROM ops_acceptance_business_a.acceptance_customer;
INSERT IGNORE INTO ops_acceptance_business_prepare.acceptance_service
 SELECT * FROM ops_acceptance_business_a.acceptance_service;
INSERT IGNORE INTO ops_acceptance_business_prepare.acceptance_order
 SELECT * FROM ops_acceptance_business_a.acceptance_order;
CREATE TABLE IF NOT EXISTS ops_acceptance_business_a.ops08_deployment_receipt (
 execution_key VARCHAR(256) PRIMARY KEY, request_hash CHAR(64) NOT NULL,
 result_json LONGTEXT NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6));
CREATE TABLE IF NOT EXISTS ops_acceptance_business_prepare.ops08_deployment_receipt
 LIKE ops_acceptance_business_a.ops08_deployment_receipt;
CREATE USER IF NOT EXISTS 'ops_workflow_prepare'@'%' IDENTIFIED BY '{{PREPARE_PASSWORD}}';
GRANT SELECT ON ops_acceptance_business_prepare.acceptance_customer TO 'ops_workflow_prepare'@'%';
GRANT SELECT ON ops_acceptance_business_prepare.acceptance_order TO 'ops_workflow_prepare'@'%';
GRANT SELECT, UPDATE(version,scenario) ON ops_acceptance_business_prepare.acceptance_service TO 'ops_workflow_prepare'@'%';
GRANT INSERT ON ops_acceptance_business_prepare.ops04_request TO 'ops_workflow_prepare'@'%';
GRANT SELECT, INSERT ON ops_acceptance_business_prepare.ops08_deployment_receipt TO 'ops_workflow_prepare'@'%';
GRANT UPDATE(version,scenario) ON ops_acceptance_business_a.acceptance_service TO 'ops_workflow_target'@'%';
GRANT SELECT, INSERT ON ops_acceptance_business_a.ops08_deployment_receipt TO 'ops_workflow_target'@'%';
