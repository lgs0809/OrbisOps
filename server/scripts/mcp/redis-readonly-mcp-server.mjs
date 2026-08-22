#!/usr/bin/env node

import net from "node:net";
import readline from "node:readline";

const REDIS_HOST = process.env.REDIS_HOST || "127.0.0.1";
const REDIS_PORT = Number(process.env.REDIS_PORT || 6379);
const REDIS_USERNAME = String(process.env.REDIS_USERNAME || "").trim();
const REDIS_PASSWORD = process.env.REDIS_PASSWORD || "";
const REDIS_DB = process.env.REDIS_DB || "0";
const REDIS_TIMEOUT_MS = Number(process.env.REDIS_TIMEOUT_MS || 10000);
const REDIS_MCP_MAX_KEYS = Number(process.env.REDIS_MCP_MAX_KEYS || 100);
const REDIS_MCP_MAX_VALUE_BYTES = Number(process.env.REDIS_MCP_MAX_VALUE_BYTES || 65536);
const REDIS_MCP_ALLOWED_KEY_PREFIXES = new Set(
  String(process.env.REDIS_MCP_ALLOWED_KEY_PREFIXES || "")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean),
);
const REDIS_MCP_ALLOW_DEFAULT_USER =
  String(process.env.REDIS_MCP_ALLOW_DEFAULT_USER || "false").toLowerCase() === "true";

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
    content: [{ type: "text", text: JSON.stringify(value, null, 2) }],
    isError: false,
  };
}

function normalizeLimit(value, fallback = 20) {
  const parsed = Number.parseInt(String(value ?? fallback), 10);
  if (!Number.isFinite(parsed) || parsed <= 0) return fallback;
  return Math.min(parsed, REDIS_MCP_MAX_KEYS);
}

function assertRedisConnectionPolicy() {
  if ((!REDIS_USERNAME || REDIS_USERNAME === "default") && !REDIS_MCP_ALLOW_DEFAULT_USER) {
    throw new Error("REDIS_MCP_READONLY_USERNAME_REQUIRED");
  }
  if (!REDIS_PASSWORD) throw new Error("REDIS_MCP_PASSWORD_REQUIRED");
}

function allowedPrefix(value) {
  const text = String(value || "");
  return [...REDIS_MCP_ALLOWED_KEY_PREFIXES].find((prefix) => text.startsWith(prefix));
}

function requireAllowedKey(key) {
  const normalized = String(key || "").trim();
  if (!normalized) throw new Error("REDIS_MCP_KEY_REQUIRED");
  if (REDIS_MCP_ALLOWED_KEY_PREFIXES.size === 0) {
    throw new Error("REDIS_MCP_KEY_PREFIX_ALLOWLIST_REQUIRED");
  }
  if (!allowedPrefix(normalized)) throw new Error("REDIS_MCP_KEY_FORBIDDEN");
  return normalized;
}

