#!/usr/bin/env node

import readline from "node:readline";

const PROMETHEUS_URL = (process.env.PROMETHEUS_URL || "http://127.0.0.1:9090").replace(/\/$/, "");
const PROMETHEUS_USERNAME = process.env.PROMETHEUS_USERNAME || "";
const PROMETHEUS_PASSWORD = process.env.PROMETHEUS_PASSWORD || "";
const PROMETHEUS_TIMEOUT_MS = Number(process.env.PROMETHEUS_TIMEOUT_MS || 30000);
const PROMETHEUS_MAX_RANGE_MINUTES = Number(process.env.PROMETHEUS_MAX_RANGE_MINUTES || 60);

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

function headers() {
  const value = { accept: "application/json" };
  if (PROMETHEUS_USERNAME || PROMETHEUS_PASSWORD) {
    value.authorization = `Basic ${Buffer.from(`${PROMETHEUS_USERNAME}:${PROMETHEUS_PASSWORD}`).toString("base64")}`;
  }
  return value;
}

async function prometheus(path, params = {}) {
  const url = new URL(`${PROMETHEUS_URL}${path}`);
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && String(value).length > 0) {
      url.searchParams.set(key, String(value));
    }
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), PROMETHEUS_TIMEOUT_MS);
  try {
    const response = await fetch(url, { headers: headers(), signal: controller.signal });
    const text = await response.text();
    let body = text;
    try {
      body = text ? JSON.parse(text) : null;
    } catch {
      // Health endpoints can return plain text.
    }
    if (!response.ok) {
      throw new Error(`Prometheus ${response.status}: ${typeof body === "string" ? body : JSON.stringify(body)}`);
    }
    return body;
  } finally {
    clearTimeout(timer);
  }
}

function normalizeRange(start, end) {
  const now = Math.floor(Date.now() / 1000);
  const normalizedEnd = end ? Number(end) : now;
  const normalizedStart = start ? Number(start) : normalizedEnd - 15 * 60;
  const maxSeconds = Math.max(1, PROMETHEUS_MAX_RANGE_MINUTES) * 60;
  if (normalizedEnd - normalizedStart > maxSeconds) {
    return { start: normalizedEnd - maxSeconds, end: normalizedEnd };
  }
  return { start: normalizedStart, end: normalizedEnd };
}

function normalizeInstantTime(value) {
  if (value === undefined || value === null || value === "") return undefined;
  let timestamp = Number(value);
  if (!Number.isFinite(timestamp)) {
    throw new Error("time must be a unix timestamp");
  }
  if (timestamp <= 0) return undefined;
  if (timestamp > 1_000_000_000_000) {
    timestamp = Math.floor(timestamp / 1000);
  }
  return timestamp;
}

const tools = [
  {
    name: "prometheus_health",
    description: "Check Prometheus server/query API availability only. This is discovery evidence, not proof that a project or business metric is healthy; use prometheus_query or prometheus_range_query for conclusions.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false },
  },
  {
    name: "prometheus_metric_names",
    description: "Discover metric names only. Project/service identity is normally stored in labels such as job, application, service, or instance, so do not search metric names for a project name or treat this output as business evidence.",
    inputSchema: {
      type: "object",
      properties: {
        match: { type: "string", description: "Optional metric name substring filter." },
        limit: { type: "integer", description: "Maximum names to return. Defaults to 100." },
      },
      additionalProperties: false,
    },
  },
  {
    name: "prometheus_query",
    description: "Run an instant PromQL query and return actual metric values. Use label selectors (job/application/service/instance) to scope the current project. This is the primary tool for current-state evidence.",
    inputSchema: {
      type: "object",
      properties: {
        query: { type: "string", description: "PromQL expression." },
        time: {
          type: "number",
          description: "Optional unix timestamp in seconds for an explicitly historical instant. Omit it for current status; 0 is treated as omitted.",
        },
      },
      required: ["query"],
      additionalProperties: false,
    },
  },
  {
    name: "prometheus_range_query",
    description: "Run a range PromQL query and return actual time-series values. Use it for trends, rates, error ratios, and latency over a window; the range is capped by PROMETHEUS_MAX_RANGE_MINUTES.",
    inputSchema: {
      type: "object",
      properties: {
        query: { type: "string", description: "PromQL expression." },
        start: { type: "number", description: "Unix timestamp. Defaults to now - 15m." },
        end: { type: "number", description: "Unix timestamp. Defaults to now." },
        step: { type: "string", description: "Prometheus step such as 30s, 1m, or 300." },
      },
      required: ["query"],
      additionalProperties: false,
    },
  },
];

async function callTool(name, args = {}) {
  if (name === "prometheus_health") {
    const health = await prometheus("/-/healthy");
    const query = await prometheus("/api/v1/query", { query: "up" });
    return { baseUrl: PROMETHEUS_URL, health, queryStatus: query?.status || "unknown" };
  }
  if (name === "prometheus_metric_names") {
    const limit = Math.max(1, Math.min(Number(args.limit || 100), 500));
    const match = String(args.match || "").toLowerCase();
    const response = await prometheus("/api/v1/label/__name__/values");
    const names = Array.isArray(response?.data) ? response.data : [];
    return {
      match,
      limit,
      metrics: names.filter((nameValue) => !match || String(nameValue).toLowerCase().includes(match)).slice(0, limit),
    };
  }
  if (name === "prometheus_query") {
    if (!args.query) throw new Error("query is required");
    return prometheus("/api/v1/query", { query: args.query, time: normalizeInstantTime(args.time) });
  }
  if (name === "prometheus_range_query") {
    if (!args.query) throw new Error("query is required");
    const range = normalizeRange(args.start, args.end);
    return prometheus("/api/v1/query_range", {
      query: args.query,
      start: range.start,
      end: range.end,
      step: args.step || "30s",
    });
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
        serverInfo: { name: "local-prometheus-mcp-server", version: "2.0.0" },
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
