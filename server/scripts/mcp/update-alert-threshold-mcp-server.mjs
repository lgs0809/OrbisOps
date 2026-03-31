#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import readline from "node:readline";

const STATE_FILE = String(process.env.ALERT_THRESHOLD_STATE_FILE || "").trim();
const LOCK_TIMEOUT_MS = positiveInteger(process.env.ALERT_THRESHOLD_LOCK_TIMEOUT_MS, 5000);
const LOCK_STALE_MS = positiveInteger(process.env.ALERT_THRESHOLD_LOCK_STALE_MS, 30000);
const MAX_ABSOLUTE_VALUE = 1_000_000_000_000;
const RECEIPT_HASH_VERSION = 1;
const ALLOWED_PROJECTS = csvSet(process.env.ALERT_THRESHOLD_ALLOWED_PROJECTS);
const ALLOWED_METRICS = csvSet(process.env.ALERT_THRESHOLD_ALLOWED_METRICS);
const ALLOWED_APPROVALS = csvSet(process.env.ALERT_THRESHOLD_ALLOWED_APPROVAL_IDS);
const ALLOWED_ACTORS = csvSet(process.env.ALERT_THRESHOLD_ALLOWED_ACTORS);
const INITIAL_STATE_JSON = String(process.env.ALERT_THRESHOLD_INITIAL_STATE_JSON || "{}").trim();
const EMERGENCY_STOP = booleanValue(process.env.ALERT_THRESHOLD_EMERGENCY_STOP, false);
const ALLOW_LEGACY_APPROVAL_IDS = booleanValue(
  process.env.ALERT_THRESHOLD_ALLOW_LEGACY_APPROVAL_IDS,
  false,
);
const ALLOW_LEGACY_UNSIGNED_STORE = booleanValue(
  process.env.ALERT_THRESHOLD_ALLOW_LEGACY_UNSIGNED_STORE,
  false,
);
const APPROVAL_GRANTS = parseApprovalGrants(process.env.ALERT_THRESHOLD_APPROVAL_GRANTS_JSON);
const TEST_MODE = booleanValue(process.env.ALERT_THRESHOLD_TEST_MODE, false);
const TEST_POST_COMMIT_ACTION = text(process.env.ALERT_THRESHOLD_TEST_POST_COMMIT_ACTION).toLowerCase();
const TEST_POST_COMMIT_EXECUTION_KEY = text(
  process.env.ALERT_THRESHOLD_TEST_POST_COMMIT_EXECUTION_KEY,
);

const WRITE_TOOLS = new Set(["update_alert_threshold", "restore_alert_threshold"]);

const tools = [
  {
    name: "get_alert_threshold",
    description: "Read one allowlisted alert threshold and its authoritative version.",
    inputSchema: objectSchema(
      ["projectId", "metric", "actor"],
      {
        projectId: stringSchema(1, 128),
        metric: metricSchema(),
        actor: stringSchema(1, 128),
      },
    ),
    annotations: { readOnlyHint: true, destructiveHint: false, idempotentHint: true },
  },
  {
    name: "update_alert_threshold",
    description: "Update one allowlisted alert threshold with expected value/version CAS and a persistent receipt.",
    inputSchema: mutationSchema(),
    annotations: { readOnlyHint: false, destructiveHint: true, idempotentHint: true },
  },
  {
    name: "get_operation_receipt",
    description: "Query the authoritative persisted result for one execution key without replaying the operation.",
    inputSchema: objectSchema(
      ["executionKey", "projectId", "actor"],
      {
        executionKey: stringSchema(1, 256),
        projectId: stringSchema(1, 128),
        actor: stringSchema(1, 128),
      },
    ),
    annotations: { readOnlyHint: true, destructiveHint: false, idempotentHint: true },
  },
  {
    name: "restore_alert_threshold",
    description: "Restore one threshold through a separately approved expected-state/version CAS operation.",
    inputSchema: mutationSchema(),
    annotations: { readOnlyHint: false, destructiveHint: true, idempotentHint: true },
  },
];

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

function objectSchema(required, properties) {
  return { type: "object", required, properties, additionalProperties: false };
}

function stringSchema(minLength, maxLength) {
  return { type: "string", minLength, maxLength };
}

