#!/usr/bin/env node

import { execFile } from "node:child_process";
import readline from "node:readline";

const MYSQL_MCP_MODE = (process.env.MYSQL_MCP_MODE || "docker").toLowerCase();
const MYSQL_CONTAINER = process.env.MYSQL_CONTAINER || "orbisops-mysql";
const MYSQL_HOST = process.env.MYSQL_HOST || "127.0.0.1";
const MYSQL_PORT = process.env.MYSQL_PORT || "13306";
const MYSQL_USER = String(process.env.MYSQL_USER || "").trim();
const MYSQL_PASSWORD = process.env.MYSQL_PASSWORD || "";
const MYSQL_DATABASE = process.env.MYSQL_DATABASE || "";
const MYSQL_CLI = process.env.MYSQL_CLI || "mysql";
const MYSQL_TIMEOUT_MS = Number(process.env.MYSQL_TIMEOUT_MS || 15000);
const MAX_LIMIT = Number(process.env.MYSQL_MCP_MAX_LIMIT || 50);
const MYSQL_MCP_ALLOW_PRIVILEGED_USER =
  String(process.env.MYSQL_MCP_ALLOW_PRIVILEGED_USER || "false").toLowerCase() === "true";
const MYSQL_MCP_ALLOW_EXPLAIN_SELECT =
  String(process.env.MYSQL_MCP_ALLOW_EXPLAIN_SELECT || "false").toLowerCase() === "true";
const MYSQL_MCP_ALLOWED_DATABASES = csvSet(process.env.MYSQL_MCP_ALLOWED_DATABASES);
const MYSQL_MCP_ALLOWED_TABLES = csvSet(process.env.MYSQL_MCP_ALLOWED_TABLES);

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
    content: [
      {
        type: "text",
        text: JSON.stringify(value, null, 2),
      },
    ],
    isError: false,
  };
}

function normalizeLimit(value, fallback = 10) {
  const parsed = Number.parseInt(String(value ?? fallback), 10);
  if (!Number.isFinite(parsed) || parsed <= 0) return fallback;
  return Math.min(parsed, MAX_LIMIT);
}

function csvSet(value) {
  return new Set(
    String(value || "")
      .split(",")
      .map((item) => item.trim().toLowerCase())
      .filter(Boolean),
  );
}

function assertConnectionPolicy() {
  if (!MYSQL_USER) throw new Error("MYSQL_MCP_READONLY_USER_REQUIRED");
  if (!MYSQL_PASSWORD) throw new Error("MYSQL_MCP_PASSWORD_REQUIRED");
  if (!MYSQL_MCP_ALLOW_PRIVILEGED_USER && /^(root|admin|administrator|mysql\.sys)$/i.test(MYSQL_USER)) {
    throw new Error("MYSQL_MCP_PRIVILEGED_USER_FORBIDDEN");
  }
}

function requireAllowedDatabase(value) {
  const database = String(value || "").trim();
  if (!database) throw new Error("MYSQL_MCP_DATABASE_REQUIRED");
  if (MYSQL_MCP_ALLOWED_DATABASES.size === 0) {
    throw new Error("MYSQL_MCP_DATABASE_ALLOWLIST_REQUIRED");
  }
  if (!MYSQL_MCP_ALLOWED_DATABASES.has(database.toLowerCase())) {
    throw new Error("MYSQL_MCP_DATABASE_FORBIDDEN");
  }
  return database;
}

function requireAllowedTable(database, value) {
  const table = String(value || "").trim();
  if (!table) throw new Error("MYSQL_MCP_TABLE_REQUIRED");
  if (MYSQL_MCP_ALLOWED_TABLES.size === 0) {
    throw new Error("MYSQL_MCP_TABLE_ALLOWLIST_REQUIRED");
  }
  const identity = `${database}.${table}`.toLowerCase();
  if (!MYSQL_MCP_ALLOWED_TABLES.has(identity)) {
    throw new Error("MYSQL_MCP_TABLE_FORBIDDEN");
  }
  return table;
}

function allowedDatabaseSql() {
  if (MYSQL_MCP_ALLOWED_DATABASES.size === 0) {
    throw new Error("MYSQL_MCP_DATABASE_ALLOWLIST_REQUIRED");
  }
  return [...MYSQL_MCP_ALLOWED_DATABASES].map(quote).join(", ");
}

