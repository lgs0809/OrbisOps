#!/usr/bin/env node

import { execFile } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import readline from "node:readline";

const REPOSITORY_ROOT = fs.realpathSync(process.env.GIT_REPOSITORY_ROOT || ".");
const DEFAULT_COMMIT = process.env.GIT_DEFAULT_COMMIT || "HEAD";
const GIT_BINARY = process.env.GIT_BINARY || "git";
const TIMEOUT_MS = Number(process.env.GIT_TIMEOUT_MS || 8000);
const MAX_FILE_BYTES = Number(process.env.GIT_MAX_FILE_BYTES || 1048576);
const MAX_OUTPUT_BYTES = Number(process.env.GIT_MAX_OUTPUT_BYTES || 2097152);
const REVISION_PATTERN = /^[A-Za-z0-9._/@{}~^+\-]{1,200}$/;

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

function runGit(args, allowNoMatch = false, maxBuffer = MAX_OUTPUT_BYTES) {
  return new Promise((resolve, reject) => {
    execFile(
      GIT_BINARY,
      ["-C", REPOSITORY_ROOT, ...args],
      { timeout: TIMEOUT_MS, maxBuffer },
      (error, stdout, stderr) => {
        if (error && !(allowNoMatch && error.code === 1)) {
          reject(new Error((stderr || error.message || "git command failed").trim()));
          return;
        }
        resolve(String(stdout || ""));
      },
    );
  });
}

function safeRevision(raw) {
  const revision = String(raw || DEFAULT_COMMIT).trim();
  if (!REVISION_PATTERN.test(revision) || revision.startsWith("-") || revision.includes("..") || revision.includes(":")) {
    throw new Error("revision format is invalid");
  }
  return revision;
}

async function resolveCommit(raw) {
  const revision = safeRevision(raw);
  const commit = (await runGit(["rev-parse", "--verify", `${revision}^{commit}`], false, 1024)).trim();
  if (!/^[a-f0-9]{40}$/i.test(commit)) {
    throw new Error("revision does not resolve to a commit");
  }
  return commit.toLowerCase();
}

function safePath(raw, allowEmpty = false) {
  const value = String(raw || "").trim().replaceAll("\\", "/");
  if (allowEmpty && value === "") return "";
  const normalized = path.posix.normalize(value);
  if (
    !normalized ||
    normalized === "." ||
    normalized === ".." ||
    normalized.startsWith("../") ||
    normalized.startsWith("/") ||
    normalized === ".git" ||
    normalized.startsWith(".git/") ||
    /[\r\n\0]/.test(normalized)
  ) {
    throw new Error("path is invalid");
  }
  return normalized;
}

function normalizeLimit(raw, fallback = 50, max = 200) {
  const value = Number.parseInt(String(raw ?? fallback), 10);
  return Number.isFinite(value) && value > 0 ? Math.min(value, max) : fallback;
}

const tools = [
  {
    name: "git_repository_info",
    description: "Return the fixed read-only repository root and exact default commit available to this Agent.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false },
  },
  {
    name: "git_read_file",
    description: "Read one text file from an exact Git commit without using the mutable working tree.",
    inputSchema: {
      type: "object",
      properties: {
        revision: { type: "string", description: "Commit SHA or safe revision. Defaults to the registered commit." },
        path: { type: "string", description: "Repository-relative file path." },
      },
      required: ["path"],
      additionalProperties: false,
    },
  },
  {
    name: "git_search_code",
    description: "Search a fixed string in tracked text files at an exact Git commit.",
    inputSchema: {
      type: "object",
      properties: {
        revision: { type: "string" },
        query: { type: "string", description: "Literal query, 1-200 characters." },
        limit: { type: "integer", description: "Maximum hits, default 50 and max 200." },
      },
      required: ["query"],
      additionalProperties: false,
    },
  },
  {
    name: "git_list_files",
    description: "List tracked files at an exact Git commit, optionally under a safe path prefix.",
    inputSchema: {
      type: "object",
      properties: {
        revision: { type: "string" },
        prefix: { type: "string" },
        limit: { type: "integer", description: "Maximum files, default 100 and max 500." },
      },
      additionalProperties: false,
    },
  },
  {
    name: "git_diff_summary",
    description: "Return a read-only name-status and diff-stat summary between two exact commits.",
    inputSchema: {
      type: "object",
      properties: {
        fromRevision: { type: "string" },
        toRevision: { type: "string" },
      },
      required: ["fromRevision", "toRevision"],
      additionalProperties: false,
    },
  },
];

async function repositoryInfo() {
  const defaultCommit = await resolveCommit(DEFAULT_COMMIT);
  return { repositoryRoot: REPOSITORY_ROOT, defaultCommit, mode: "read_only" };
}

async function readFile(args) {
  const commit = await resolveCommit(args.revision);
  const filePath = safePath(args.path);
  const object = `${commit}:${filePath}`;
  const size = Number.parseInt((await runGit(["cat-file", "-s", object], false, 1024)).trim(), 10);
  if (!Number.isFinite(size) || size < 0 || size > MAX_FILE_BYTES) {
    throw new Error(`file exceeds ${MAX_FILE_BYTES} bytes`);
  }
  const content = await runGit(["show", object], false, MAX_FILE_BYTES + 1);
  if (content.includes("\0")) throw new Error("binary files are not supported");
  return { commit, path: filePath, sizeBytes: size, content };
}

async function searchCode(args) {
  const query = String(args.query || "");
  if (!query.trim() || query.length > 200 || /[\r\n\0]/.test(query)) {
    throw new Error("query must contain 1-200 characters without line breaks");
  }
  const commit = await resolveCommit(args.revision);
  const limit = normalizeLimit(args.limit, 50, 200);
  const stdout = await runGit(["grep", "-n", "-I", "-F", "-e", query, commit, "--"], true);
  const prefix = `${commit}:`;
  const hits = [];
  for (const line of stdout.split(/\r?\n/)) {
    if (!line || hits.length >= limit) continue;
    const value = line.startsWith(prefix) ? line.slice(prefix.length) : line;
    const match = value.match(/^(.+?):(\d+):(.*)$/);
    if (!match) continue;
    hits.push({ path: match[1], line: Number(match[2]), text: match[3] });
  }
  return { commit, query, hitCount: hits.length, hits };
}

async function listFiles(args) {
  const commit = await resolveCommit(args.revision);
  const prefix = safePath(args.prefix, true);
  const limit = normalizeLimit(args.limit, 100, 500);
  const command = ["ls-tree", "-r", "--name-only", commit];
  if (prefix) command.push("--", prefix);
  const files = (await runGit(command))
    .split(/\r?\n/)
    .filter(Boolean)
    .slice(0, limit);
  return { commit, prefix: prefix || null, fileCount: files.length, files };
}

async function diffSummary(args) {
  const fromCommit = await resolveCommit(args.fromRevision);
  const toCommit = await resolveCommit(args.toRevision);
  const nameStatus = await runGit(["diff", "--name-status", fromCommit, toCommit, "--"]);
  const stat = await runGit(["diff", "--stat", fromCommit, toCommit, "--"]);
  return { fromCommit, toCommit, nameStatus, stat };
}

async function callTool(name, args = {}) {
  if (name === "git_repository_info") return repositoryInfo();
  if (name === "git_read_file") return readFile(args);
  if (name === "git_search_code") return searchCode(args);
  if (name === "git_list_files") return listFiles(args);
  if (name === "git_diff_summary") return diffSummary(args);
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
        serverInfo: { name: "local-git-readonly-mcp-server", version: "2.0.0" },
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