function metricSchema() {
  return { type: "string", pattern: "^[A-Za-z][A-Za-z0-9_.:-]{0,127}$" };
}

function mutationSchema() {
  return objectSchema(
    [
      "projectId",
      "metric",
      "expectedValue",
      "expectedVersion",
      "newValue",
      "approvalId",
      "executionKey",
      "deadline",
      "actor",
    ],
    {
      projectId: stringSchema(1, 128),
      metric: metricSchema(),
      expectedValue: { type: "number", minimum: -MAX_ABSOLUTE_VALUE, maximum: MAX_ABSOLUTE_VALUE },
      expectedVersion: { type: "integer", minimum: 0 },
      newValue: { type: "number", minimum: -MAX_ABSOLUTE_VALUE, maximum: MAX_ABSOLUTE_VALUE },
      approvalId: stringSchema(1, 128),
      executionKey: stringSchema(1, 256),
      deadline: { type: "string", format: "date-time" },
      actor: stringSchema(1, 128),
    },
  );
}

function positiveInteger(value, fallback) {
  const parsed = Number.parseInt(String(value ?? ""), 10);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function booleanValue(value, fallback) {
  const normalized = text(value).toLowerCase();
  if (!normalized) return fallback;
  if (["true", "1", "yes", "on"].includes(normalized)) return true;
  if (["false", "0", "no", "off"].includes(normalized)) return false;
  return fallback;
}

function csvSet(value) {
  return new Set(
    String(value || "")
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean),
  );
}

function text(value) {
  return value == null ? "" : String(value).trim();
}

function parseApprovalGrants(value) {
  const raw = text(value);
  if (!raw) return { grants: new Map(), error: null };
  try {
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      return { grants: new Map(), error: "ALERT_THRESHOLD_APPROVAL_GRANTS_INVALID" };
    }
    const grants = new Map();
    for (const [approvalId, candidate] of Object.entries(parsed)) {
      if (!text(approvalId) || !candidate || typeof candidate !== "object" || Array.isArray(candidate)) {
        return { grants: new Map(), error: "ALERT_THRESHOLD_APPROVAL_GRANTS_INVALID" };
      }
      const operations = Array.isArray(candidate.operations)
        ? candidate.operations.map(text).filter(Boolean)
        : [];
      const grant = {
        projectId: text(candidate.projectId),
        metric: text(candidate.metric),
        actor: text(candidate.actor),
        operations: new Set(operations),
        expiresAt: text(candidate.expiresAt),
      };
      const expiresAt = Date.parse(grant.expiresAt);
      if (
        [grant.projectId, grant.metric, grant.actor].some((item) => !item || item === "*")
        || grant.operations.size === 0
        || grant.operations.has("*")
        || !Number.isFinite(expiresAt)
      ) {
        return { grants: new Map(), error: "ALERT_THRESHOLD_APPROVAL_GRANTS_INVALID" };
      }
      grants.set(text(approvalId), { ...grant, expiresAtEpochMs: expiresAt });
    }
    return { grants, error: null };
  } catch {
    return { grants: new Map(), error: "ALERT_THRESHOLD_APPROVAL_GRANTS_INVALID" };
  }
}

function stateKey(projectId, metric) {
  return `${projectId}::${metric}`;
}

function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.keys(value)
        .sort()
        .map((key) => [key, canonical(value[key])]),
    );
  }
  return value;
}

function sha256(value) {
  return crypto
    .createHash("sha256")
    .update(JSON.stringify(canonical(value)))
    .digest("hex");
}

function decimalText(value) {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    throw new Error("ALERT_THRESHOLD_HASH_DECIMAL_INVALID");
  }
  if (Object.is(value, -0) || value === 0) return "0";
  const raw = String(value).toLowerCase();
  if (!raw.includes("e")) return normalizePlainDecimal(raw);
  const [coefficient, exponentText] = raw.split("e");
  const exponent = Number.parseInt(exponentText, 10);
  if (!Number.isInteger(exponent)) throw new Error("ALERT_THRESHOLD_HASH_DECIMAL_INVALID");
  const negative = coefficient.startsWith("-");
  const unsigned = negative ? coefficient.slice(1) : coefficient;
  const [integerPart, fractionPart = ""] = unsigned.split(".");
  const digits = `${integerPart}${fractionPart}`;
  const decimalIndex = integerPart.length + exponent;
  let expanded;
  if (decimalIndex <= 0) {
    expanded = `0.${"0".repeat(-decimalIndex)}${digits}`;
  } else if (decimalIndex >= digits.length) {
    expanded = `${digits}${"0".repeat(decimalIndex - digits.length)}`;
  } else {
    expanded = `${digits.slice(0, decimalIndex)}.${digits.slice(decimalIndex)}`;
  }
  return normalizePlainDecimal(`${negative ? "-" : ""}${expanded}`);
}