function requireAllowedPattern(pattern) {
  const normalized = String(pattern || "").trim();
  if (!normalized) throw new Error("REDIS_MCP_SCAN_PATTERN_REQUIRED");
  if (REDIS_MCP_ALLOWED_KEY_PREFIXES.size === 0) {
    throw new Error("REDIS_MCP_KEY_PREFIX_ALLOWLIST_REQUIRED");
  }
  const prefix = allowedPrefix(normalized);
  if (!prefix || /[*?\[]/.test(normalized.slice(0, prefix.length))) {
    throw new Error("REDIS_MCP_SCAN_PATTERN_FORBIDDEN");
  }
  return normalized;
}

function commandBytes(args) {
  const parts = [`*${args.length}\r\n`];
  for (const arg of args) {
    const text = String(arg ?? "");
    parts.push(`$${Buffer.byteLength(text)}\r\n${text}\r\n`);
  }
  return parts.join("");
}

function readLine(buffer, offset) {
  const end = buffer.indexOf("\r\n", offset);
  if (end < 0) throw new Error("Invalid Redis response");
  return [buffer.slice(offset, end), end + 2];
}

function parseRedis(buffer, offset = 0) {
  const prefix = buffer[offset];
  if (!prefix) throw new Error("Empty Redis response");
  if (prefix === "+") {
    const [line, next] = readLine(buffer, offset + 1);
    return [line, next];
  }
  if (prefix === "-") {
    const [line] = readLine(buffer, offset + 1);
    throw new Error(line);
  }
  if (prefix === ":") {
    const [line, next] = readLine(buffer, offset + 1);
    return [Number(line), next];
  }
  if (prefix === "$") {
    const [line, next] = readLine(buffer, offset + 1);
    const length = Number(line);
    if (length < 0) return [null, next];
    const value = buffer.slice(next, next + length);
    return [value, next + length + 2];
  }
  if (prefix === "*") {
    const [line, afterHeader] = readLine(buffer, offset + 1);
    const length = Number(line);
    if (length < 0) return [null, afterHeader];
    const values = [];
    let next = afterHeader;
    for (let i = 0; i < length; i += 1) {
      const [value, nextOffset] = parseRedis(buffer, next);
      values.push(value);
      next = nextOffset;
    }
    return [values, next];
  }
  throw new Error(`Unknown Redis response prefix: ${prefix}`);
}

function redis(args) {
  assertRedisConnectionPolicy();
  return new Promise((resolve, reject) => {
    const socket = net.createConnection({ host: REDIS_HOST, port: REDIS_PORT });
    let buffer = "";
    let settled = false;
    const cleanup = () => {
      socket.removeAllListeners();
      socket.end();
      socket.destroy();
    };
    const rejectOnce = (error) => {
      if (settled) return;
      settled = true;
      cleanup();
      reject(error);
    };
    const send = (items) => socket.write(commandBytes(items));
    const commands = [];
    commands.push(["AUTH", REDIS_USERNAME, REDIS_PASSWORD]);
    if (REDIS_DB && REDIS_DB !== "0") commands.push(["SELECT", REDIS_DB]);
    commands.push(args);
    let index = 0;
    socket.setTimeout(REDIS_TIMEOUT_MS, () => rejectOnce(new Error("Redis command timeout")));
    socket.on("connect", () => send(commands[index]));
    socket.on("data", (chunk) => {
      buffer += chunk.toString("utf8");
      try {
        const [value, nextOffset] = parseRedis(buffer);
        if (nextOffset > buffer.length) return;
        buffer = buffer.slice(nextOffset);
        index += 1;
        if (index < commands.length) {
          send(commands[index]);
          return;
        }
        if (!settled) {
          settled = true;
          cleanup();
          resolve(value);
        }
      } catch (error) {
        rejectOnce(error);
      }
    });
    socket.on("error", rejectOnce);
  });
}

function asText(value) {
  if (Buffer.isBuffer(value)) return value.toString("utf8");
  return value == null ? "" : String(value);
}

const tools = [
  {
    name: "redis_health",
    description: "Check read-only Redis connectivity with PING.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false },
  },
  {
    name: "redis_info",
    description: "Read Redis INFO for a section such as server, clients, memory, stats, keyspace, or default.",
    inputSchema: {
      type: "object",
      properties: {
        section: { type: "string", description: "Optional INFO section." },
      },
      additionalProperties: false,
    },
  },
  {
    name: "redis_scan",
    description: "Scan keys by pattern without blocking Redis. Returns key names only.",
    inputSchema: {
      type: "object",
      properties: {
        pattern: { type: "string", description: "Key pattern, for example demo:*." },
        count: { type: "integer", description: "Requested scan count, capped by REDIS_MCP_MAX_KEYS." },
      },
      additionalProperties: false,
    },
  },
  {
    name: "redis_get",
    description: "Read a single Redis string value by key. Non-string values should be inspected with redis_scan and redis_info first.",
    inputSchema: {
      type: "object",
      properties: {
        key: { type: "string", description: "Redis key." },
      },
      required: ["key"],
      additionalProperties: false,
    },
  },
  {
    name: "redis_ttl",
    description: "Read TTL for one Redis key.",
    inputSchema: {
      type: "object",
      properties: {
        key: { type: "string", description: "Redis key." },
      },
      required: ["key"],
      additionalProperties: false,
    },
  },
];

async function callTool(name, args = {}) {
  if (name === "redis_health") {
    return { host: REDIS_HOST, port: REDIS_PORT, db: REDIS_DB, ping: asText(await redis(["PING"])) };
  }
  if (name === "redis_info") {
    const section = String(args.section || "default").trim();
    const command = section && section !== "default" ? ["INFO", section] : ["INFO"];
    return { section, info: asText(await redis(command)).slice(0, 20000) };
  }
  if (name === "redis_scan") {
    const pattern = requireAllowedPattern(args.pattern);
    const count = normalizeLimit(args.count);
    const response = await redis(["SCAN", "0", "MATCH", pattern, "COUNT", count]);
    const keys = Array.isArray(response?.[1]) ? response[1].map(asText) : [];
    return { pattern, count, keys: keys.slice(0, count) };
  }
  if (name === "redis_get") {
    const key = requireAllowedKey(args.key);
    const sizeBytes = Number(await redis(["STRLEN", key]));
    if (Number.isFinite(sizeBytes) && sizeBytes > REDIS_MCP_MAX_VALUE_BYTES) {
      return {
        key,
        sizeBytes,
        value: null,
        valueOmitted: true,
        reasonCode: "REDIS_MCP_VALUE_SIZE_LIMIT_EXCEEDED",
      };
    }
    const value = await redis(["GET", key]);
    return { key, sizeBytes, value: asText(value) };
  }
  if (name === "redis_ttl") {
    const key = requireAllowedKey(args.key);
    return { key, ttlSeconds: await redis(["TTL", key]) };
  }
  throw new Error(`Unknown tool: ${name}`);
}

async function handle(message) {
  if (!message || message.jsonrpc !== "2.0") return;
  const { id, method, params } = message;
  try {
    if (method === "initialize") {
      ok(id, {
        protocolVersion: params?.protocolVersion || "2024-11-05",
        capabilities: { tools: {} },
        serverInfo: { name: "local-redis-readonly-mcp-server", version: "2.0.0" },
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
    fail(id, -32000, error.message || "Tool execution failed");
  }
}

const rl = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
rl.on("line", (line) => {
  if (!line.trim()) return;
  try {
    void handle(JSON.parse(line));
  } catch (error) {
    fail(null, -32700, "Parse error", error.message);
  }
});
