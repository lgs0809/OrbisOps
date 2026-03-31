#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import readline from "node:readline";

const STATE_FILE = text(process.env.SERVICE_CONTROL_STATE_FILE);
const ALLOWED_SERVICES = csvSet(process.env.SERVICE_CONTROL_ALLOWED_SERVICES);
const EMERGENCY_STOP = booleanValue(process.env.SERVICE_CONTROL_EMERGENCY_STOP, false);
const LOCK_TIMEOUT_MS = positiveInteger(process.env.SERVICE_CONTROL_LOCK_TIMEOUT_MS, 5000);
const LOCK_STALE_MS = positiveInteger(process.env.SERVICE_CONTROL_LOCK_STALE_MS, 30000);
const TEST_MODE = booleanValue(process.env.SERVICE_CONTROL_TEST_MODE, false);
const TEST_POST_COMMIT_ACTION = text(process.env.SERVICE_CONTROL_TEST_POST_COMMIT_ACTION).toLowerCase();
const TEST_POST_COMMIT_MATCH = text(process.env.SERVICE_CONTROL_TEST_POST_COMMIT_MATCH);
const RECEIPT_HASH_VERSION = 1;

const tools = [
  {
    name: "get_service_status",
    description: "Read the authoritative state and version of one allowlisted service.",
    inputSchema: objectSchema(["projectId", "service", "actor"], identityProperties()),
    annotations: { readOnlyHint: true, destructiveHint: false, idempotentHint: true },
  },
  {
    name: "restart_service_dry_run",
    description: "Validate that an allowlisted service can be restarted without changing service state.",
    inputSchema: objectSchema(["projectId", "service", "actor"], {
      ...identityProperties(),
      expectedVersion: { type: "integer", minimum: 0 },
    }),
    annotations: { readOnlyHint: true, destructiveHint: false, idempotentHint: true },
  },
  {
    name: "restart_service",
    description: "Restart one allowlisted service using expected-version CAS and a durable idempotent receipt.",
    inputSchema: objectSchema(
      ["projectId", "service", "expectedVersion", "executionKey", "deadline", "actor"],
      {
        ...identityProperties(),
        expectedVersion: { type: "integer", minimum: 0 },
        executionKey: stringSchema(1, 256),
        deadline: { type: "string", format: "date-time" },
      },
    ),
    annotations: { readOnlyHint: false, destructiveHint: true, idempotentHint: true },
  },
  {
    name: "get_operation_receipt",
    description: "Read the durable authoritative receipt for an execution key without replaying the operation.",
    inputSchema: objectSchema(
      ["projectId", "executionKey", "actor"],
      {
        projectId: stringSchema(1, 128),
        executionKey: stringSchema(1, 256),
        actor: stringSchema(1, 128),
      },
    ),
    annotations: { readOnlyHint: true, destructiveHint: false, idempotentHint: true },
  },
];

function identityProperties() {
  return {
    projectId: stringSchema(1, 128),
    service: { type: "string", pattern: "^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$" },
    actor: stringSchema(1, 128),
  };
}

function objectSchema(required, properties) {
  return { type: "object", required, properties, additionalProperties: false };
}

function stringSchema(minLength, maxLength) {
  return { type: "string", minLength, maxLength };
}

function text(value) {
  return value == null ? "" : String(value).trim();
}

function csvSet(value) {
  return new Set(String(value || "").split(",").map((item) => item.trim()).filter(Boolean));
}

function booleanValue(value, fallback) {
  const normalized = text(value).toLowerCase();
  if (!normalized) return fallback;
  if (["1", "true", "yes", "on"].includes(normalized)) return true;
  if (["0", "false", "no", "off"].includes(normalized)) return false;
  return fallback;
}

function positiveInteger(value, fallback) {
  const parsed = Number.parseInt(String(value ?? ""), 10);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.keys(value).sort().map((key) => [key, canonical(value[key])]));
  }
  return value;
}

function sha256(value) {
  return crypto.createHash("sha256").update(JSON.stringify(canonical(value))).digest("hex");
}

function serviceKey(projectId, service) {
  return `${projectId}::${service}`;
}

function blocked(reasonCode, message, details = {}) {
  return { status: "BLOCKED", reasonCode, message, ...details };
}

function failed(reasonCode, message, details = {}) {
  return { status: "FAILED", reasonCode, message, ...details };
}