function normalizePlainDecimal(value) {
  const negative = value.startsWith("-");
  const unsigned = negative ? value.slice(1) : value;
  const [rawInteger, rawFraction = ""] = unsigned.split(".");
  const integerPart = rawInteger.replace(/^0+(?=\d)/, "") || "0";
  const fractionPart = rawFraction.replace(/0+$/, "");
  const normalized = fractionPart ? `${integerPart}.${fractionPart}` : integerPart;
  return normalized === "0" ? "0" : `${negative ? "-" : ""}${normalized}`;
}

function operationHashFacts(input) {
  return {
    actor: text(input.actor),
    approvalId: text(input.approvalId),
    deadline: text(input.deadline),
    executionKey: text(input.executionKey),
    expectedValue: decimalText(input.expectedValue),
    expectedVersion: String(input.expectedVersion),
    hashVersion: RECEIPT_HASH_VERSION,
    metric: text(input.metric),
    newValue: decimalText(input.newValue),
    projectId: text(input.projectId),
    toolName: text(input.toolName),
  };
}

function receiptHashFacts(receipt) {
  return {
    actor: text(receipt.actor),
    approvalId: text(receipt.approvalId),
    completedAt: text(receipt.completedAt),
    currentValue: decimalText(receipt.currentValue),
    currentVersion: String(receipt.currentVersion),
    executionKey: text(receipt.executionKey),
    expectedVersion: String(receipt.expectedVersion),
    hashVersion: receipt.hashVersion,
    metric: text(receipt.metric),
    operation: text(receipt.operation),
    operationInputHash: text(receipt.operationInputHash),
    previousValue: decimalText(receipt.previousValue),
    projectId: text(receipt.projectId),
    receiptId: text(receipt.receiptId),
    status: text(receipt.status),
  };
}

function hashOperationInput(input) {
  return sha256(operationHashFacts(input));
}

function hashReceiptFacts(receipt) {
  return `sha256:${sha256(receiptHashFacts(receipt))}`;
}

function blocked(reasonCode, message, details = {}) {
  return { status: "BLOCKED", reasonCode, message, ...details };
}

function failed(reasonCode, message, details = {}) {
  return { status: "FAILED", reasonCode, message, ...details };
}

function validateServerConfiguration() {
  if (!STATE_FILE) return blocked("ALERT_THRESHOLD_STATE_FILE_REQUIRED", "Persistent state file is required.");
  if (ALLOWED_PROJECTS.size === 0) return blocked("ALERT_THRESHOLD_PROJECT_ALLOWLIST_REQUIRED", "Project allowlist is empty.");
  if (ALLOWED_METRICS.size === 0) return blocked("ALERT_THRESHOLD_METRIC_ALLOWLIST_REQUIRED", "Metric allowlist is empty.");
  if (ALLOWED_ACTORS.size === 0) return blocked("ALERT_THRESHOLD_ACTOR_ALLOWLIST_REQUIRED", "Actor allowlist is empty.");
  if (APPROVAL_GRANTS.error) {
    return blocked(APPROVAL_GRANTS.error, "Approval grants JSON is invalid.");
  }
  if (APPROVAL_GRANTS.grants.size === 0
      && !(ALLOW_LEGACY_APPROVAL_IDS && ALLOWED_APPROVALS.size > 0)) {
    return blocked(
      "ALERT_THRESHOLD_APPROVAL_GRANTS_REQUIRED",
      "Exact, expiring approval grants are required.",
    );
  }
  if (TEST_POST_COMMIT_ACTION && !TEST_MODE) {
    return blocked(
      "ALERT_THRESHOLD_TEST_MODE_REQUIRED",
      "Post-commit fault injection is forbidden unless explicit test mode is enabled.",
    );
  }
  if (TEST_POST_COMMIT_ACTION && TEST_POST_COMMIT_ACTION !== "exit") {
    return blocked(
      "ALERT_THRESHOLD_TEST_POST_COMMIT_ACTION_INVALID",
      "Unsupported post-commit test action.",
    );
  }
  if (TEST_POST_COMMIT_ACTION && !TEST_POST_COMMIT_EXECUTION_KEY) {
    return blocked(
      "ALERT_THRESHOLD_TEST_EXECUTION_KEY_REQUIRED",
      "Post-commit fault injection requires an exact execution key.",
    );
  }
  return null;
}

