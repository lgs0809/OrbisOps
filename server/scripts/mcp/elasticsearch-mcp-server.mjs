#!/usr/bin/env node

import readline from "node:readline";

const ES_HOST = (process.env.ES_HOST || "http://127.0.0.1:9200").replace(/\/$/, "");
const ES_API_KEY = process.env.ES_API_KEY || "";

function write(message) {
  process.stdout.write(`${JSON.stringify(message)}\n`);
}

function ok(id, result = {}) {
  write({ jsonrpc: "2.0", id, result });
}

function fail(id, code, message, data) {
  write({ jsonrpc: "2.0", id, error: { code, message, data } });
}

function headers() {
  const value = { "content-type": "application/json" };
  if (ES_API_KEY && !ES_API_KEY.startsWith("your-")) {
    value.authorization = `ApiKey ${ES_API_KEY}`;
  }
  return value;
}

async function es(path, options = {}) {
  const response = await fetch(`${ES_HOST}${path}`, {
    ...options,
    headers: {
      ...headers(),
      ...(options.headers || {}),
    },
  });

  const text = await response.text();
  let body = text;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    // Elasticsearch cat APIs can return plain text when format is omitted.
  }

  if (!response.ok) {
    const message = typeof body === "object" && body?.error ? JSON.stringify(body.error) : text;
    throw new Error(`Elasticsearch ${response.status}: ${message}`);
  }

  return body;
}

const tools = [
  {
    name: "list_indices",
    description: "Discover Elasticsearch indices with document count, store size, health, and status. Index metadata is not application-log evidence; use search for incident conclusions.",
    inputSchema: {
      type: "object",
      properties: {},
      additionalProperties: false,
    },
  },
  {
    name: "get_mappings",
    description: "Discover field mappings for an Elasticsearch index. Use only when the expected query field is unknown or rejected; mappings alone are not incident evidence.",
    inputSchema: {
      type: "object",
      properties: {
        index: { type: "string", description: "Elasticsearch index name or pattern." },
      },
      required: ["index"],
      additionalProperties: false,
    },
  },
  {
    name: "search",
    description: "Run an Elasticsearch Query DSL search and return actual log documents/aggregations. Scope every project diagnosis by time range and a project identity field such as service.keyword, service, application, or host; discard samples from unrelated services.",
    inputSchema: {
      type: "object",
      properties: {
        index: { type: "string", description: "Elasticsearch index name or pattern." },
        queryBody: { type: "object", description: "Complete Elasticsearch search request body." },
      },
      required: ["index", "queryBody"],
      additionalProperties: false,
    },
  },
];

async function callTool(name, args = {}) {
  if (name === "list_indices") {
    return es("/_cat/indices?format=json&h=index,docs.count,store.size,health,status");
  }

  if (name === "get_mappings") {
    if (!args.index) throw new Error("index is required");
    return es(`/${encodeURIComponent(args.index)}/_mapping`);
  }

  if (name === "search") {
    if (!args.index) throw new Error("index is required");
    if (args.queryBody == null) throw new Error("queryBody is required");
    const body = typeof args.queryBody === "string" ? JSON.parse(args.queryBody) : args.queryBody;
    return es(`/${encodeURIComponent(args.index)}/_search`, {
      method: "POST",
      body: JSON.stringify(body),
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
        capabilities: {
          tools: {},
        },
        serverInfo: {
          name: "local-elasticsearch-mcp-server",
          version: "2.0.0",
        },
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
      const result = await callTool(params?.name, params?.arguments || {});
      ok(id, {
        content: [
          {
            type: "text",
            text: JSON.stringify(result, null, 2),
          },
        ],
        isError: false,
      });
      return;
    }

    fail(id, -32601, `Method not found: ${method}`);
  } catch (error) {
    fail(id, -32000, error.message || "Tool execution failed");
  }
}

const rl = readline.createInterface({
  input: process.stdin,
  crlfDelay: Infinity,
});

rl.on("line", (line) => {
  if (!line.trim()) return;
  try {
    void handle(JSON.parse(line));
  } catch (error) {
    fail(null, -32700, "Parse error", error.message);
  }
});