function validateServerConfiguration() {
  if (!STATE_FILE) return blocked("SERVICE_CONTROL_STATE_FILE_REQUIRED", "Persistent state file is required.");
  if (ALLOWED_SERVICES.size === 0) {
    return blocked("SERVICE_CONTROL_SERVICE_ALLOWLIST_REQUIRED", "Service allowlist is empty.");
  }
  if (TEST_POST_COMMIT_ACTION && !TEST_MODE) {
    return blocked("SERVICE_CONTROL_TEST_MODE_REQUIRED", "Fault injection requires explicit test mode.");
  }
  if (TEST_POST_COMMIT_ACTION && TEST_POST_COMMIT_ACTION !== "exit") {
    return blocked("SERVICE_CONTROL_TEST_POST_COMMIT_ACTION_INVALID", "Unsupported post-commit test action.");
  }
  if (TEST_POST_COMMIT_ACTION && !TEST_POST_COMMIT_MATCH) {
    return blocked("SERVICE_CONTROL_TEST_EXECUTION_MATCH_REQUIRED", "Fault injection requires an exact execution-key match value.");
  }
  return null;
}

function validateIdentity(args) {
  const projectId = text(args.projectId);
  const service = text(args.service);
  const actor = text(args.actor);
  if (!projectId) return blocked("SERVICE_CONTROL_PROJECT_REQUIRED", "projectId is required.");
  if (!service) return blocked("SERVICE_CONTROL_SERVICE_REQUIRED", "service is required.");
  if (!actor) return blocked("SERVICE_CONTROL_ACTOR_REQUIRED", "actor is required.");
  if (!/^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$/.test(service)) {
    return blocked("SERVICE_CONTROL_SERVICE_INVALID", "service format is invalid.");
  }
  if (!ALLOWED_SERVICES.has(service)) {
    return blocked("SERVICE_CONTROL_SERVICE_FORBIDDEN", "service is not allowlisted.");
  }
  return null;
}

function validateRestart(args, { enforceDeadline = true } = {}) {
  const identity = validateIdentity(args);
  if (identity) return identity;
  if (!Number.isInteger(args.expectedVersion) || args.expectedVersion < 0) {
    return blocked("SERVICE_CONTROL_EXPECTED_VERSION_INVALID", "expectedVersion must be a non-negative integer.");
  }
  const executionKey = text(args.executionKey);
  if (!executionKey || executionKey.length > 256) {
    return blocked("SERVICE_CONTROL_EXECUTION_KEY_INVALID", "executionKey is required and must not exceed 256 characters.");
  }
  const deadline = Date.parse(text(args.deadline));
  if (!Number.isFinite(deadline)) return blocked("SERVICE_CONTROL_DEADLINE_INVALID", "deadline must be ISO-8601.");
  if (enforceDeadline && deadline <= Date.now()) {
    return blocked("SERVICE_CONTROL_DEADLINE_EXPIRED", "deadline has expired.");
  }
  return null;
}

function defaultState() {
  return { status: "RUNNING", version: 1, restartCount: 0, lastRestartAt: "" };
}

function ensureState(store, projectId, service) {
  const key = serviceKey(projectId, service);
  if (!store.states[key]) store.states[key] = defaultState();
  return store.states[key];
}

function operationInput(args) {
  return {
    projectId: text(args.projectId),
    service: text(args.service),
    expectedVersion: args.expectedVersion,
    executionKey: text(args.executionKey),
    deadline: text(args.deadline),
    actor: text(args.actor),
    operation: "restart_service",
  };
}

function receiptHashFacts(receipt) {
  return {
    status: receipt.status,
    receiptId: receipt.receiptId,
    operation: receipt.operation,
    executionKey: receipt.executionKey,
    projectId: receipt.projectId,
    service: receipt.service,
    actor: receipt.actor,
    expectedVersion: receipt.expectedVersion,
    previousVersion: receipt.previousVersion,
    currentVersion: receipt.currentVersion,
    previousRestartCount: receipt.previousRestartCount,
    currentRestartCount: receipt.currentRestartCount,
    completedAt: receipt.completedAt,
    operationInputHash: receipt.operationInputHash,
    hashVersion: receipt.hashVersion,
  };
}

function validateStoredReceipt(entry) {
  if (!entry || typeof entry !== "object" || Array.isArray(entry)) throw new Error("SERVICE_CONTROL_RECEIPT_INVALID");
  if (!/^[a-f0-9]{64}$/.test(text(entry.inputHash))) throw new Error("SERVICE_CONTROL_RECEIPT_INPUT_HASH_INVALID");
  const receipt = entry.receipt;
  if (!receipt || typeof receipt !== "object" || Array.isArray(receipt)) throw new Error("SERVICE_CONTROL_RECEIPT_INVALID");
  if (receipt.hashVersion !== RECEIPT_HASH_VERSION) throw new Error("SERVICE_CONTROL_RECEIPT_HASH_VERSION_INVALID");
  if (text(receipt.operationInputHash) !== text(entry.inputHash)) throw new Error("SERVICE_CONTROL_RECEIPT_INPUT_HASH_MISMATCH");
  if (text(receipt.resultHash) !== `sha256:${sha256(receiptHashFacts(receipt))}`) {
    throw new Error("SERVICE_CONTROL_RECEIPT_INTEGRITY_INVALID");
  }
}