function validateIdentity(args) {
  const projectId = text(args.projectId);
  const metric = text(args.metric);
  const actor = text(args.actor);
  if (!projectId) return blocked("ALERT_THRESHOLD_PROJECT_REQUIRED", "projectId is required.");
  if (!metric) return blocked("ALERT_THRESHOLD_METRIC_REQUIRED", "metric is required.");
  if (!actor) return blocked("ALERT_THRESHOLD_ACTOR_REQUIRED", "actor is required.");
  if (!/^[A-Za-z][A-Za-z0-9_.:-]{0,127}$/.test(metric)) {
    return blocked("ALERT_THRESHOLD_METRIC_INVALID", "metric format is invalid.");
  }
  if (!ALLOWED_PROJECTS.has(projectId)) {
    return blocked("ALERT_THRESHOLD_PROJECT_FORBIDDEN", "projectId is not allowlisted.");
  }
  if (!ALLOWED_METRICS.has(metric)) {
    return blocked("ALERT_THRESHOLD_METRIC_FORBIDDEN", "metric is not allowlisted.");
  }
  if (!ALLOWED_ACTORS.has(actor)) {
    return blocked("ALERT_THRESHOLD_ACTOR_FORBIDDEN", "actor is not allowlisted.");
  }
  return null;
}

function validateReceiptQueryIdentity(args) {
  const projectId = text(args.projectId);
  const actor = text(args.actor);
  if (!projectId) return blocked("ALERT_THRESHOLD_PROJECT_REQUIRED", "projectId is required.");
  if (!actor) return blocked("ALERT_THRESHOLD_ACTOR_REQUIRED", "actor is required.");
  if (!ALLOWED_PROJECTS.has(projectId)) {
    return blocked("ALERT_THRESHOLD_PROJECT_FORBIDDEN", "projectId is not allowlisted.");
  }
  if (!ALLOWED_ACTORS.has(actor)) {
    return blocked("ALERT_THRESHOLD_ACTOR_FORBIDDEN", "actor is not allowlisted.");
  }
  return null;
}

function validateApproval(toolName, args) {
  const approvalId = text(args.approvalId);
  if (!approvalId) {
    return blocked("ALERT_THRESHOLD_APPROVAL_REQUIRED", "approvalId is required.");
  }
  const grant = APPROVAL_GRANTS.grants.get(approvalId);
  if (!grant) {
    if (ALLOW_LEGACY_APPROVAL_IDS && ALLOWED_APPROVALS.has(approvalId)) return null;
    return blocked("ALERT_THRESHOLD_APPROVAL_GRANT_NOT_FOUND", "approvalId has no exact grant.");
  }
  if (grant.expiresAtEpochMs <= Date.now()) {
    return blocked("ALERT_THRESHOLD_APPROVAL_EXPIRED", "approval grant has expired.");
  }
  if (grant.projectId !== text(args.projectId)) {
    return blocked("ALERT_THRESHOLD_APPROVAL_PROJECT_MISMATCH", "approval grant project does not match.");
  }
  if (grant.metric !== text(args.metric)) {
    return blocked("ALERT_THRESHOLD_APPROVAL_METRIC_MISMATCH", "approval grant metric does not match.");
  }
  if (grant.actor !== text(args.actor)) {
    return blocked("ALERT_THRESHOLD_APPROVAL_ACTOR_MISMATCH", "approval grant actor does not match.");
  }
  if (!grant.operations.has(toolName)) {
    return blocked("ALERT_THRESHOLD_APPROVAL_OPERATION_FORBIDDEN", "approval grant does not allow this operation.");
  }
  return null;
}