function validateExplainTables(sql, defaultDatabase) {
  if (/\bwith\b|\bunion\b|\/\*|--|#|\(\s*select\b/i.test(sql)) {
    throw new Error("MYSQL_MCP_COMPLEX_SELECT_FORBIDDEN");
  }
  if (/\bfrom\b[^;]*,/i.test(sql)) {
    throw new Error("MYSQL_MCP_COMMA_JOIN_FORBIDDEN");
  }
  const references = [...sql.matchAll(/\b(?:from|join)\s+(`?[A-Za-z0-9_$]+`?(?:\s*\.\s*`?[A-Za-z0-9_$]+`?)?)/gi)];
  for (const reference of references) {
    const identity = reference[1].replaceAll("`", "").replace(/\s+/g, "");
    const parts = identity.split(".");
    const database = parts.length === 2 ? requireAllowedDatabase(parts[0]) : defaultDatabase;
    requireAllowedTable(database, parts.length === 2 ? parts[1] : parts[0]);
  }
}

function normalizeRangeMinutes(value, fallback = 30) {
  const parsed = Number.parseInt(String(value ?? fallback), 10);
  if (!Number.isFinite(parsed) || parsed <= 0) return fallback;
  return Math.min(parsed, 1440);
}

function normalizeThresholdMs(value, fallback = 500) {
  const parsed = Number(value ?? fallback);
  if (!Number.isFinite(parsed) || parsed < 0) return fallback;
  return Math.min(parsed, 600000);
}

function quote(value) {
  return `'${String(value ?? "").replace(/'/g, "''")}'`;
}

function ident(value, label) {
  const text = String(value || "").trim();
  if (!/^[A-Za-z0-9_$]+$/.test(text)) {
    throw new Error(`${label} contains unsupported characters`);
  }
  return `\`${text.replace(/`/g, "``")}\``;
}

function parseTsv(stdout) {
  const lines = stdout.split(/\r?\n/).filter((line) => line.length > 0);
  if (lines.length === 0) return [];
  const headers = lines[0].split("\t");
  return lines.slice(1).map((line) => {
    const cells = line.split("\t");
    const row = {};
    headers.forEach((header, index) => {
      const value = cells[index] ?? "";
      row[header] = value === "NULL" ? null : value;
    });
    return row;
  });
}

function mysqlArgs(sql, database = "") {
  const args = [
    "--batch",
    "--raw",
    "--default-character-set=utf8mb4",
    "--connect-timeout=5",
  ];
  if (MYSQL_USER) args.push(`-u${MYSQL_USER}`);
  if (MYSQL_PASSWORD) args.push(`-p${MYSQL_PASSWORD}`);
  const selectedDatabase = database || MYSQL_DATABASE;
  if (selectedDatabase) args.push("-D", selectedDatabase);
  args.push("-e", sql);
  return args;
}

function commandFor(sql, database = "") {
  if (MYSQL_MCP_MODE === "native") {
    return {
      command: MYSQL_CLI,
      args: ["-h", MYSQL_HOST, "-P", MYSQL_PORT, ...mysqlArgs(sql, database)],
    };
  }
  return {
    command: "docker",
    args: ["exec", "-i", MYSQL_CONTAINER, "mysql", ...mysqlArgs(sql, database)],
  };
}

async function runMysql(sql, database = "") {
  assertConnectionPolicy();
  const { command, args } = commandFor(sql, database);
  return new Promise((resolve, reject) => {
    execFile(command, args, { timeout: MYSQL_TIMEOUT_MS, maxBuffer: 2 * 1024 * 1024 }, (error, stdout, stderr) => {
      if (error) {
        const message = stderr ? stderr.trim() : error.message;
        reject(new Error(message || "mysql command failed"));
        return;
      }
      resolve(stdout);
    });
  });
}

async function queryRows(sql, database = "") {
  return parseTsv(await runMysql(sql, database));
}