function normalizeStore(value) {
  if (!value || typeof value !== "object" || Array.isArray(value) || value.schemaVersion !== 1) {
    throw new Error("SERVICE_CONTROL_STORE_SCHEMA_UNSUPPORTED");
  }
  if (!value.states || typeof value.states !== "object" || Array.isArray(value.states)) {
    throw new Error("SERVICE_CONTROL_STORE_STATES_INVALID");
  }
  if (!value.receipts || typeof value.receipts !== "object" || Array.isArray(value.receipts)) {
    throw new Error("SERVICE_CONTROL_STORE_RECEIPTS_INVALID");
  }
  const unsigned = { schemaVersion: 1, states: value.states, receipts: value.receipts };
  if (text(value.storeHash) !== `sha256:${sha256(unsigned)}`) throw new Error("SERVICE_CONTROL_STORE_INTEGRITY_INVALID");
  for (const state of Object.values(value.states)) {
    if (!state || state.status !== "RUNNING" || !Number.isInteger(state.version) || state.version < 1
        || !Number.isInteger(state.restartCount) || state.restartCount < 0) {
      throw new Error("SERVICE_CONTROL_STORE_STATE_INVALID");
    }
  }
  Object.values(value.receipts).forEach(validateStoredReceipt);
  return unsigned;
}

async function readStore() {
  try {
    return normalizeStore(JSON.parse(await fs.readFile(STATE_FILE, "utf8")));
  } catch (error) {
    if (error.code === "ENOENT") return { schemaVersion: 1, states: {}, receipts: {} };
    if (error instanceof SyntaxError) throw new Error("SERVICE_CONTROL_STORE_INVALID_JSON");
    throw error;
  }
}

async function syncDirectory(directory) {
  let handle;
  try {
    handle = await fs.open(directory, "r");
    await handle.sync();
  } catch (error) {
    if (!["EINVAL", "ENOTSUP", "EISDIR", "EPERM"].includes(error.code)) throw error;
  } finally {
    if (handle) await handle.close();
  }
}

async function writeStore(store) {
  const unsigned = { schemaVersion: 1, states: store.states, receipts: store.receipts };
  const persisted = { ...unsigned, storeHash: `sha256:${sha256(unsigned)}` };
  await fs.mkdir(path.dirname(STATE_FILE), { recursive: true });
  const temporary = `${STATE_FILE}.${process.pid}.${crypto.randomUUID()}.tmp`;
  let handle;
  try {
    handle = await fs.open(temporary, "wx", 0o600);
    await handle.writeFile(`${JSON.stringify(persisted, null, 2)}\n`, "utf8");
    await handle.sync();
    await handle.close();
    handle = null;
    await fs.rename(temporary, STATE_FILE);
    await syncDirectory(path.dirname(STATE_FILE));
  } catch (error) {
    if (handle) await handle.close().catch(() => {});
    await fs.unlink(temporary).catch(() => {});
    throw error;
  }
}

async function readLockOwner(lockFile) {
  try {
    const parsed = JSON.parse(await fs.readFile(lockFile, "utf8"));
    const pid = Number(parsed?.pid);
    const nonce = text(parsed?.nonce);
    return Number.isInteger(pid) && pid > 0 && nonce ? { pid, nonce } : null;
  } catch (error) {
    if (error.code === "ENOENT" || error instanceof SyntaxError) return null;
    throw error;
  }
}

function processIsAlive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch (error) {
    return error.code === "EPERM";
  }
}

async function removeOwnedLock(lockFile, owner) {
  const current = await readLockOwner(lockFile);
  if (!current) return;
  if (current.pid !== owner.pid || current.nonce !== owner.nonce) {
    throw new Error("SERVICE_CONTROL_STORE_LOCK_OWNERSHIP_LOST");
  }
  await fs.unlink(lockFile).catch((error) => {
    if (error.code !== "ENOENT") throw error;
  });
}

async function delay(ms) {
  await new Promise((resolve) => setTimeout(resolve, ms));
}

