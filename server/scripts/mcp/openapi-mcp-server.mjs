#!/usr/bin/env node

import fs from "node:fs/promises";
import readline from "node:readline";

const OPENAPI_URL = process.env.OPENAPI_URL || "http://127.0.0.1:8080/v3/api-docs";
const OPENAPI_TIMEOUT_MS = Number(process.env.OPENAPI_TIMEOUT_MS || 10000);

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

async function loadDocument() {
  if (OPENAPI_URL.startsWith("file://")) {
    return JSON.parse(await fs.readFile(new URL(OPENAPI_URL), "utf8"));
  }
  if (!OPENAPI_URL.includes("://")) {
    return JSON.parse(await fs.readFile(OPENAPI_URL, "utf8"));
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), OPENAPI_TIMEOUT_MS);
  try {
    const response = await fetch(OPENAPI_URL, {
      headers: { accept: "application/json" },
      signal: controller.signal,
    });
    const text = await response.text();
    if (!response.ok) {
      throw new Error(`OpenAPI ${response.status}: ${text}`);
    }
    return JSON.parse(text);
  } finally {
    clearTimeout(timer);
  }
}

const HTTP_METHODS = new Set([
  "get", "post", "put", "patch", "delete", "head", "options", "trace",
]);

function operations(document) {
  const result = [];
  for (const [path, pathItem] of Object.entries(document?.paths || {})) {
    if (!pathItem || typeof pathItem !== "object") continue;
    for (const [method, operation] of Object.entries(pathItem)) {
      if (!HTTP_METHODS.has(method.toLowerCase()) || !operation || typeof operation !== "object") continue;
      result.push({
        method: method.toUpperCase(),
        path,
        summary: operation.summary || "",
        description: operation.description || "",
        operationId: operation.operationId || "",
        tags: Array.isArray(operation.tags) ? operation.tags : [],
      });
    }
  }
  return result;
}

const tools = [
  {
    name: "openapi_list_operations",
    description: "Return every API operation from the current project's Swagger/OpenAPI document. Use this before guessing a private endpoint from a business term such as 订单锁定 or 支付回调.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false },
  },
];

async function callTool(name) {
  if (name !== "openapi_list_operations") {
    throw new Error(`Unknown tool: ${name}`);
  }
  const document = await loadDocument();
  const allOperations = operations(document);
  return {
    source: OPENAPI_URL,
    openapi: document?.openapi || document?.swagger || "",
    title: document?.info?.title || "",
    version: document?.info?.version || "",
    operationCount: allOperations.length,
    operations: allOperations,
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
        serverInfo: { name: "local-openapi-mcp-server", version: "2.0.0" },
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
      ok(id, toolResult(await callTool(params?.name)));
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
