"""Bounded configuration deployment for isolated acceptance targets only.

The MySQL transaction changes the configuration used by actual order requests and
stores its idempotent receipt atomically. No approval or OrbisOps outcome is written.
"""
import datetime
import hashlib
import hmac
import json
import re


class DeploymentControl:
    def __init__(self, sql, quote, project, token, allowed_services):
        self.sql, self.quote, self.project, self.token = sql, quote, project, token
        self.allowed = set(allowed_services)

    def handle(self, handler, body=None):
        if not self.token or not hmac.compare_digest(handler.headers.get("Authorization", ""), "Bearer " + self.token):
            return handler.send(403, {"error": "CONTROL_NOT_AUTHORIZED"})
        try:
            if body is None:
                service = handler.path.removeprefix("/control/state/")
                self.check_service(service)
                data = self.state(service)
            else:
                data = self.apply(body)
            return handler.send(200, data)
        except ValueError as error:
            return handler.send(409, {"error": str(error)})
        except Exception:
            return handler.send(503, {"error": "CONTROL_RESULT_UNAVAILABLE"})

    def check_service(self, service):
        if service not in self.allowed:
            raise ValueError("SERVICE_NOT_ALLOWLISTED")

    def state(self, service):
        self.check_service(service)
        raw = self.sql("SELECT JSON_OBJECT('status','AVAILABLE','resourceKey',service_id,'projectId',project_id,"
                       "'version',version,'scenario',scenario) FROM acceptance_service WHERE project_id="
                       + self.quote(self.project) + " AND service_id=" + self.quote(service))
        if not raw:
            raise ValueError("SERVICE_NOT_FOUND")
        return {**json.loads(raw), "observedAt": datetime.datetime.now(datetime.timezone.utc).isoformat()}

    def apply(self, body):
        if not isinstance(body, dict) or set(body) != {
                "projectId", "service", "expectedVersion", "version", "scenario", "executionKey", "deadline", "actor"}:
            raise ValueError("INVALID_DEPLOYMENT_ARGUMENTS")
        if body["projectId"] != self.project:
            raise ValueError("PROJECT_NOT_AUTHORIZED")
        self.check_service(body["service"])
        for field in ("expectedVersion", "version"):
            if not isinstance(body[field], str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,19}", body[field]):
                raise ValueError("INVALID_VERSION")
        if body["version"] == body["expectedVersion"]:
            raise ValueError("NEW_VERSION_REQUIRED")
        if body["scenario"] not in ("HEALTHY", "FAULT", "SLOW_SQL"):
            raise ValueError("INVALID_FIXTURE_SCENARIO")
        if not isinstance(body["actor"], str) or not 1 <= len(body["actor"]) <= 128:
            raise ValueError("ACTOR_REQUIRED")
        if not isinstance(body["executionKey"], str) or not re.fullmatch(r"[A-Za-z0-9_.:-]{1,256}", body["executionKey"]):
            raise ValueError("INVALID_EXECUTION_KEY")
        deadline = datetime.datetime.fromisoformat(str(body["deadline"]).replace("Z", "+00:00"))
        if deadline.tzinfo is None or deadline <= datetime.datetime.now(datetime.timezone.utc):
            raise ValueError("AUTHORITY_EXPIRED")
        digest = hashlib.sha256(json.dumps({k: v for k, v in body.items() if k not in ("deadline", "executionKey")},
                                          sort_keys=True, separators=(",", ":")).encode()).hexdigest()
        q = self.quote
        identity = "project_id=" + q(self.project) + " AND service_id=" + q(body["service"])
        key = q(body["executionKey"])
        # Lock the business row before consulting the receipt, including on duplicate calls.
        query = f"""START TRANSACTION;
SELECT version INTO @before_version FROM acceptance_service WHERE {identity} FOR UPDATE;
SET @prior_hash=(SELECT request_hash FROM ops08_deployment_receipt WHERE execution_key={key});
SET @can_apply=(@prior_hash IS NULL AND @before_version={q(body['expectedVersion'])});
UPDATE acceptance_service SET version={q(body['version'])},scenario={q(body['scenario'])}
 WHERE {identity} AND @can_apply;
SET @applied=ROW_COUNT();
INSERT INTO ops08_deployment_receipt(execution_key,request_hash,result_json)
 SELECT {key},{q(digest)},JSON_OBJECT('status','SUCCEEDED','projectId',{q(self.project)},
 'resourceKey',{q(body['service'])},'beforeVersion',@before_version,'version',{q(body['version'])},
 'scenario',{q(body['scenario'])},'executionKey',{key},'actor',{q(body['actor'])}) WHERE @applied=1;
COMMIT;
SELECT JSON_OBJECT('requestHash',request_hash,'result',CAST(result_json AS JSON))
 FROM ops08_deployment_receipt WHERE execution_key={key};"""
        raw = self.sql(query)
        if not raw:
            raise ValueError("VERSION_CONFLICT")
        receipt = json.loads(raw)
        if receipt["requestHash"] != digest:
            raise ValueError("IDEMPOTENCY_CONFLICT")
        return receipt["result"]