function validateMutation(toolName, args, { enforceDeadline = true, enforceApproval = true } = {}) {
  const identity = validateIdentity(args);
  if (identity) return identity;
  const executionKey = text(args.executionKey);
  if (!executionKey || executionKey.length > 256) {
    return blocked("ALERT_THRESHOLD_EXECUTION_KEY_INVALID", "executionKey is required and must not exceed 256 characters.");
  }
  const deadline = Date.parse(text(args.deadline));
  if (!Number.isFinite(deadline)) {
    return blocked("ALERT_THRESHOLD_DEADLINE_INVALID", "deadline must be an ISO-8601 instant.");
  }
  if (enforceDeadline && deadline <= Date.now()) {
    return blocked("ALERT_THRESHOLD_DEADLINE_EXPIRED", "deadline has expired.");
  }
  for (const [name, value] of [["expectedValue", args.expectedValue], ["newValue", args.newValue]]) {
    if (typeof value !== "number" || !Number.isFinite(value) || Math.abs(value) > MAX_ABSOLUTE_VALUE) {
      return blocked(`ALERT_THRESHOLD_${name.toUpperCase()}_INVALID`, `${name} is outside the supported numeric range.`);
    }
  }
  if (!Number.isInteger(args.expectedVersion) || args.expectedVersion < 0) {
    return blocked("ALERT_THRESHOLD_EXPECTED_VERSION_INVALID", "expectedVersion must be a non-negative integer.");
  }
  if (Object.is(args.expectedValue, args.newValue)) {
    return blocked("ALERT_THRESHOLD_NO_CHANGE", "newValue must differ from expectedValue.");
  }
  if (enforceApproval) {
    const approval = validateApproval(toolName, args);
    if (approval) return approval;
  }
  return null;
}

async function delay(milliseconds) {
  await new Promise((resolve) => setTimeout(resolve, milliseconds));
}

async function withStoreLock(action) {
  const configurationError = validateServerConfiguration();
  if (configurationError) return configurationError;
  const lockFile = `${STATE_FILE}.lock`;
  const started = Date.now();
  const owner = {
    pid: process.pid,
    nonce: crypto.randomUUID(),
    createdAt: new Date().toISOString(),
  };
  let handle;
  while (!handle) {
    let openedThisAttempt = false;
    try {
      await fs.mkdir(path.dirname(STATE_FILE), { recursive: true });
      handle = await fs.open(lockFile, "wx", 0o600);
      openedThisAttempt = true;
      await handle.writeFile(`${JSON.stringify(owner)}\n`, "utf8");
      await handle.sync();
    } catch (error) {
      if (handle) {
        await handle.close().catch(() => {});
        handle = null;
      }
      if (error.code !== "EEXIST") {
        if (openedThisAttempt) {
          await fs.unlink(lockFile).catch(() => {});
        }
        throw error;
      }
      try {
        const stat = await fs.stat(lockFile);
        if (Date.now() - stat.mtimeMs > LOCK_STALE_MS) {
          const existingOwner = await readLockOwner(lockFile);
          if (!existingOwner || !processIsAlive(existingOwner.pid)) {
            await fs.unlink(lockFile);
            continue;
          }
        }
      } catch (statError) {
        if (statError.code !== "ENOENT") throw statError;
      }
      if (Date.now() - started >= LOCK_TIMEOUT_MS) {
        return failed("ALERT_THRESHOLD_STORE_LOCK_TIMEOUT", "Could not acquire the persistent store lock.");
      }
      await delay(20);
    }
  }
  try {
    const store = await readStore();
    const outcome = await action(store);
    if (outcome?.persist) {
      await writeStore(store);
      injectPostCommitFault(outcome);
    }
    return outcome?.result ?? outcome;
  } finally {
    await handle.close();
    await removeOwnedLock(lockFile, owner);
  }
}

async function readLockOwner(lockFile) {
  try {
    const parsed = JSON.parse(await fs.readFile(lockFile, "utf8"));
    const pid = Number(parsed?.pid);
    const nonce = text(parsed?.nonce);
    if (!Number.isInteger(pid) || pid <= 0 || !nonce) return null;
    return { pid, nonce };
  } catch (error) {
    if (error.code === "ENOENT") return null;
    if (error instanceof SyntaxError) return null;
    throw error;
  }
}