async function withStoreLock(action) {
  const configError = validateServerConfiguration();
  if (configError) return configError;
  const lockFile = `${STATE_FILE}.lock`;
  const started = Date.now();
  const owner = { pid: process.pid, nonce: crypto.randomUUID(), createdAt: new Date().toISOString() };
  let handle;
  while (!handle) {
    try {
      await fs.mkdir(path.dirname(STATE_FILE), { recursive: true });
      handle = await fs.open(lockFile, "wx", 0o600);
      await handle.writeFile(`${JSON.stringify(owner)}\n`, "utf8");
      await handle.sync();
    } catch (error) {
      if (handle) {
        await handle.close().catch(() => {});
        handle = null;
      }
      if (error.code !== "EEXIST") throw error;
      try {
        const stat = await fs.stat(lockFile);
        if (Date.now() - stat.mtimeMs > LOCK_STALE_MS) {
          const existing = await readLockOwner(lockFile);
          if (!existing || !processIsAlive(existing.pid)) {
            await fs.unlink(lockFile);
            continue;
          }
        }
      } catch (statError) {
        if (statError.code !== "ENOENT") throw statError;
      }
      if (Date.now() - started >= LOCK_TIMEOUT_MS) {
        return failed("SERVICE_CONTROL_STORE_LOCK_TIMEOUT", "Could not acquire persistent store lock.");
      }
      await delay(20);
    }
  }
  try {
    const store = await readStore();
    const outcome = await action(store);
    if (outcome?.persist) {
      await writeStore(store);
      if (TEST_MODE && TEST_POST_COMMIT_ACTION === "exit"
          && text(outcome.executionKey) === TEST_POST_COMMIT_MATCH) {
        process.exit(87);
      }
    }
    return outcome?.result ?? outcome;
  } finally {
    await handle.close();
    await removeOwnedLock(lockFile, owner);
  }
}

async function getServiceStatus(args) {
  const validation = validateIdentity(args);
  if (validation) return validation;
  return withStoreLock(async (store) => {
    const key = serviceKey(text(args.projectId), text(args.service));
    const created = !store.states[key];
    const state = ensureState(store, text(args.projectId), text(args.service));
    return { persist: created, result: {
      status: "SUCCEEDED",
      projectId: text(args.projectId),
      service: text(args.service),
      serviceStatus: state.status,
      version: state.version,
      restartCount: state.restartCount,
      lastRestartAt: state.lastRestartAt,
    } };
  });
}

async function dryRunRestart(args) {
  const validation = validateIdentity(args);
  if (validation) return validation;
  if (args.expectedVersion != null && (!Number.isInteger(args.expectedVersion) || args.expectedVersion < 0)) {
    return blocked("SERVICE_CONTROL_EXPECTED_VERSION_INVALID", "expectedVersion must be a non-negative integer.");
  }
  return withStoreLock(async (store) => {
    const key = serviceKey(text(args.projectId), text(args.service));
    const created = !store.states[key];
    const state = ensureState(store, text(args.projectId), text(args.service));
    if (args.expectedVersion != null && state.version !== args.expectedVersion) {
      return { persist: created, result: blocked(
        "SERVICE_CONTROL_EXPECTED_VERSION_MISMATCH",
        "Current service version does not match expectedVersion.",
        { actualVersion: state.version },
      ) };
    }
    return { persist: created, result: {
      status: "PASSED",
      validationType: "SERVICE_RESTART_DRY_RUN",
      trustedProviderObservation: true,
      projectId: text(args.projectId),
      service: text(args.service),
      serviceStatus: state.status,
      expectedVersion: state.version,
      restartCount: state.restartCount,
      writesTargetResource: false,
    } };
  });
}

