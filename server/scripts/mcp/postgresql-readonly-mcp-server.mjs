#!/usr/bin/env node

import { execFile } from "node:child_process";
import readline from "node:readline";

const HOST = process.env.POSTGRES_HOST || "127.0.0.1";
const PORT = process.env.POSTGRES_PORT || "5432";
const USER = process.env.POSTGRES_USER || "postgres";
const PASSWORD = process.env.POSTGRES_PASSWORD || "";
const DATABASE = process.env.POSTGRES_DATABASE || "postgres";
const PSQL = process.env.POSTGRES_CLI || "psql";
const TIMEOUT_MS = Number(process.env.POSTGRES_TIMEOUT_MS || 15000);
const MAX_LIMIT = Number(process.env.POSTGRES_MCP_MAX_LIMIT || 100);

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
  return Number.isFinite(parsed) && parsed > 0 ? Math.min(parsed, MAX_LIMIT) : fallback;
}

function quote(value) {
  return `'${String(value ?? "").replace(/'/g, "''")}'`;
}

function identifier(value, label) {
  const text = String(value || "").trim();
  if (!/^[A-Za-z_][A-Za-z0-9_$]*$/.test(text)) {
    throw new Error(`${label} contains unsupported characters`);
  }
  return `"${text.replace(/"/g, '""')}"`;
}

function parseTsv(stdout) {
  const lines = stdout.split(/\r?\n/).filter(Boolean);
  if (!lines.length) return [];
  const headers = lines[0].split("\t");
  return lines.slice(1).map((line) => {
    const cells = line.split("\t");
    return Object.fromEntries(headers.map((header, index) => [header, cells[index] ?? null]));
  });
}

async function query(sql) {
  const args = [
    "-X",
    "-h", HOST,
    "-p", PORT,
    "-U", USER,
    "-d", DATABASE,
    "-A",
    "-F", "\t",
    "-P", "footer=off",
    "-c", sql,
  ];
  return new Promise((resolve, reject) => {
    execFile(
      PSQL,
      args,
      {
        timeout: TIMEOUT_MS,
        maxBuffer: 2 * 1024 * 1024,
        env: { ...process.env, PGPASSWORD: PASSWORD },
      },
      (error, stdout, stderr) => {
        if (error) {
          reject(new Error(stderr?.trim() || error.message || "psql command failed"));
          return;
        }
        resolve(parseTsv(stdout));
      },
    );
  });
}

const tools = [
  {
    name: "postgresql_health",
    description: "Check read-only PostgreSQL connectivity and pg_stat_statements availability.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false },
  },
  {
    name: "query_pg_stat_statements",
    description: "Read high-cost SQL summaries from pg_stat_statements.",
    inputSchema: {
      type: "object",
      properties: {
        minMeanExecMs: { type: "number", description: "Minimum mean execution time in milliseconds." },
        limit: { type: "integer", description: "Maximum rows." },
      },
      additionalProperties: false,
    },
  },
  {
    name: "show_postgresql_indexes",
    description: "Read index definitions for one PostgreSQL table.",
    inputSchema: {
      type: "object",
      properties: {
        schema: { type: "string" },
        table: { type: "string" },
      },
      required: ["table"],
      additionalProperties: false,
    },
  },
  {
    name: "explain_postgresql_select",
    description: "Run EXPLAIN (FORMAT JSON) for one read-only SELECT statement.",
    inputSchema: {
      type: "object",
      properties: { sql: { type: "string" } },
      required: ["sql"],
      additionalProperties: false,
    },
  },
];

async function health() {
  const rows = await query(`
    SELECT current_database() AS database,
           current_user AS username,
           EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'pg_stat_statements') AS pg_stat_statements_available
  `);
  return { host: HOST, port: PORT, checks: rows[0] || {} };
}

async function queryStatements(args) {
  const threshold = Math.max(0, Number(args.minMeanExecMs ?? 100));
  const limit = normalizeLimit(args.limit);
  const rows = await query(`
    SELECT queryid,
           calls,
           ROUND(mean_exec_time::numeric, 2) AS mean_exec_time_ms,
           ROUND(max_exec_time::numeric, 2) AS max_exec_time_ms,
           rows,
           LEFT(query, 2000) AS query
    FROM pg_stat_statements
    WHERE mean_exec_time >= ${threshold}
    ORDER BY max_exec_time DESC
    LIMIT ${limit}
  `);
  return { source: "pg_stat_statements", threshold, limit, rowCount: rows.length, rows };
}

async function showIndexes(args) {
  const schema = String(args.schema || "public").trim();
  const table = String(args.table || "").trim();
  identifier(schema, "schema");
  identifier(table, "table");
  const rows = await query(`
    SELECT schemaname, tablename, indexname, indexdef
    FROM pg_indexes
    WHERE schemaname = ${quote(schema)}
      AND tablename = ${quote(table)}
    ORDER BY indexname
  `);
  return { source: "pg_indexes", schema, table, rowCount: rows.length, rows };
}

async function explainSelect(args) {
  const sql = String(args.sql || "").trim().replace(/;+\s*$/, "");
  if (!/^(select|with)\s+/i.test(sql)) {
    throw new Error("Only SELECT or WITH queries can be explained");
  }
  if (sql.length > 8000 || /;\s*\S/.test(sql)) {
    throw new Error("Only one query can be explained");
  }
  if (/\b(for\s+(update|share)|copy\s+|pg_sleep\s*\()/i.test(sql)) {
    throw new Error("Query contains unsupported or unsafe clauses");
  }
  const rows = await query(`EXPLAIN (FORMAT JSON, COSTS TRUE, VERBOSE FALSE) ${sql}`);
  return { source: "EXPLAIN FORMAT JSON", rowCount: rows.length, rows };
}

async function callTool(name, args = {}) {
  if (name === "postgresql_health") return health();
  if (name === "query_pg_stat_statements") return queryStatements(args);
  if (name === "show_postgresql_indexes") return showIndexes(args);
  if (name === "explain_postgresql_select") return explainSelect(args);
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
        serverInfo: { name: "local-postgresql-readonly-mcp-server", version: "2.0.0" },
      });
      return;
    }
    if (method === "notifications/initialized") return;
    if (method === "ping") return ok(id, {});
    if (method === "tools/list") return ok(id, { tools });
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