function processIsAlive(pid) {
  if (!Number.isInteger(pid) || pid <= 0) return false;
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
    throw new Error("ALERT_THRESHOLD_STORE_LOCK_OWNERSHIP_LOST");
  }
  await fs.unlink(lockFile).catch((error) => {
    if (error.code !== "ENOENT") throw error;
  });
}

function injectPostCommitFault(outcome) {
  if (!TEST_MODE || TEST_POST_COMMIT_ACTION !== "exit") return;
  if (text(outcome?.executionKey) !== TEST_POST_COMMIT_EXECUTION_KEY) return;
  process.exit(86);
}

async function readStore() {
  try {
    const raw = await fs.readFile(STATE_FILE, "utf8");
    const parsed = JSON.parse(raw);
    return normalizeStore(parsed);
  } catch (error) {
    if (error.code === "ENOENT") return initialStore();
    if (error instanceof SyntaxError) {
      throw new Error("ALERT_THRESHOLD_STORE_INVALID_JSON");
    }
    throw error;
  }
}

function normalizeStore(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error("ALERT_THRESHOLD_STORE_INVALID");
  }
  if (value.schemaVersion === 1 && ALLOW_LEGACY_UNSIGNED_STORE) {
    return validatedUnsignedStore({
      schemaVersion: 2,
      states: value.states,
      receipts: value.receipts,
    });
  }
  if (value.schemaVersion !== 2) {
    throw new Error("ALERT_THRESHOLD_STORE_SCHEMA_UNSUPPORTED");
  }
  const store = validatedUnsignedStore({
    schemaVersion: 2,
    states: value.states,
    receipts: value.receipts,
  });
  const expectedHash = `sha256:${sha256(store)}`;
  if (text(value.storeHash) !== expectedHash) {
    throw new Error("ALERT_THRESHOLD_STORE_INTEGRITY_INVALID");
  }
  return store;
}

function validatedUnsignedStore(value) {
  if (!value.states || typeof value.states !== "object" || Array.isArray(value.states)) {
    throw new Error("ALERT_THRESHOLD_STORE_STATES_INVALID");
  }
  if (!value.receipts || typeof value.receipts !== "object" || Array.isArray(value.receipts)) {
    throw new Error("ALERT_THRESHOLD_STORE_RECEIPTS_INVALID");
  }
  for (const state of Object.values(value.states)) {
    if (
      !state
      || typeof state !== "object"
      || !Number.isFinite(state.value)
      || !Number.isInteger(state.version)
      || state.version < 0
    ) {
      throw new Error("ALERT_THRESHOLD_STORE_STATE_INVALID");
    }
  }
  for (const entry of Object.values(value.receipts)) {
    validatePersistedReceipt(entry);
  }
  return { schemaVersion: 2, states: value.states, receipts: value.receipts };
}

function validatePersistedReceipt(entry) {
  if (!entry || typeof entry !== "object" || Array.isArray(entry)) {
    throw new Error("ALERT_THRESHOLD_STORE_RECEIPT_INVALID");
  }
  if (!/^[a-f0-9]{64}$/.test(text(entry.inputHash))) {
    throw new Error("ALERT_THRESHOLD_STORE_RECEIPT_INPUT_HASH_INVALID");
  }
  const receipt = entry.receipt;
  if (!receipt || typeof receipt !== "object" || Array.isArray(receipt)) {
    throw new Error("ALERT_THRESHOLD_STORE_RECEIPT_INVALID");
  }
  if (receipt.hashVersion !== RECEIPT_HASH_VERSION) {
    throw new Error("ALERT_THRESHOLD_STORE_RECEIPT_HASH_VERSION_UNSUPPORTED");
  }
  if (text(receipt.operationInputHash) !== text(entry.inputHash)) {
    throw new Error("ALERT_THRESHOLD_STORE_RECEIPT_INPUT_HASH_MISMATCH");
  }
  if (text(receipt.resultHash) !== hashReceiptFacts(receipt)) {
    throw new Error("ALERT_THRESHOLD_STORE_RECEIPT_INTEGRITY_INVALID");
  }
}