async function restartService(args) {
  const precheck = validateRestart(args, { enforceDeadline: false });
  if (precheck) return precheck;
  const input = operationInput(args);
  const inputHash = sha256(input);
  return withStoreLock(async (store) => {
    const existing = store.receipts[input.executionKey];
    if (existing) {
      if (existing.inputHash !== inputHash) {
        return { result: blocked(
          "SERVICE_CONTROL_EXECUTION_KEY_CONFLICT",
          "executionKey was already used with different arguments.",
          { executionKey: input.executionKey },
        ) };
      }
      return { result: { ...existing.receipt, replayed: true } };
    }
    if (EMERGENCY_STOP) {
      return { result: blocked(
        "SERVICE_CONTROL_EMERGENCY_STOP_ACTIVE",
        "New service mutations are disabled by the provider emergency stop.",
      ) };
    }
    const validation = validateRestart(args);
    if (validation) return { result: validation };
    const state = ensureState(store, input.projectId, input.service);
    if (state.version !== input.expectedVersion) {
      return { persist: true, result: blocked(
        "SERVICE_CONTROL_EXPECTED_VERSION_MISMATCH",
        "Current service version does not match expectedVersion.",
        { actualVersion: state.version, actualRestartCount: state.restartCount },
      ) };
    }
    const completedAt = new Date().toISOString();
    const receiptFacts = {
      status: "SUCCEEDED",
      receiptId: crypto.randomUUID(),
      operation: "restart_service",
      executionKey: input.executionKey,
      projectId: input.projectId,
      service: input.service,
      actor: input.actor,
      expectedVersion: input.expectedVersion,
      previousVersion: state.version,
      currentVersion: state.version + 1,
      previousRestartCount: state.restartCount,
      currentRestartCount: state.restartCount + 1,
      completedAt,
      operationInputHash: inputHash,
      hashVersion: RECEIPT_HASH_VERSION,
    };
    const receipt = { ...receiptFacts, resultHash: `sha256:${sha256(receiptHashFacts(receiptFacts))}` };
    store.states[serviceKey(input.projectId, input.service)] = {
      status: "RUNNING",
      version: receipt.currentVersion,
      restartCount: receipt.currentRestartCount,
      lastRestartAt: completedAt,
    };
    store.receipts[input.executionKey] = { inputHash, receipt };
    return { persist: true, executionKey: input.executionKey, result: receipt };
  });
}

async function getOperationReceipt(args) {
  const projectId = text(args.projectId);
  const actor = text(args.actor);
  const executionKey = text(args.executionKey);
  if (!projectId) return blocked("SERVICE_CONTROL_PROJECT_REQUIRED", "projectId is required.");
  if (!actor) return blocked("SERVICE_CONTROL_ACTOR_REQUIRED", "actor is required.");
  if (!executionKey) return blocked("SERVICE_CONTROL_EXECUTION_KEY_REQUIRED", "executionKey is required.");
  return withStoreLock(async (store) => {
    const entry = store.receipts[executionKey];
    if (!entry) return { result: { status: "NOT_FOUND", reasonCode: "SERVICE_CONTROL_RECEIPT_NOT_FOUND", executionKey } };
    if (entry.receipt.projectId !== projectId || entry.receipt.actor !== actor) {
      return { result: blocked("SERVICE_CONTROL_RECEIPT_SCOPE_MISMATCH", "Receipt is outside project or actor scope.") };
    }
    return { result: { ...entry.receipt, replayed: true } };
  });
}

async function callTool(name, args = {}) {
  if (name === "get_service_status") return getServiceStatus(args);
  if (name === "restart_service_dry_run") return dryRunRestart(args);
  if (name === "restart_service") return restartService(args);
  if (name === "get_operation_receipt") return getOperationReceipt(args);
  return blocked("SERVICE_CONTROL_TOOL_NOT_ALLOWED", `Unknown or forbidden tool: ${text(name)}`);
}

function write(message) {
  process.stdout.write(`${JSON.stringify(message)}\n`);
}

function ok(id, result = {}) {
  write({ jsonrpc: "2.0", id, result });
}

function fail(id, code, message, data) {
  write({ jsonrpc: "2.0", id, error: { code, message, data } });
}

function toolResult(value) {
  return {
    content: [{ type: "text", text: JSON.stringify(value) }],
    isError: value?.status === "BLOCKED" || value?.status === "FAILED",
  };
}

async function handle(message) {
  if (!message || message.jsonrpc !== "2.0") return;
  const { id, method, params } = message;
  try {
    if (method === "initialize") {
      ok(id, {
        protocolVersion: params?.protocolVersion || "2024-11-05",
        capabilities: { tools: {} },
        serverInfo: { name: "controlled-service-control-mcp-server", version: "2.0.0" },
      });
      return;
    }
    if (method === "notifications/initialized") return;
    if (method === "ping") return ok(id, {});
    if (method === "tools/list") return ok(id, { tools });
    if (method === "tools/call") return ok(id, toolResult(await callTool(params?.name, params?.arguments || {})));
    fail(id, -32601, `Method not found: ${method}`);
  } catch (error) {
    fail(id, -32000, "Tool execution failed", { reasonCode: text(error?.message) || "SERVICE_CONTROL_SERVER_FAILURE" });
  }
}

const rl = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
rl.on("line", (line) => {
  if (!line.trim()) return;
  try {
    void handle(JSON.parse(line));
  } catch (error) {
    fail(null, -32700, "Parse error", { reasonCode: text(error?.message) });
  }
});