let readonlyAccountPromise;
async function ensureReadonlyAccount() {
  if (!readonlyAccountPromise) {
    readonlyAccountPromise = queryRows("SHOW GRANTS FOR CURRENT_USER()")
      .then((rows) => {
        const grants = rows
          .flatMap((row) => Object.values(row))
          .map((value) => String(value || ""))
          .join("\n");
        if (!grants) throw new Error("MYSQL_MCP_GRANTS_UNAVAILABLE");
        if (/\b(ALL PRIVILEGES|INSERT|UPDATE|DELETE|CREATE|DROP|ALTER|TRUNCATE|EXECUTE|TRIGGER|EVENT|REFERENCES|LOCK TABLES)\b/i.test(grants)) {
          throw new Error("MYSQL_MCP_ACCOUNT_NOT_READONLY");
        }
        if (!/\bSELECT\b/i.test(grants)) {
          throw new Error("MYSQL_MCP_SELECT_GRANT_REQUIRED");
        }
        return true;
      })
      .catch((error) => {
        readonlyAccountPromise = undefined;
        throw error;
      });
  }
  return readonlyAccountPromise;
}

const tools = [
  {
    name: "mysql_health",
    description: "Check read-only MySQL connectivity and whether mysql.slow_log/performance_schema digest tables are available.",
    inputSchema: {
      type: "object",
      properties: {},
      additionalProperties: false,
    },
  },
  {
    name: "query_slow_log",
    description: "Read recent rows from mysql.slow_log by time window and threshold. Read-only evidence collection for slow SQL diagnosis.",
    inputSchema: {
      type: "object",
      properties: {
        rangeMinutes: { type: "integer", description: "Lookback window in minutes. Defaults to 30, max 1440." },
        thresholdMs: { type: "number", description: "Minimum query_time in milliseconds. Defaults to 500." },
        limit: { type: "integer", description: "Maximum rows to return. Defaults to 10, capped by MYSQL_MCP_MAX_LIMIT." },
      },
      additionalProperties: false,
    },
  },
  {
    name: "query_statement_digest",
    description: "Read performance_schema.events_statements_summary_by_digest ordered by max latency. Read-only fallback for high-cost SQL summaries.",
    inputSchema: {
      type: "object",
      properties: {
        schemaName: { type: "string", description: "Optional schema name filter." },
        thresholdMs: { type: "number", description: "Minimum MAX_TIMER_WAIT in milliseconds. Defaults to 500." },
        limit: { type: "integer", description: "Maximum rows to return. Defaults to 10, capped by MYSQL_MCP_MAX_LIMIT." },
      },
      additionalProperties: false,
    },
  },
  {
    name: "show_table_indexes",
    description: "Show indexes for one table. Read-only metadata query.",
    inputSchema: {
      type: "object",
      properties: {
        database: { type: "string", description: "Database/schema name." },
        table: { type: "string", description: "Table name." },
      },
      required: ["database", "table"],
      additionalProperties: false,
    },
  },
  {
    name: "explain_select",
    description: "Run EXPLAIN FORMAT=JSON for a read-only SELECT statement. It never executes DML/DDL.",
    inputSchema: {
      type: "object",
      properties: {
        database: { type: "string", description: "Optional database/schema context." },
        sql: { type: "string", description: "A SELECT statement to explain." },
      },
      required: ["sql"],
      additionalProperties: false,
    },
  },
];

async function mysqlHealth() {
  await ensureReadonlyAccount();
  const rows = await queryRows(`
    SELECT
      1 AS ok,
      EXISTS(
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'mysql' AND table_name = 'slow_log'
      ) AS slow_log_available,
      EXISTS(
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'performance_schema' AND table_name = 'events_statements_summary_by_digest'
      ) AS statement_digest_available
  `);
  return {
    mode: MYSQL_MCP_MODE,
    container: MYSQL_MCP_MODE === "native" ? null : MYSQL_CONTAINER,
    host: MYSQL_MCP_MODE === "native" ? MYSQL_HOST : null,
    port: MYSQL_MCP_MODE === "native" ? MYSQL_PORT : null,
    checks: rows[0] || {},
  };
}

async function querySlowLog(args) {
  const rangeMinutes = normalizeRangeMinutes(args.rangeMinutes);
  const thresholdMs = normalizeThresholdMs(args.thresholdMs);
  const limit = normalizeLimit(args.limit);
  const allowedDatabases = allowedDatabaseSql();
  await ensureReadonlyAccount();
  const rows = await queryRows(`
    SELECT DATE_FORMAT(start_time, '%Y-%m-%d %H:%i:%s') AS start_time,
           db AS database_name,
           user_host,
           sql_text,
           ROUND(TIME_TO_SEC(query_time) * 1000, 2) AS query_time_ms,
           rows_examined,
           rows_sent
    FROM mysql.slow_log
    WHERE start_time >= DATE_SUB(NOW(), INTERVAL ${rangeMinutes} MINUTE)
      AND db IN (${allowedDatabases})
      AND TIME_TO_SEC(query_time) * 1000 >= ${thresholdMs}
    ORDER BY query_time DESC
    LIMIT ${limit}
  `);
  return {
    source: "mysql.slow_log",
    rangeMinutes,
    thresholdMs,
    limit,
    rowCount: rows.length,
    rows,
  };
}