function initialStore() {
  let parsed;
  try {
    parsed = JSON.parse(INITIAL_STATE_JSON || "{}");
  } catch {
    throw new Error("ALERT_THRESHOLD_INITIAL_STATE_JSON_INVALID");
  }
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error("ALERT_THRESHOLD_INITIAL_STATE_JSON_INVALID");
  }
  const states = {};
  for (const [projectId, metrics] of Object.entries(parsed)) {
    if (!ALLOWED_PROJECTS.has(projectId)) {
      throw new Error("ALERT_THRESHOLD_INITIAL_STATE_PROJECT_FORBIDDEN");
    }
    if (!metrics || typeof metrics !== "object" || Array.isArray(metrics)) {
      throw new Error("ALERT_THRESHOLD_INITIAL_STATE_JSON_INVALID");
    }
    for (const [metric, state] of Object.entries(metrics)) {
      if (!ALLOWED_METRICS.has(metric)) {
        throw new Error("ALERT_THRESHOLD_INITIAL_STATE_METRIC_FORBIDDEN");
      }
      if (!state || typeof state !== "object" || Array.isArray(state)) {
        throw new Error("ALERT_THRESHOLD_INITIAL_STATE_JSON_INVALID");
      }
      const value = Number(state.value);
      const version = Number(state.version);
      if (!Number.isFinite(value) || !Number.isInteger(version) || version < 0) {
        throw new Error("ALERT_THRESHOLD_INITIAL_STATE_JSON_INVALID");
      }
      states[stateKey(projectId, metric)] = { value, version };
    }
  }
  return { schemaVersion: 2, states, receipts: {} };
}

