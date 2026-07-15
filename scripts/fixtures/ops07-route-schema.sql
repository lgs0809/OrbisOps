-- PostgreSQL only. Rebuildable routing projection, no business data changes.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE IF NOT EXISTS ops_skill_route_generation (
              generation_id varchar(64) PRIMARY KEY, scope varchar(16) NOT NULL, project_id varchar(128) NOT NULL,
              skill_id varchar(128) NOT NULL, skill_version integer NOT NULL, skill_hash text NOT NULL,
              package_hash text NOT NULL, model_identity text NOT NULL, dimension integer NOT NULL CHECK(dimension=1024),
              status varchar(16) NOT NULL CHECK(status IN ('BUILDING','READY')), created_at timestamptz NOT NULL DEFAULT now(), ready_at timestamptz);
CREATE TABLE IF NOT EXISTS ops_skill_route_document (
              generation_id varchar(64) PRIMARY KEY REFERENCES ops_skill_route_generation(generation_id),
              content_hash varchar(64) NOT NULL, routing_text text NOT NULL, embedding vector(1024) NOT NULL);
CREATE INDEX IF NOT EXISTS idx_skill_route_scope ON ops_skill_route_generation(project_id,scope,status);