async function queryStatementDigest(args) {
  const thresholdMs = normalizeThresholdMs(args.thresholdMs);
  const limit = normalizeLimit(args.limit);
  const schemaName = args.schemaName ? requireAllowedDatabase(args.schemaName) : "";
  const schemaFilter = schemaName
    ? `AND SCHEMA_NAME = ${quote(schemaName)}`
    : `AND SCHEMA_NAME IN (${allowedDatabaseSql()})`;
  await ensureReadonlyAccount();
  const rows = await queryRows(`
    SELECT SCHEMA_NAME AS database_name,
           DIGEST AS digest,
           DIGEST_TEXT AS sql_text,
           COUNT_STAR AS count_star,
           ROUND(AVG_TIMER_WAIT / 1000000000, 2) AS avg_query_time_ms,
           ROUND(MAX_TIMER_WAIT / 1000000000, 2) AS max_query_time_ms,
           SUM_ROWS_EXAMINED AS rows_examined,
           SUM_ROWS_SENT AS rows_sent
    FROM performance_schema.events_statements_summary_by_digest
    WHERE DIGEST_TEXT IS NOT NULL
      AND MAX_TIMER_WAIT / 1000000000 >= ${thresholdMs}
      ${schemaFilter}
    ORDER BY MAX_TIMER_WAIT DESC
    LIMIT ${limit}
  `);
  return {
    source: "performance_schema.events_statements_summary_by_digest",
    thresholdMs,
    limit,
    rowCount: rows.length,
    rows,
  };
}

async function showTableIndexes(args) {
  const databaseName = requireAllowedDatabase(args.database);
  const tableName = requireAllowedTable(databaseName, args.table);
  const database = ident(databaseName, "database");
  const table = ident(tableName, "table");
  await ensureReadonlyAccount();
  const rows = await queryRows(`SHOW INDEX FROM ${database}.${table}`);
  return {
    source: "information_schema/statistics",
    database: databaseName,
    table: tableName,
    rowCount: rows.length,
    rows,
  };
}

async function explainSelect(args) {
  if (!MYSQL_MCP_ALLOW_EXPLAIN_SELECT) {
    throw new Error("MYSQL_MCP_EXPLAIN_DISABLED");
  }
  const sql = String(args.sql || "").trim().replace(/;+\s*$/, "");
  if (!/^select\s+/i.test(sql)) {
    throw new Error("MYSQL_MCP_ONLY_SELECT_ALLOWED");
  }
  if (sql.length > 8000 || /;\s*\S/.test(sql)) {
    throw new Error("MYSQL_MCP_SINGLE_SELECT_REQUIRED");
  }
  if (/\b(into\s+(outfile|dumpfile)|for\s+update|lock\s+in\s+share\s+mode|sleep\s*\(|benchmark\s*\()/i.test(sql)) {
    throw new Error("MYSQL_MCP_UNSAFE_SELECT_FORBIDDEN");
  }
  const databaseName = requireAllowedDatabase(args.database);
  validateExplainTables(sql, databaseName);
  await ensureReadonlyAccount();
  const rows = await queryRows(`EXPLAIN FORMAT=JSON ${sql}`, databaseName);
  return {
    source: "EXPLAIN FORMAT=JSON",
    database: databaseName,
    rowCount: rows.length,
    rows,
  };
}

async function callTool(name, args = {}) {
  if (name === "mysql_health") return mysqlHealth();
  if (name === "query_slow_log") return querySlowLog(args);
  if (name === "query_statement_digest") return queryStatementDigest(args);
  if (name === "show_table_indexes") return showTableIndexes(args);
  if (name === "explain_select") return explainSelect(args);
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
        serverInfo: {
          name: "local-mysql-readonly-mcp-server",
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
      ok(id, toolResult(await callTool(params?.name, params?.arguments || {})));
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