async function writeStore(store) {
  const unsignedStore = validatedUnsignedStore({
    schemaVersion: 2,
    states: store.states,
    receipts: store.receipts,
  });
  const persistedStore = {
    ...unsignedStore,
    storeHash: `sha256:${sha256(unsignedStore)}`,
  };
  const temporary = `${STATE_FILE}.${process.pid}.${crypto.randomUUID()}.tmp`;
  let handle;
  try {
    handle = await fs.open(temporary, "wx", 0o600);
    await handle.writeFile(`${JSON.stringify(persistedStore, null, 2)}\n`, "utf8");
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

async function getAlertThreshold(args) {
  const identity = validateIdentity(args);
  if (identity) return identity;
  return withStoreLock(async (store) => {
    const key = stateKey(text(args.projectId), text(args.metric));
    const state = store.states[key];
    if (!state) {
      return { result: blocked("ALERT_THRESHOLD_NOT_FOUND", "Threshold state does not exist.") };
    }
    return {
      result: {
        status: "SUCCEEDED",
        projectId: text(args.projectId),
        metric: text(args.metric),
        value: state.value,
        version: state.version,
      },
    };
  });
}

async function getOperationReceipt(args) {
  const identity = validateReceiptQueryIdentity(args);
  if (identity) return identity;
  const executionKey = text(args.executionKey);
  if (!executionKey) return blocked("ALERT_THRESHOLD_EXECUTION_KEY_REQUIRED", "executionKey is required.");
  return withStoreLock(async (store) => {
    const entry = store.receipts[executionKey];
    if (!entry) {
      return {
        result: {
          status: "NOT_FOUND",
          reasonCode: "ALERT_THRESHOLD_RECEIPT_NOT_FOUND",
          executionKey,
        },
      };
    }
    if (
      entry.receipt.projectId !== text(args.projectId)
      || entry.receipt.actor !== text(args.actor)
    ) {
      return {
        result: blocked(
          "ALERT_THRESHOLD_RECEIPT_SCOPE_MISMATCH",
          "Receipt is outside the requested project or actor scope.",
          { executionKey },
        ),
      };
    }
    return { result: { ...entry.receipt, replayed: true } };
  });
}

async function mutateThreshold(toolName, args) {
  const validation = validateMutation(toolName, args, {
    enforceDeadline: false,
    enforceApproval: false,
  });
  if (validation) return validation;
  const executionKey = text(args.executionKey);
  const operationInput = {
    toolName,
    projectId: text(args.projectId),
    metric: text(args.metric),
    expectedValue: args.expectedValue,
    expectedVersion: args.expectedVersion,
    newValue: args.newValue,
    approvalId: text(args.approvalId),
    executionKey,
    deadline: text(args.deadline),
    actor: text(args.actor),
  };
  const inputHash = hashOperationInput(operationInput);
  return withStoreLock(async (store) => {
    const existing = store.receipts[executionKey];
    if (existing) {
      if (existing.inputHash !== inputHash) {
        return {
          result: blocked(
            "ALERT_THRESHOLD_EXECUTION_KEY_CONFLICT",
            "executionKey was already used with different arguments.",
            { executionKey },
          ),
        };
      }
      return { result: { ...existing.receipt, replayed: true } };
    }

    if (EMERGENCY_STOP) {
      return {
        result: blocked(
          "ALERT_THRESHOLD_EMERGENCY_STOP_ACTIVE",
          "New threshold mutations are disabled by the provider emergency stop.",
        ),
      };
    }

    const firstExecutionValidation = validateMutation(toolName, args);
    if (firstExecutionValidation) {
      return { result: firstExecutionValidation };
    }

    const key = stateKey(operationInput.projectId, operationInput.metric);
    const current = store.states[key];
    if (!current) {
      return { result: blocked("ALERT_THRESHOLD_NOT_FOUND", "Threshold state does not exist.") };
    }
    if (!Object.is(current.value, operationInput.expectedValue)) {
      return {
        result: blocked(
          "ALERT_THRESHOLD_EXPECTED_VALUE_MISMATCH",
          "Current threshold value does not match expectedValue.",
          { actualValue: current.value, actualVersion: current.version },
        ),
      };
    }
    if (current.version !== operationInput.expectedVersion) {
      return {
        result: blocked(
          "ALERT_THRESHOLD_EXPECTED_VERSION_MISMATCH",
          "Current threshold version does not match expectedVersion.",
          { actualValue: current.value, actualVersion: current.version },
        ),
      };
    }

    const completedAt = new Date().toISOString();
    const receiptFacts = {
      status: "SUCCEEDED",
      receiptId: crypto.randomUUID(),
      operation: toolName,
      executionKey,
      projectId: operationInput.projectId,
      metric: operationInput.metric,
      approvalId: operationInput.approvalId,
      actor: operationInput.actor,
      expectedVersion: operationInput.expectedVersion,
      hashVersion: RECEIPT_HASH_VERSION,
      operationInputHash: inputHash,
      previousValue: current.value,
      currentValue: operationInput.newValue,
      currentVersion: current.version + 1,
      completedAt,
    };
    const receipt = {
      ...receiptFacts,
      resultHash: hashReceiptFacts(receiptFacts),
    };
    store.states[key] = { value: receipt.currentValue, version: receipt.currentVersion };
    store.receipts[executionKey] = { inputHash, receipt };
    return { persist: true, executionKey, result: receipt };
  });
}

async function callTool(name, args = {}) {
  if (name === "get_alert_threshold") return getAlertThreshold(args);
  if (name === "get_operation_receipt") return getOperationReceipt(args);
  if (WRITE_TOOLS.has(name)) return mutateThreshold(name, args);
  return blocked("ALERT_THRESHOLD_TOOL_NOT_ALLOWED", `Unknown or forbidden tool: ${text(name)}`);
}

async function handle(message) {
  if (!message || message.jsonrpc !== "2.0") return;
  const { id, method, params } = message;
  try {
    if (method === "initialize") {
      ok(id, {
        protocolVersion: params?.protocolVersion || "2024-11-05",
        capabilities: { tools: {} },
        serverInfo: { name: "controlled-alert-threshold-mcp-server", version: "2.0.0" },
      });
      return;
    }
    if (method === "notifications/initialized") return;
    if (method === "ping") {
      ok(id, {});
      return;
    }
    if (method === "tools/list") {
      ok(id, { tools });
      return;
    }
    if (method === "tools/call") {
      ok(id, toolResult(await callTool(params?.name, params?.arguments || {})));
      return;
    }
    fail(id, -32601, `Method not found: ${method}`);
  } catch (error) {
    fail(id, -32000, "Tool execution failed", {
      reasonCode: text(error?.message) || "ALERT_THRESHOLD_SERVER_FAILURE",
    });
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
