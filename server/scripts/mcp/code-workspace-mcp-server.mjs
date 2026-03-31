#!/usr/bin/env node

import { spawn, spawnSync } from "node:child_process";
import crypto from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import readline from "node:readline";

const SERVER_VERSION = "2.0.0";
const GIT_BINARY = process.env.CODE_WORKSPACE_GIT_BINARY || "git";
const STATE_ROOT = absolute(process.env.CODE_WORKSPACE_STATE_ROOT || path.join(os.tmpdir(), "orbisops-code-workspace-mcp"));
const WORKTREE_ROOT = path.join(STATE_ROOT, "worktrees");
const ANALYSIS_ROOT = path.join(STATE_ROOT, "analysis");
const PROCESS_ROOT = path.join(STATE_ROOT, "processes");
const LSP_DATA_ROOT = path.join(STATE_ROOT, "lsp-data");
const LSP_RUNTIME_ROOT = path.join(STATE_ROOT, "runtimes");
const MAX_FILE_BYTES = boundedInt(process.env.CODE_WORKSPACE_MAX_FILE_BYTES, 1024 * 1024, 1024, 16 * 1024 * 1024);
const MAX_OUTPUT_BYTES = boundedInt(process.env.CODE_WORKSPACE_MAX_OUTPUT_BYTES, 256 * 1024, 1024, 4 * 1024 * 1024);
const MAX_LOG_BYTES = boundedInt(process.env.CODE_WORKSPACE_MAX_LOG_BYTES, 1024 * 1024, 4096, 16 * 1024 * 1024);
const DEFAULT_TIMEOUT_MS = boundedInt(process.env.CODE_WORKSPACE_TIMEOUT_MS, 30_000, 1000, 300_000);
const BACKGROUND_TTL_MS = boundedInt(process.env.CODE_WORKSPACE_BACKGROUND_TTL_MS, 30 * 60_000, 5000, 24 * 60 * 60_000);
const LSP_TIMEOUT_MS = boundedInt(process.env.CODE_WORKSPACE_LSP_TIMEOUT_MS, 30_000, 1000, 120_000);
const SUPPORTED_JDTLS_VERSIONS = new Set(["1.43.0", "1.42.0"]);
const SUPPORTED_PYRIGHT_VERSIONS = new Set(["1.1.410", "1.1.409", "1.1.408"]);
const LSP_LANGUAGES = configuredLspLanguages();
const JDTLS_AUTO_INSTALL = LSP_LANGUAGES.has("java")
  && !["0", "false", "no", "off"].includes(text(process.env.CODE_WORKSPACE_JDTLS_AUTO_INSTALL || "true").toLowerCase());
const JDTLS_VERSION = text(process.env.CODE_WORKSPACE_JDTLS_VERSION || "1.43.0");
const JDTLS_DOWNLOAD_BASE_URL = text(process.env.CODE_WORKSPACE_JDTLS_DOWNLOAD_BASE_URL || `https://download.eclipse.org/jdtls/milestones/${JDTLS_VERSION}`);
const JDTLS_DOWNLOAD_TIMEOUT_MS = boundedInt(process.env.CODE_WORKSPACE_JDTLS_DOWNLOAD_TIMEOUT_MS, 120_000, 10_000, 600_000);
const JDTLS_MAX_ARCHIVE_BYTES = boundedInt(process.env.CODE_WORKSPACE_JDTLS_MAX_ARCHIVE_BYTES, 128 * 1024 * 1024, 32 * 1024 * 1024, 512 * 1024 * 1024);
const PYRIGHT_AUTO_INSTALL = LSP_LANGUAGES.has("python")
  && !["0", "false", "no", "off"].includes(text(process.env.CODE_WORKSPACE_PYRIGHT_AUTO_INSTALL || "true").toLowerCase());
const PYRIGHT_VERSION = text(process.env.CODE_WORKSPACE_PYRIGHT_VERSION || "1.1.410");
const PYRIGHT_REGISTRY_URL = text(process.env.CODE_WORKSPACE_PYRIGHT_REGISTRY_URL || "https://registry.npmjs.org/pyright");
const PYRIGHT_DOWNLOAD_TIMEOUT_MS = boundedInt(process.env.CODE_WORKSPACE_PYRIGHT_DOWNLOAD_TIMEOUT_MS, 120_000, 10_000, 600_000);
const PYRIGHT_MAX_ARCHIVE_BYTES = boundedInt(process.env.CODE_WORKSPACE_PYRIGHT_MAX_ARCHIVE_BYTES, 64 * 1024 * 1024, 8 * 1024 * 1024, 256 * 1024 * 1024);
const REVISION_PATTERN = /^[A-Za-z0-9._/@{}~^+\-]{1,200}$/;
const WORKSPACE_PATTERN = /^[A-Za-z0-9._\-]{1,160}$/;
const REPOSITORY_PATTERN = /^[A-Za-z0-9._\-]{1,160}$/;
const SHA256_PATTERN = /^[a-f0-9]{64}$/i;
const COMMIT_PATTERN = /^[a-f0-9]{40}$/i;
const SENSITIVE_NAMES = /^(?:\.env(?:\..*)?|credentials?|id_(?:rsa|dsa|ecdsa|ed25519)|.*\.(?:pem|key|p12|pfx|jks|keystore))$/i;
const SECRET_LINE = /(?:password|passwd|pwd|secret|token|access[_-]?key|secret[_-]?key|private[_-]?key|api[_-]?key|credential|authorization|bearer|jwt|session|cookie)\s*[:=]\s*[^\s]+/gi;

fs.mkdirSync(WORKTREE_ROOT, { recursive: true });
fs.mkdirSync(ANALYSIS_ROOT, { recursive: true });
fs.mkdirSync(PROCESS_ROOT, { recursive: true });
fs.mkdirSync(LSP_DATA_ROOT, { recursive: true });
fs.mkdirSync(path.join(LSP_RUNTIME_ROOT, "jdtls"), { recursive: true });
fs.mkdirSync(path.join(LSP_RUNTIME_ROOT, "pyright"), { recursive: true });

const repositories = loadRepositories();
const workspaces = new Map();
const executions = new Map();
const lspSessions = new Map();
const analysisRoots = new Map();

function absolute(value) {
  return path.resolve(String(value || "."));
}

function boundedInt(raw, fallback, min, max) {
  const value = Number.parseInt(String(raw ?? fallback), 10);
  return Number.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
}

function sha256(value) {
  return crypto.createHash("sha256").update(value ?? "").digest("hex");
}

function text(value) {
  return value == null ? "" : String(value).trim();
}

function configuredLspLanguages() {
  const configured = text(process.env.CODE_WORKSPACE_LSP_LANGUAGES).toLowerCase();
  const values = new Set();
  if (configured) {
    for (const raw of configured.split(/[\s,;]+/)) {
      const language = text(raw).toLowerCase();
      if (!language) continue;
      if (language === "java" || language === "python") values.add(language);
      else throw new Error(`unsupported CODE_WORKSPACE_LSP_LANGUAGES entry: ${language}`);
    }
  }
  if (text(process.env.CODE_WORKSPACE_JDTLS_COMMAND)) values.add("java");
  if (text(process.env.CODE_WORKSPACE_PYRIGHT_COMMAND)) values.add("python");
  return values;
}

function maskSecrets(value) {
  return String(value ?? "").replace(SECRET_LINE, (current) => {
    const eq = current.indexOf("=");
    const colon = current.indexOf(":");
    const split = eq < 0 ? colon : colon < 0 ? eq : Math.min(eq, colon);
    return split < 0 ? "***" : `${current.slice(0, split + 1)}***`;
  });
}

function loadRepositories() {
  const result = new Map();
  const json = text(process.env.CODE_WORKSPACE_REPOSITORIES_JSON);
  if (json) {
    const parsed = JSON.parse(json);
    for (const [id, raw] of Object.entries(parsed || {})) {
      const repositoryId = safeRepositoryId(id);
      const root = fs.realpathSync(absolute(typeof raw === "string" ? raw : raw?.root));
      assertGitRepository(root);
      result.set(repositoryId, { repositoryId, root });
    }
  }
  const singleRoot = text(process.env.CODE_WORKSPACE_REPOSITORY_ROOT);
  if (singleRoot) {
    const repositoryId = safeRepositoryId(process.env.CODE_WORKSPACE_REPOSITORY_ID || "default");
    const root = fs.realpathSync(absolute(singleRoot));
    assertGitRepository(root);
    result.set(repositoryId, { repositoryId, root });
  }
  if (result.size === 0) {
    throw new Error("CODE_WORKSPACE_REPOSITORY_ROOT or CODE_WORKSPACE_REPOSITORIES_JSON is required");
  }
  return result;
}

function assertGitRepository(root) {
  if (!fs.statSync(root).isDirectory()) throw new Error(`repository root is not a directory: ${root}`);
  const gitMarker = path.join(root, ".git");
  if (!fs.existsSync(gitMarker)) throw new Error(`repository root is not a Git worktree: ${root}`);
}

function safeRepositoryId(raw) {
  const value = text(raw);
  if (!REPOSITORY_PATTERN.test(value)) throw new Error("repositoryId is invalid");
  return value;
}

function repository(raw) {
  const id = safeRepositoryId(raw || (repositories.size === 1 ? [...repositories.keys()][0] : ""));
  const repo = repositories.get(id);
  if (!repo) throw new Error(`repositoryId is not registered: ${id}`);
  return repo;
}

function safeWorkspaceId(raw) {
  const value = text(raw);
  if (!WORKSPACE_PATTERN.test(value)) throw new Error("workspaceId is invalid");
  return value;
}

function safeRevision(raw) {
  const revision = text(raw || "HEAD");
  if (!REVISION_PATTERN.test(revision) || revision.startsWith("-") || revision.includes("..") || revision.includes(":")) {
    throw new Error("revision format is invalid");
  }
  return revision;
}

function safeRelativePath(raw, allowEmpty = false) {
  const value = text(raw).replaceAll("\\", "/");
  if (allowEmpty && !value) return "";
  const normalized = path.posix.normalize(value);
  if (!normalized || normalized === "." || normalized === ".." || normalized.startsWith("../") ||
      normalized.startsWith("/") || normalized === ".git" || normalized.startsWith(".git/") || /[\r\n\0]/.test(normalized)) {
    throw new Error("path is invalid");
  }
  const name = path.posix.basename(normalized);
  if (SENSITIVE_NAMES.test(name)) throw new Error(`sensitive file is blocked: ${normalized}`);
  return normalized;
}

function globMatches(relativePath, pattern) {
  if (pattern === "**/*") return true;
  let regex = "^";
  for (let i = 0; i < pattern.length; i += 1) {
    const char = pattern[i];
    if (char === "*") {
      if (pattern[i + 1] === "*") {
        i += 1;
        if (pattern[i + 1] === "/") {
          i += 1;
          regex += "(?:.*/)?";
        } else {
          regex += ".*";
        }
      } else {
        regex += "[^/]*";
      }
    } else if (char === "?") {
      regex += "[^/]";
    } else if ("\\^$+.()|{}[]".includes(char)) {
      regex += `\\${char}`;
    } else {
      regex += char;
    }
  }
  regex += "$";
  return new RegExp(regex).test(relativePath);
}

function assertContained(root, candidate) {
  const normalizedRoot = path.resolve(root);
  const normalized = path.resolve(candidate);
  if (normalized !== normalizedRoot && !normalized.startsWith(`${normalizedRoot}${path.sep}`)) {
    throw new Error("path escapes allowed workspace root");
  }
  return normalized;
}

function resolveExistingFile(root, relativePath) {
  const safe = safeRelativePath(relativePath);
  const candidate = assertContained(root, path.join(root, safe));
  const real = fs.realpathSync(candidate);
  assertContained(fs.realpathSync(root), real);
  const stat = fs.statSync(real);
  if (!stat.isFile()) throw new Error("path is not a regular file");
  if (stat.size > MAX_FILE_BYTES) throw new Error(`file exceeds ${MAX_FILE_BYTES} bytes`);
  return real;
}

function resolveWritableFile(root, relativePath) {
  const safe = safeRelativePath(relativePath);
  const normalizedRoot = fs.realpathSync(root);
  const candidate = assertContained(normalizedRoot, path.join(normalizedRoot, safe));
  let cursor = normalizedRoot;
  const parts = safe.split("/");
  for (let i = 0; i < parts.length - 1; i += 1) {
    cursor = path.join(cursor, parts[i]);
    if (!fs.existsSync(cursor)) break;
    const stat = fs.lstatSync(cursor);
    if (stat.isSymbolicLink()) throw new Error("symlink path component is not writable");
    const real = fs.realpathSync(cursor);
    assertContained(normalizedRoot, real);
  }
  if (fs.existsSync(candidate) && fs.lstatSync(candidate).isSymbolicLink()) {
    throw new Error("symlink file is not writable");
  }
  return candidate;
}

async function runProcess(command, args, options = {}) {
  const timeoutMs = boundedInt(options.timeoutMs, DEFAULT_TIMEOUT_MS, 1000, 300_000);
  const outputLimit = boundedInt(options.outputLimit, MAX_OUTPUT_BYTES, 1024, 16 * 1024 * 1024);
  const cwd = options.cwd ? absolute(options.cwd) : process.cwd();
  const started = Date.now();
  return new Promise((resolve, reject) => {
    const child = spawn(command, args || [], {
      cwd,
      env: options.env || process.env,
      stdio: [options.input == null ? "ignore" : "pipe", "pipe", "pipe"],
      detached: process.platform !== "win32",
    });
    let stdout = Buffer.alloc(0);
    let stderr = Buffer.alloc(0);
    let truncated = false;
    const append = (current, chunk) => {
      const available = Math.max(0, outputLimit - current.length);
      if (chunk.length > available) truncated = true;
      return available > 0 ? Buffer.concat([current, chunk.subarray(0, available)]) : current;
    };
    child.stdout.on("data", (chunk) => { stdout = append(stdout, Buffer.from(chunk)); });
    child.stderr.on("data", (chunk) => { stderr = append(stderr, Buffer.from(chunk)); });
    child.on("error", reject);
    if (options.input != null) {
      child.stdin.write(options.input);
      child.stdin.end();
    }
    let timedOut = false;
    const timer = setTimeout(() => {
      timedOut = true;
      try {
        if (process.platform !== "win32" && child.pid) process.kill(-child.pid, "SIGKILL");
        else child.kill("SIGKILL");
      } catch {
        try { child.kill("SIGKILL"); } catch { /* already gone */ }
      }
    }, timeoutMs);
    child.on("close", (code, signal) => {
      clearTimeout(timer);
      resolve({
        exitCode: timedOut ? 124 : (code ?? 128),
        signal: signal || null,
        stdout: stdout.toString("utf8"),
        stderr: stderr.toString("utf8"),
        truncated,
        durationMs: Date.now() - started,
        timedOut,
      });
    });
  });
}

async function runGit(repoRoot, args, options = {}) {
  const result = await runProcess(GIT_BINARY, ["-C", repoRoot, ...args], { ...options, cwd: repoRoot });
  const allowExit = options.allowExitCodes || [0];
  if (!allowExit.includes(result.exitCode)) {
    throw new Error((result.stderr || result.stdout || `git exited ${result.exitCode}`).trim());
  }
  return result;
}

async function resolveCommit(repo, rawRevision) {
  const revision = safeRevision(rawRevision);
  const result = await runGit(repo.root, ["rev-parse", "--verify", `${revision}^{commit}`], { outputLimit: 1024 });
  const commit = result.stdout.trim().toLowerCase();
  if (!COMMIT_PATTERN.test(commit)) throw new Error("revision does not resolve to an exact commit");
  return commit;
}

function workspace(raw) {
  const id = safeWorkspaceId(raw);
  const current = workspaces.get(id);
  if (!current) throw new Error(`workspaceId is not active: ${id}`);
  if (!fs.existsSync(current.root)) throw new Error(`workspace root is missing: ${id}`);
  return current;
}

function commandTokens(raw) {
  const source = text(raw);
  if (!source) throw new Error("command is required");
  if (/[\r\n\0]/.test(source)) throw new Error("command cannot contain line breaks");
  const tokens = [];
  let current = "";
  let quote = null;
  let escaping = false;
  for (const char of source) {
    if (escaping) { current += char; escaping = false; continue; }
    if (char === "\\" && quote !== "'") { escaping = true; continue; }
    if (quote) {
      if (char === quote) quote = null;
      else current += char;
      continue;
    }
    if (char === "'" || char === '"') { quote = char; continue; }
    if (/\s/.test(char)) {
      if (current) { tokens.push(current); current = ""; }
      continue;
    }
    if ("|;&><`".includes(char)) throw new Error("shell operators and redirection are not allowed");
    if (char === "$" && source.includes("$(")) throw new Error("command substitution is not allowed");
    current += char;
  }
  if (quote || escaping) throw new Error("command quoting is incomplete");
  if (current) tokens.push(current);
  if (tokens.length === 0) throw new Error("command is required");
  return tokens;
}

function validateCommand(tokens, currentWorkspace, expectedEffect = "READ_ONLY") {
  const first = tokens[0];
  const lower = first.toLowerCase();
  const effect = text(expectedEffect || "READ_ONLY").toUpperCase();
  const denied = new Set(["sudo", "su", "ssh", "scp", "nc", "telnet", "kill", "killall", "pkill", "rm", "chmod", "chown", "mount", "umount", "docker", "kubectl", "helm", "terraform", "ansible-playbook"]);
  if (denied.has(lower)) throw new Error(`command is blocked: ${first}`);
  if (tokens.some((token) => token === ".." || token.startsWith("../") || token.includes("/../"))) {
    throw new Error("command arguments cannot escape the workspace");
  }
  if (tokens.some((token) => path.posix.isAbsolute(token) || path.win32.isAbsolute(token))) {
    throw new Error("host absolute paths are blocked");
  }
  if (lower === "git") {
    const sub = (tokens[1] || "").toLowerCase();
    if (["push", "pull", "fetch", "reset", "clean", "merge", "rebase"].includes(sub)) throw new Error(`git ${sub} is blocked`);
    if (["add", "commit", "restore", "checkout"].includes(sub) && !currentWorkspace) throw new Error(`git ${sub} requires a repair workspace`);
    if (!["status", "diff", "log", "show", "blame", "grep", "rev-parse", "ls-files", "add", "commit", "restore", "checkout"].includes(sub)) {
      throw new Error(`git subcommand is not allowed: ${sub}`);
    }
    return;
  }
  const readOnly = new Set(["pwd", "ls", "find", "rg", "grep", "cat", "head", "tail", "sed", "sha256sum", "shasum"]);
  const development = new Set(["mvn", "gradle", "./gradlew", "npm", "pnpm", "yarn", "pytest", "python", "python3", "go", "cargo", "make", "java", "./mvnw", "curl"]);
  const script = first.startsWith("./") && !first.includes("../");
  if (effect === "READ_ONLY") {
    if (!readOnly.has(lower) && !(lower === "curl" && tokens.slice(1).every((v) => !/^(?:-X|--request)$/i.test(v)))) {
      throw new Error(`command is not read-only: ${first}`);
    }
    return;
  }
  if (!currentWorkspace) throw new Error("development commands require a repair workspace");
  if (!development.has(lower) && !script) throw new Error(`development command is not allowed: ${first}`);
  if (lower === "mvn" && tokens.some((v) => ["deploy", "release:perform", "release:prepare"].includes(v))) throw new Error("Maven publish/release is blocked");
  if (["npm", "pnpm", "yarn", "cargo"].includes(lower) && tokens.some((v) => ["publish", "login", "owner"].includes(v))) throw new Error("package publishing is blocked");
  if (lower === "curl") {
    const urls = tokens.filter((v) => /^https?:\/\//i.test(v));
    if (urls.some((v) => !/^https?:\/\/(?:127\.0\.0\.1|localhost|\[::1\])(?::\d+)?(?:\/|$)/i.test(v))) {
      throw new Error("curl is limited to localhost verification endpoints");
    }
  }
}

function scopedEnvironment(currentWorkspace) {
  const runtimeRoot = path.join(PROCESS_ROOT, currentWorkspace.workspaceId);
  fs.mkdirSync(runtimeRoot, { recursive: true });
  const home = path.join(runtimeRoot, "home");
  const tmp = path.join(runtimeRoot, "tmp");
  fs.mkdirSync(home, { recursive: true });
  fs.mkdirSync(tmp, { recursive: true });
  return { ...process.env, HOME: home, TMPDIR: tmp, TMP: tmp, TEMP: tmp, ORBISOPS_CODE_WORKSPACE_ID: currentWorkspace.workspaceId };
}

function stopProcessTree(record, signal = "SIGTERM") {
  if (!record || record.status !== "RUNNING") return;
  try { process.kill(-record.pid, signal); } catch { try { process.kill(record.pid, signal); } catch { /* already gone */ } }
}

function readLog(record, limit = MAX_OUTPUT_BYTES) {
  if (!record || !fs.existsSync(record.logPath)) return "";
  const stat = fs.statSync(record.logPath);
  const size = Math.min(stat.size, Math.max(1, limit));
  const fd = fs.openSync(record.logPath, "r");
  try {
    const buffer = Buffer.alloc(size);
    fs.readSync(fd, buffer, 0, size, Math.max(0, stat.size - size));
    return maskSecrets(buffer.toString("utf8"));
  } finally { fs.closeSync(fd); }
}

function executionFor(workspaceId, executionId) {
  const id = text(executionId);
  const record = executions.get(id);
  if (!record || record.workspaceId !== workspaceId) throw new Error("executionId does not belong to this workspace");
  return record;
}

async function startBackground(currentWorkspace, tokens, cwd, timeoutMs) {
  validateCommand(tokens, currentWorkspace, "TEST_OR_BUILD");
  const executionId = `exec-${crypto.randomUUID()}`;
  const logDir = path.join(PROCESS_ROOT, currentWorkspace.workspaceId, "logs");
  fs.mkdirSync(logDir, { recursive: true });
  const logPath = path.join(logDir, `${executionId}.log`);
  const fd = fs.openSync(logPath, "a");
  const child = spawn(tokens[0], tokens.slice(1), {
    cwd,
    env: scopedEnvironment(currentWorkspace),
    detached: true,
    stdio: ["ignore", fd, fd],
  });
  fs.closeSync(fd);
  const record = {
    executionId,
    workspaceId: currentWorkspace.workspaceId,
    pid: child.pid,
    commandHash: sha256(tokens.join("\u0000")),
    status: "RUNNING",
    exitCode: null,
    signal: null,
    startedAt: new Date().toISOString(),
    finishedAt: null,
    logPath,
    logRef: `workspace://${currentWorkspace.workspaceId}/process/${executionId}/log`,
    timer: null,
  };
  executions.set(executionId, record);
  const ttl = Math.min(BACKGROUND_TTL_MS, boundedInt(timeoutMs, BACKGROUND_TTL_MS, 5000, BACKGROUND_TTL_MS));
  record.timer = setTimeout(() => {
    if (record.status === "RUNNING") {
      record.status = "TIMED_OUT";
      stopProcessTree(record, "SIGKILL");
    }
  }, ttl);
  child.on("exit", (code, signal) => {
    if (record.timer) clearTimeout(record.timer);
    if (record.status === "RUNNING") record.status = code === 0 ? "SUCCEEDED" : "FAILED";
    record.exitCode = code;
    record.signal = signal;
    record.finishedAt = new Date().toISOString();
  });
  child.on("error", (error) => {
    if (record.timer) clearTimeout(record.timer);
    record.status = "FAILED";
    record.exitCode = 127;
    record.finishedAt = new Date().toISOString();
    fs.appendFileSync(logPath, `\n${error.message}\n`);
  });
  return { executionId, pid: child.pid, status: record.status, logRef: record.logRef, commandHash: record.commandHash };
}

class LspSession {
  constructor(key, root, command, args, dataDir, provider, languageId) {
    this.key = key;
    this.root = root;
    this.command = command;
    this.args = args;
    this.dataDir = dataDir;
    this.provider = provider || "lsp";
    this.languageId = languageId || "plaintext";
    this.child = null;
    this.buffer = Buffer.alloc(0);
    this.pending = new Map();
    this.nextId = 1;
    this.diagnostics = new Map();
    this.documents = new Map();
    this.initialized = false;
  }

  async start() {
    if (this.initialized && this.child && !this.child.killed) return;
    fs.mkdirSync(this.dataDir, { recursive: true });
    this.child = spawn(this.command, this.args, { cwd: this.root, env: process.env, stdio: ["pipe", "pipe", "pipe"] });
    this.child.stdout.on("data", (chunk) => this.onData(Buffer.from(chunk)));
    this.child.stderr.on("data", (chunk) => {
      const message = maskSecrets(Buffer.from(chunk).toString("utf8")).trim();
      if (message) console.error(`[code-mcp] ${this.provider}: ${message.slice(-2048)}`);
    });
    this.child.on("exit", () => {
      const startupOutput = maskSecrets(this.buffer.toString("utf8")).trim();
      if (startupOutput) console.error(`[code-mcp] ${this.provider} stdout before exit: ${startupOutput.slice(-2048)}`);
      const configIndex = this.args.indexOf("-configuration");
      const configDir = configIndex >= 0 ? this.args[configIndex + 1] : "";
      if (configDir && fs.existsSync(configDir)) {
        try {
          const logs = fs.readdirSync(configDir)
            .filter((name) => name.endsWith(".log"))
            .map((name) => ({ path: path.join(configDir, name), mtime: fs.statSync(path.join(configDir, name)).mtimeMs }))
            .sort((left, right) => left.mtime - right.mtime);
          const latest = logs.at(-1)?.path;
          if (latest) {
            const detail = maskSecrets(fs.readFileSync(latest, "utf8")).trim();
            if (detail) console.error(`[code-mcp] ${this.provider} startup log: ${detail.slice(-4096)}`);
          }
        } catch { /* startup diagnostics are best effort only */ }
      }
      for (const { reject } of this.pending.values()) reject(new Error("LSP process exited"));
      this.pending.clear();
      this.initialized = false;
    });
    const rootUri = pathToFileUri(this.root);
    await this.request("initialize", {
      processId: process.pid,
      rootUri,
      workspaceFolders: [{ uri: rootUri, name: path.basename(this.root) }],
      capabilities: {
        workspace: { symbol: { dynamicRegistration: false } },
        textDocument: {
          definition: { dynamicRegistration: false, linkSupport: true },
          references: { dynamicRegistration: false },
          hover: { dynamicRegistration: false, contentFormat: ["markdown", "plaintext"] },
          publishDiagnostics: { relatedInformation: true },
          synchronization: { dynamicRegistration: false, didSave: false },
        },
      },
    }, Math.max(LSP_TIMEOUT_MS, 60_000));
    this.notify("initialized", {});
    this.initialized = true;
  }

  onData(chunk) {
    this.buffer = Buffer.concat([this.buffer, chunk]);
    while (true) {
      const headerEnd = this.buffer.indexOf("\r\n\r\n");
      if (headerEnd < 0) return;
      const header = this.buffer.subarray(0, headerEnd).toString("ascii");
      const match = header.match(/Content-Length:\s*(\d+)/i);
      if (!match) { this.buffer = this.buffer.subarray(headerEnd + 4); continue; }
      const length = Number(match[1]);
      if (this.buffer.length < headerEnd + 4 + length) return;
      const body = this.buffer.subarray(headerEnd + 4, headerEnd + 4 + length).toString("utf8");
      this.buffer = this.buffer.subarray(headerEnd + 4 + length);
      let message;
      try { message = JSON.parse(body); } catch { continue; }
      if (message.id != null && this.pending.has(message.id)) {
        const pending = this.pending.get(message.id);
        this.pending.delete(message.id);
        if (message.error) pending.reject(new Error(message.error.message || "LSP request failed"));
        else pending.resolve(message.result);
      } else if (message.method === "textDocument/publishDiagnostics") {
        this.diagnostics.set(message.params?.uri, message.params?.diagnostics || []);
      } else if (message.id != null && message.method) {
        if (message.method === "workspace/configuration") {
          const items = Array.isArray(message.params?.items) ? message.params.items : [];
          this.respond(message.id, items.map(() => null));
        } else if (message.method === "workspace/workspaceFolders") {
          const rootUri = pathToFileUri(this.root);
          this.respond(message.id, [{ uri: rootUri, name: path.basename(this.root) }]);
        } else {
          this.respond(message.id, null);
        }
      }
    }
  }

  send(message) {
    if (!this.child?.stdin?.writable) throw new Error("LSP process is unavailable");
    const body = Buffer.from(JSON.stringify(message), "utf8");
    this.child.stdin.write(`Content-Length: ${body.length}\r\n\r\n`);
    this.child.stdin.write(body);
  }

  respond(id, result) { this.send({ jsonrpc: "2.0", id, result }); }
  notify(method, params) { this.send({ jsonrpc: "2.0", method, params }); }

  request(method, params, timeoutMs = LSP_TIMEOUT_MS) {
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`LSP request timed out: ${method}`));
      }, timeoutMs);
      this.pending.set(id, {
        resolve: (value) => { clearTimeout(timer); resolve(value); },
        reject: (error) => { clearTimeout(timer); reject(error); },
      });
      this.send({ jsonrpc: "2.0", id, method, params });
    });
  }

  async openFile(relativePath) {
    const filePath = resolveExistingFile(this.root, relativePath);
    const content = fs.readFileSync(filePath, "utf8");
    if (content.includes("\0")) throw new Error("binary file is not supported by LSP");
    const uri = pathToFileUri(filePath);
    const current = this.documents.get(uri);
    if (!current) {
      this.documents.set(uri, { version: 1, content });
      this.notify("textDocument/didOpen", { textDocument: { uri, languageId: this.languageId, version: 1, text: content } });
    } else if (current.content !== content) {
      const version = current.version + 1;
      this.documents.set(uri, { version, content });
      this.diagnostics.delete(uri);
      this.notify("textDocument/didChange", {
        textDocument: { uri, version },
        contentChanges: [{ text: content }],
      });
    }
    return { uri, content };
  }

  close() {
    try { this.notify("exit", {}); } catch { /* ignored */ }
    if (this.child && !this.child.killed) this.child.kill("SIGKILL");
    this.initialized = false;
  }
}

function pathToFileUri(filePath) {
  return `file://${filePath.split(path.sep).map(encodeURIComponent).join("/")}`.replace("file:////", "file:///");
}

function fromFileUri(uri, root) {
  const value = text(uri);
  if (!value.startsWith("file://")) return value;
  const filePath = decodeURIComponent(value.replace(/^file:\/\//, ""));
  try { return path.relative(root, filePath).replaceAll("\\", "/"); } catch { return filePath; }
}

function explicitLspConfiguration(language) {
  const normalized = normalizeLspLanguage(language);
  const prefix = normalized === "java" ? "JDTLS" : "PYRIGHT";
  const command = text(process.env[`CODE_WORKSPACE_${prefix}_COMMAND`]);
  if (!command) return null;
  let args = normalized === "python" ? ["--stdio"] : [];
  const raw = text(process.env[`CODE_WORKSPACE_${prefix}_ARGS_JSON`]);
  if (raw) args = JSON.parse(raw);
  if (!Array.isArray(args)) throw new Error(`CODE_WORKSPACE_${prefix}_ARGS_JSON must be a JSON array`);
  return {
    command,
    args: args.map(String),
    mode: "EXPLICIT",
    version: "external",
    configTemplateDir: "",
    provider: normalized === "java" ? "eclipse-jdtls" : "pyright",
    languageId: normalized,
  };
}

function normalizeLspLanguage(raw, relativePath = "") {
  const explicit = text(raw).toLowerCase();
  if (explicit) {
    if (explicit === "java" || explicit === "python") return explicit;
    throw new Error(`unsupported LSP language: ${explicit}`);
  }
  const extension = path.extname(text(relativePath)).toLowerCase();
  if (extension === ".java") return "java";
  if ([".py", ".pyi", ".pyw"].includes(extension)) return "python";
  if (LSP_LANGUAGES.size === 1) return [...LSP_LANGUAGES][0];
  throw new Error("LSP_LANGUAGE_REQUIRED");
}

function assertSupportedManagedVersion(language, version) {
  const supported = language === "java" ? SUPPORTED_JDTLS_VERSIONS : SUPPORTED_PYRIGHT_VERSIONS;
  if (!supported.has(version)) {
    const error = new Error(`${language.toUpperCase()}_LSP_VERSION_UNSUPPORTED:${version}; supported=${[...supported].join(",")}`);
    error.code = `${language.toUpperCase()}_LSP_VERSION_UNSUPPORTED`;
    throw error;
  }
}

function javaMajor(command) {
  if (!command) return 0;
  const result = spawnSync(command, ["-version"], { encoding: "utf8", timeout: 5000 });
  if (result.error || result.status !== 0) return 0;
  const output = `${result.stderr || ""}\n${result.stdout || ""}`;
  const match = output.match(/version\s+"(?:1\.)?(\d+)/i);
  return match ? Number.parseInt(match[1], 10) : 0;
}

function javaFromHome(home) {
  const value = text(home);
  if (!value) return "";
  const command = path.join(value, "bin", process.platform === "win32" ? "java.exe" : "java");
  return fs.existsSync(command) && javaMajor(command) >= 17 ? command : "";
}

function discoverJdtlsJavaCommand() {
  const explicitCommand = text(process.env.CODE_WORKSPACE_JDTLS_JAVA_COMMAND);
  if (explicitCommand && javaMajor(explicitCommand) >= 17) return explicitCommand;
  for (const home of [process.env.CODE_WORKSPACE_JDTLS_JAVA_HOME, process.env.JAVA_HOME]) {
    const command = javaFromHome(home);
    if (command) return command;
  }
  if (process.platform === "darwin" && fs.existsSync("/usr/libexec/java_home")) {
    const result = spawnSync("/usr/libexec/java_home", ["-v", "17"], { encoding: "utf8", timeout: 5000 });
    if (!result.error && result.status === 0) {
      const command = javaFromHome(result.stdout);
      if (command) return command;
    }
  }
  return javaMajor("java") >= 17 ? "java" : "";
}

function managedJdtlsRuntimeDirectory() {
  if (!/^\d+\.\d+\.\d+$/.test(JDTLS_VERSION)) throw new Error("CODE_WORKSPACE_JDTLS_VERSION must be semver-like x.y.z");
  assertSupportedManagedVersion("java", JDTLS_VERSION);
  return path.join(LSP_RUNTIME_ROOT, "jdtls", JDTLS_VERSION);
}

function managedJdtlsRuntimeDescriptor() {
  const runtimeRoot = managedJdtlsRuntimeDirectory();
  const plugins = path.join(runtimeRoot, "plugins");
  if (!fs.existsSync(plugins) || !fs.statSync(plugins).isDirectory()) return null;
  const launchers = fs.readdirSync(plugins)
    .filter((name) => /^org\.eclipse\.equinox\.launcher_.*\.jar$/.test(name))
    .sort();
  if (launchers.length === 0) return null;
  const platformConfigName = process.platform === "darwin" ? "config_mac" : process.platform === "win32" ? "config_win" : "config_linux";
  const configTemplateDir = path.join(runtimeRoot, platformConfigName);
  if (!fs.existsSync(configTemplateDir) || !fs.statSync(configTemplateDir).isDirectory()) return null;
  return { runtimeRoot, launcher: path.join(plugins, launchers.at(-1)), configTemplateDir };
}

async function fetchBuffer(url, maxBytes, options = {}) {
  const prefix = text(options.prefix || "LSP").toUpperCase();
  const timeoutMs = boundedInt(options.timeoutMs, JDTLS_DOWNLOAD_TIMEOUT_MS, 1000, 600_000);
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, { redirect: "follow", signal: controller.signal });
    if (!response.ok) throw new Error(`${prefix}_DOWNLOAD_HTTP_${response.status}`);
    const declared = Number.parseInt(response.headers.get("content-length") || "0", 10);
    if (declared > maxBytes) throw new Error(`${prefix}_DOWNLOAD_TOO_LARGE`);
    const buffer = Buffer.from(await response.arrayBuffer());
    if (buffer.length > maxBytes) throw new Error(`${prefix}_DOWNLOAD_TOO_LARGE`);
    return buffer;
  } finally {
    clearTimeout(timer);
  }
}

function tar(commandArgs, errorCode, options = {}) {
  const result = spawnSync("tar", commandArgs, {
    encoding: "utf8",
    timeout: options.timeoutMs || 60_000,
    maxBuffer: options.maxBuffer || 4 * 1024 * 1024,
  });
  if (result.error || result.status !== 0) {
    throw new Error(`${errorCode}:${maskSecrets(result.stderr || result.stdout || result.error?.message || "tar failed")}`);
  }
  return result.stdout || "";
}

async function installManagedJdtls() {
  const existing = managedJdtlsRuntimeDescriptor();
  if (existing) return existing;
  const runtimeRoot = managedJdtlsRuntimeDirectory();
  const installRoot = `${runtimeRoot}.install-${crypto.randomUUID()}`;
  const downloadRoot = path.join(LSP_RUNTIME_ROOT, ".downloads");
  fs.mkdirSync(downloadRoot, { recursive: true });
  fs.mkdirSync(installRoot, { recursive: true });
  try {
    const base = JDTLS_DOWNLOAD_BASE_URL.replace(/\/+$/, "");
    const latestName = (await fetchBuffer(`${base}/latest.txt`, 4096)).toString("utf8").trim();
    const expectedPrefix = `jdt-language-server-${JDTLS_VERSION}-`;
    if (!latestName.startsWith(expectedPrefix) || !latestName.endsWith(".tar.gz") || !/^[A-Za-z0-9._-]+$/.test(latestName)) {
      throw new Error("JDTLS_LATEST_FILENAME_INVALID");
    }
    const checksumText = (await fetchBuffer(`${base}/${latestName}.sha256`, 4096)).toString("utf8").trim();
    const checksum = checksumText.match(/\b([a-f0-9]{64})\b/i)?.[1]?.toLowerCase();
    if (!checksum) throw new Error("JDTLS_CHECKSUM_INVALID");
    const archive = await fetchBuffer(`${base}/${latestName}`, JDTLS_MAX_ARCHIVE_BYTES);
    const actual = crypto.createHash("sha256").update(archive).digest("hex");
    if (actual !== checksum) throw new Error("JDTLS_CHECKSUM_MISMATCH");
    const archivePath = path.join(downloadRoot, `${JDTLS_VERSION}-${actual.slice(0, 16)}.tar.gz`);
    fs.writeFileSync(archivePath, archive, { mode: 0o600 });
    const entries = tar(["-tzf", archivePath], "JDTLS_ARCHIVE_LIST_FAILED");
    for (const entry of entries.split(/\r?\n/).filter(Boolean)) {
      const normalized = entry.replaceAll("\\", "/");
      if (normalized.startsWith("/") || normalized.split("/").includes("..") || /^[A-Za-z]:\//.test(normalized)) {
        throw new Error("JDTLS_ARCHIVE_PATH_INVALID");
      }
    }
    tar(["-xzf", archivePath, "-C", installRoot], "JDTLS_ARCHIVE_EXTRACT_FAILED", { timeoutMs: 120_000 });
    fs.writeFileSync(path.join(installRoot, "orbisops-runtime.json"), JSON.stringify({
      runtime: "eclipse-jdtls",
      version: JDTLS_VERSION,
      archive: latestName,
      sha256: actual,
      source: base,
      installedAt: new Date().toISOString(),
    }, null, 2));
    if (fs.existsSync(runtimeRoot)) fs.rmSync(runtimeRoot, { recursive: true, force: true });
    fs.renameSync(installRoot, runtimeRoot);
    const installed = managedJdtlsRuntimeDescriptor();
    if (!installed) throw new Error("JDTLS_RUNTIME_LAYOUT_INVALID");
    return installed;
  } finally {
    if (fs.existsSync(installRoot)) fs.rmSync(installRoot, { recursive: true, force: true });
  }
}

function managedPyrightRuntimeDirectory() {
  if (!/^\d+\.\d+\.\d+$/.test(PYRIGHT_VERSION)) throw new Error("CODE_WORKSPACE_PYRIGHT_VERSION must be semver-like x.y.z");
  assertSupportedManagedVersion("python", PYRIGHT_VERSION);
  return path.join(LSP_RUNTIME_ROOT, "pyright", PYRIGHT_VERSION);
}

function managedPyrightRuntimeDescriptor() {
  const runtimeRoot = managedPyrightRuntimeDirectory();
  const langserver = path.join(runtimeRoot, "package", "langserver.index.js");
  if (!fs.existsSync(langserver) || !fs.statSync(langserver).isFile()) return null;
  return { runtimeRoot, langserver };
}

async function installManagedPyright() {
  const existing = managedPyrightRuntimeDescriptor();
  if (existing) return existing;
  const runtimeRoot = managedPyrightRuntimeDirectory();
  const installRoot = `${runtimeRoot}.install-${crypto.randomUUID()}`;
  const downloadRoot = path.join(LSP_RUNTIME_ROOT, ".downloads");
  fs.mkdirSync(downloadRoot, { recursive: true });
  fs.mkdirSync(installRoot, { recursive: true });
  try {
    const registry = PYRIGHT_REGISTRY_URL.replace(/\/+$/, "");
    const metadataBuffer = await fetchBuffer(`${registry}/${PYRIGHT_VERSION}`, 2 * 1024 * 1024, {
      prefix: "PYRIGHT",
      timeoutMs: PYRIGHT_DOWNLOAD_TIMEOUT_MS,
    });
    const metadata = JSON.parse(metadataBuffer.toString("utf8"));
    const tarballUrl = text(metadata?.dist?.tarball);
    const integrity = text(metadata?.dist?.integrity);
    if (!/^https?:\/\//i.test(tarballUrl)) throw new Error("PYRIGHT_TARBALL_URL_INVALID");
    const integrityMatch = integrity.match(/^sha512-(.+)$/i);
    if (!integrityMatch) throw new Error("PYRIGHT_SHA512_INTEGRITY_REQUIRED");
    const archive = await fetchBuffer(tarballUrl, PYRIGHT_MAX_ARCHIVE_BYTES, {
      prefix: "PYRIGHT",
      timeoutMs: PYRIGHT_DOWNLOAD_TIMEOUT_MS,
    });
    const actualIntegrity = crypto.createHash("sha512").update(archive).digest("base64");
    if (actualIntegrity !== integrityMatch[1]) throw new Error("PYRIGHT_CHECKSUM_MISMATCH");
    const sha256Digest = crypto.createHash("sha256").update(archive).digest("hex");
    const archivePath = path.join(downloadRoot, `pyright-${PYRIGHT_VERSION}-${sha256Digest.slice(0, 16)}.tgz`);
    fs.writeFileSync(archivePath, archive, { mode: 0o600 });
    const entries = tar(["-tzf", archivePath], "PYRIGHT_ARCHIVE_LIST_FAILED");
    for (const entry of entries.split(/\r?\n/).filter(Boolean)) {
      const normalized = entry.replaceAll("\\", "/");
      if (normalized.startsWith("/") || normalized.split("/").includes("..") || /^[A-Za-z]:\//.test(normalized)) {
        throw new Error("PYRIGHT_ARCHIVE_PATH_INVALID");
      }
    }
    tar(["-xzf", archivePath, "-C", installRoot], "PYRIGHT_ARCHIVE_EXTRACT_FAILED", { timeoutMs: 120_000 });
    fs.writeFileSync(path.join(installRoot, "orbisops-runtime.json"), JSON.stringify({
      runtime: "pyright",
      version: PYRIGHT_VERSION,
      sha256: sha256Digest,
      integrity,
      source: tarballUrl,
      installedAt: new Date().toISOString(),
    }, null, 2));
    if (fs.existsSync(runtimeRoot)) fs.rmSync(runtimeRoot, { recursive: true, force: true });
    fs.renameSync(installRoot, runtimeRoot);
    const installed = managedPyrightRuntimeDescriptor();
    if (!installed) throw new Error("PYRIGHT_RUNTIME_LAYOUT_INVALID");
    return installed;
  } finally {
    if (fs.existsSync(installRoot)) fs.rmSync(installRoot, { recursive: true, force: true });
  }
}

function lspCapabilityStatus(language) {
  const normalized = normalizeLspLanguage(language);
  if (!LSP_LANGUAGES.has(normalized)) {
    return { language: normalized, available: false, mode: "DISABLED", provisioned: false, version: null, reasonCode: "LSP_LANGUAGE_DISABLED" };
  }
  const explicit = explicitLspConfiguration(normalized);
  if (explicit) {
    return { language: normalized, provider: explicit.provider, available: true, mode: "EXPLICIT", provisioned: true, version: "external", reasonCode: "" };
  }
  if (normalized === "java") {
    assertSupportedManagedVersion("java", JDTLS_VERSION);
    if (!JDTLS_AUTO_INSTALL) return { language: normalized, provider: "eclipse-jdtls", available: false, mode: "DISABLED", provisioned: false, version: JDTLS_VERSION, reasonCode: "JDTLS_AUTO_INSTALL_DISABLED" };
    const javaCommand = discoverJdtlsJavaCommand();
    if (!javaCommand) return { language: normalized, provider: "eclipse-jdtls", available: false, mode: "MANAGED", provisioned: false, version: JDTLS_VERSION, reasonCode: "JDTLS_JAVA_17_REQUIRED" };
    return { language: normalized, provider: "eclipse-jdtls", available: true, mode: "MANAGED", provisioned: Boolean(managedJdtlsRuntimeDescriptor()), version: JDTLS_VERSION, reasonCode: "" };
  }
  assertSupportedManagedVersion("python", PYRIGHT_VERSION);
  if (!PYRIGHT_AUTO_INSTALL) return { language: normalized, provider: "pyright", available: false, mode: "DISABLED", provisioned: false, version: PYRIGHT_VERSION, reasonCode: "PYRIGHT_AUTO_INSTALL_DISABLED" };
  return { language: normalized, provider: "pyright", available: true, mode: "MANAGED", provisioned: Boolean(managedPyrightRuntimeDescriptor()), version: PYRIGHT_VERSION, reasonCode: "" };
}

function configuredLspCapabilities() {
  const result = {};
  for (const language of ["java", "python"]) {
    if (!LSP_LANGUAGES.has(language)) continue;
    result[language] = lspCapabilityStatus(language);
  }
  return result;
}

async function lspConfiguration(language) {
  const normalized = normalizeLspLanguage(language);
  if (!LSP_LANGUAGES.has(normalized)) return null;
  const explicit = explicitLspConfiguration(normalized);
  if (explicit) return explicit;
  if (normalized === "python") {
    if (!PYRIGHT_AUTO_INSTALL) return null;
    let runtime;
    try {
      runtime = await installManagedPyright();
    } catch (cause) {
      const error = new Error(`managed Pyright provisioning failed: ${cause.message}`);
      error.code = "PYRIGHT_PROVISION_FAILED";
      throw error;
    }
    return {
      command: process.execPath,
      args: [runtime.langserver, "--stdio"],
      mode: "MANAGED",
      version: PYRIGHT_VERSION,
      provider: "pyright",
      languageId: "python",
      configTemplateDir: "",
    };
  }
  if (!JDTLS_AUTO_INSTALL) return null;
  const javaCommand = discoverJdtlsJavaCommand();
  if (!javaCommand) {
    const error = new Error(`JDT LS ${JDTLS_VERSION} requires a Java 17+ runtime; configure CODE_WORKSPACE_JDTLS_JAVA_HOME or install Java 17 on the Code MCP host`);
    error.code = "JDTLS_JAVA_17_REQUIRED";
    throw error;
  }
  let runtime;
  try {
    runtime = await installManagedJdtls();
  } catch (cause) {
    const error = new Error(`managed JDT LS provisioning failed: ${cause.message}`);
    error.code = "JDTLS_PROVISION_FAILED";
    throw error;
  }
  return {
    command: javaCommand,
    args: [
      "-Declipse.application=org.eclipse.jdt.ls.core.id1",
      "-Dosgi.bundles.defaultStartLevel=4",
      "-Declipse.product=org.eclipse.jdt.ls.core.product",
      "-Dlog.level=ERROR",
      "-Xmx1G",
      "--add-modules=ALL-SYSTEM",
      "--add-opens", "java.base/java.util=ALL-UNNAMED",
      "--add-opens", "java.base/java.lang=ALL-UNNAMED",
      "-jar", runtime.launcher,
      "-configuration", "{configDir}",
      "-data", "{dataDir}",
    ],
    mode: "MANAGED",
    version: JDTLS_VERSION,
    provider: "eclipse-jdtls",
    languageId: "java",
    configTemplateDir: runtime.configTemplateDir,
  };
}

async function ensureAnalysisRoot(repo, commit) {
  const key = `${repo.repositoryId}:${commit}`;
  if (analysisRoots.has(key) && fs.existsSync(analysisRoots.get(key))) return analysisRoots.get(key);
  const root = path.join(ANALYSIS_ROOT, repo.repositoryId, commit);
  if (!fs.existsSync(root)) {
    fs.mkdirSync(path.dirname(root), { recursive: true });
    await runGit(repo.root, ["worktree", "add", "--detach", root, commit], { timeoutMs: 60_000 });
  }
  analysisRoots.set(key, root);
  return root;
}

async function lspSession(key, root, language) {
  const normalized = normalizeLspLanguage(language);
  const config = await lspConfiguration(normalized);
  if (!config) return null;
  const sessionKey = `${key}:${normalized}`;
  let session = lspSessions.get(sessionKey);
  if (session) { await session.start(); return session; }
  const dataDir = path.join(LSP_DATA_ROOT, normalized, sha256(sessionKey).slice(0, 24));
  fs.mkdirSync(dataDir, { recursive: true });
  const args = config.args.map((arg) => arg
    .replaceAll("{dataDir}", dataDir)
    .replaceAll("{configDir}", config.configTemplateDir || "")
    .replaceAll("{workspaceRoot}", root));
  session = new LspSession(sessionKey, root, config.command, args, dataDir, config.provider, config.languageId || normalized);
  lspSessions.set(sessionKey, session);
  await session.start();
  return session;
}

function normalizeLocation(value, root) {
  if (Array.isArray(value)) return value.map((item) => normalizeLocation(item, root));
  if (!value || typeof value !== "object") return value;
  const result = { ...value };
  if (result.uri) result.uri = fromFileUri(result.uri, root);
  if (result.targetUri) result.targetUri = fromFileUri(result.targetUri, root);
  return result;
}

async function codeInfo(args) {
  const repo = repository(args.repositoryId);
  const resolvedCommit = await resolveCommit(repo, args.revision || "HEAD");
  const languages = configuredLspCapabilities();
  const lspAvailable = Object.values(languages).some((item) => item.available);
  return {
    repositoryId: repo.repositoryId,
    revision: safeRevision(args.revision || "HEAD"),
    resolvedCommit,
    root: repo.repositoryId,
    protocolVersion: "orbisops-code-workspace-v1",
    capabilities: {
      read: true, search: true, lsp: lspAvailable, worktree: true,
      patch: true, bash: true, backgroundBash: true, diff: true, commit: true, cleanup: true,
    },
    lsp: {
      enabledLanguages: [...LSP_LANGUAGES],
      languages,
      supportedVersions: {
        java: [...SUPPORTED_JDTLS_VERSIONS],
        python: [...SUPPORTED_PYRIGHT_VERSIONS],
      },
    },
  };
}

async function codeRead(args) {
  const workspaceId = text(args.workspaceId);
  const relativePath = safeRelativePath(args.path);
  let content;
  let commit = "";
  let repositoryId = "";
  if (workspaceId) {
    const current = workspace(workspaceId);
    repositoryId = current.repositoryId;
    const file = resolveExistingFile(current.root, relativePath);
    content = fs.readFileSync(file);
  } else {
    const repo = repository(args.repositoryId);
    repositoryId = repo.repositoryId;
    commit = await resolveCommit(repo, args.revision || "HEAD");
    const object = `${commit}:${relativePath}`;
    const size = Number.parseInt((await runGit(repo.root, ["cat-file", "-s", object], { outputLimit: 1024 })).stdout.trim(), 10);
    if (!Number.isFinite(size) || size < 0 || size > MAX_FILE_BYTES) throw new Error(`file exceeds ${MAX_FILE_BYTES} bytes`);
    content = Buffer.from((await runGit(repo.root, ["show", object], { outputLimit: MAX_FILE_BYTES + 1 })).stdout, "utf8");
  }
  if (content.length > MAX_FILE_BYTES || content.includes(0)) throw new Error("binary or oversized file is not supported");
  const raw = content.toString("utf8");
  return {
    repositoryId, workspaceId: workspaceId || null, commit: commit || null, path: relativePath,
    sizeBytes: content.length, sha256: sha256(raw), content: maskSecrets(raw),
  };
}

async function codeSearch(args) {
  const mode = text(args.mode || "text").toLowerCase();
  if (mode !== "text" && mode !== "glob") throw new Error(`unsupported search mode: ${mode}`);
  const limit = boundedInt(args.limit, mode === "glob" ? 100 : 50, 1, mode === "glob" ? 500 : 200);
  const workspaceId = text(args.workspaceId);
  let commit = "";
  let repositoryId = "";

  if (mode === "glob") {
    const pattern = safeRelativePath(args.pattern || args.query || "**/*");
    const files = [];
    if (workspaceId) {
      const current = workspace(workspaceId);
      repositoryId = current.repositoryId;
      const listArgs = ["ls-files", "--cached", "--others", "--exclude-standard"];
      const listed = await runGit(current.root, listArgs, { outputLimit: MAX_OUTPUT_BYTES });
      for (const raw of listed.stdout.split(/\r?\n/)) {
        if (!raw || files.length >= limit) continue;
        try {
          const relativePath = safeRelativePath(raw);
          if (!globMatches(relativePath, pattern)) continue;
          const filePath = resolveExistingFile(current.root, relativePath);
          const stat = fs.statSync(filePath);
          files.push({ path: relativePath, sizeBytes: stat.size, modifiedAt: stat.mtime.toISOString() });
        } catch { /* blocked/sensitive/oversized entries stay undisclosed */ }
      }
    } else {
      const repo = repository(args.repositoryId);
      repositoryId = repo.repositoryId;
      commit = await resolveCommit(repo, args.revision || "HEAD");
      const treeArgs = ["ls-tree", "-r", "--name-only", commit];
      const listed = await runGit(repo.root, treeArgs, { outputLimit: MAX_OUTPUT_BYTES });
      for (const raw of listed.stdout.split(/\r?\n/)) {
        if (!raw || files.length >= limit) continue;
        try {
          const relativePath = safeRelativePath(raw);
          if (!globMatches(relativePath, pattern)) continue;
          const size = Number.parseInt((await runGit(repo.root, ["cat-file", "-s", `${commit}:${relativePath}`], { outputLimit: 1024 })).stdout.trim(), 10);
          if (!Number.isFinite(size) || size < 0 || size > MAX_FILE_BYTES) continue;
          files.push({ path: relativePath, sizeBytes: size, modifiedAt: `commit:${commit}` });
        } catch { /* blocked/sensitive/oversized entries stay undisclosed */ }
      }
    }
    return { repositoryId, workspaceId: workspaceId || null, commit: commit || null, mode, pattern, fileCount: files.length, files, resultHash: sha256(JSON.stringify(files)) };
  }

  const query = String(args.query || "");
  if (!query.trim() || query.length > 500 || /[\r\n\0]/.test(query)) throw new Error("query must contain 1-500 characters without line breaks");
  const regex = args.regex === true;
  const caseSensitive = args.caseSensitive !== false;
  const glob = text(args.glob);
  const pathspec = glob ? safeRelativePath(glob) : ".";
  const grepArgs = ["grep", "-n", "-I", regex ? "-E" : "-F"];
  if (!caseSensitive) grepArgs.push("-i");
  grepArgs.push("-e", query);
  const hits = [];
  if (workspaceId) {
    const current = workspace(workspaceId);
    repositoryId = current.repositoryId;
    const result = await runGit(current.root, [...grepArgs, "--", pathspec], { allowExitCodes: [0, 1], outputLimit: MAX_OUTPUT_BYTES });
    for (const line of result.stdout.split(/\r?\n/)) {
      if (!line || hits.length >= limit) continue;
      const match = line.match(/^(.+?):(\d+):(.*)$/);
      if (!match) continue;
      const file = match[1].replace(/^\.\//, "");
      try { safeRelativePath(file); } catch { continue; }
      hits.push({ file, line: Number(match[2]), snippet: maskSecrets(match[3]) });
    }
  } else {
    const repo = repository(args.repositoryId);
    repositoryId = repo.repositoryId;
    commit = await resolveCommit(repo, args.revision || "HEAD");
    const result = await runGit(repo.root, [...grepArgs, commit, "--", pathspec], { allowExitCodes: [0, 1], outputLimit: MAX_OUTPUT_BYTES });
    const prefix = `${commit}:`;
    for (const line of result.stdout.split(/\r?\n/)) {
      if (!line || hits.length >= limit) continue;
      const value = line.startsWith(prefix) ? line.slice(prefix.length) : line;
      const match = value.match(/^(.+?):(\d+):(.*)$/);
      if (!match) continue;
      try { safeRelativePath(match[1]); } catch { continue; }
      hits.push({ file: match[1], line: Number(match[2]), snippet: maskSecrets(match[3]) });
    }
  }
  return { repositoryId, workspaceId: workspaceId || null, commit: commit || null, mode, query, hitCount: hits.length, hits, resultHash: sha256(JSON.stringify(hits)) };
}

async function codeLsp(args) {
  const action = text(args.action || "symbols").toLowerCase();
  if (!["definition", "references", "hover", "symbols", "diagnostics"].includes(action)) throw new Error(`unsupported LSP action: ${action}`);
  const relativePath = action === "symbols" ? "" : safeRelativePath(args.path);
  let language = "";
  if (text(args.language) || relativePath || LSP_LANGUAGES.size === 1) {
    language = normalizeLspLanguage(args.language, relativePath);
  }
  let root;
  let key;
  let identity;
  const workspaceId = text(args.workspaceId);
  if (workspaceId) {
    const current = workspace(workspaceId);
    root = current.root;
    key = `workspace:${current.workspaceId}`;
    identity = { repositoryId: current.repositoryId, workspaceId: current.workspaceId, commit: null };
  } else {
    const repo = repository(args.repositoryId);
    const commit = await resolveCommit(repo, args.revision || "HEAD");
    root = await ensureAnalysisRoot(repo, commit);
    key = `repository:${repo.repositoryId}:${commit}`;
    identity = { repositoryId: repo.repositoryId, workspaceId: null, commit };
  }
  if (!language) {
    const reasonCode = LSP_LANGUAGES.size === 0 ? "LSP_NOT_CONFIGURED" : "LSP_LANGUAGE_REQUIRED";
    return { ...identity, action, language: null, status: "UNAVAILABLE", readOnly: true, reasonCode, results: [] };
  }
  const capability = lspCapabilityStatus(language);
  if (!capability.available) {
    return { ...identity, action, language, status: "UNAVAILABLE", readOnly: true, reasonCode: capability.reasonCode || "LSP_NOT_CONFIGURED", results: [] };
  }
  let session;
  try {
    session = await lspSession(key, root, language);
  } catch (error) {
    return {
      ...identity,
      action,
      language,
      status: "UNAVAILABLE",
      readOnly: true,
      reasonCode: text(error?.code || "LSP_START_FAILED"),
      message: maskSecrets(error?.message || `${language} LSP is unavailable`),
      results: [],
    };
  }
  if (!session) {
    return { ...identity, action, language, status: "UNAVAILABLE", readOnly: true, reasonCode: capability.reasonCode || "LSP_NOT_CONFIGURED", results: [] };
  }
  const query = text(args.query);
  if (action === "symbols") {
    const result = await session.request("workspace/symbol", { query }, LSP_TIMEOUT_MS);
    return { ...identity, action, language, provider: capability.provider, status: "SUCCEEDED", readOnly: true, results: normalizeLocation(result || [], root) };
  }
  const opened = await session.openFile(relativePath);
  const position = { line: Math.max(0, Number(args.line || 1) - 1), character: Math.max(0, Number(args.character || 1) - 1) };
  if (action === "diagnostics") {
    const deadline = Date.now() + Math.min(LSP_TIMEOUT_MS, 5000);
    while (!session.diagnostics.has(opened.uri) && Date.now() < deadline) await new Promise((resolve) => setTimeout(resolve, 100));
    const result = session.diagnostics.get(opened.uri) || [];
    return { ...identity, action, language, provider: capability.provider, path: relativePath, status: "SUCCEEDED", readOnly: true, results: normalizeLocation(result, root) };
  }
  const method = action === "definition" ? "textDocument/definition" : action === "references" ? "textDocument/references" : "textDocument/hover";
  const params = { textDocument: { uri: opened.uri }, position };
  if (action === "references") params.context = { includeDeclaration: true };
  const result = await session.request(method, params, LSP_TIMEOUT_MS);
  return { ...identity, action, language, provider: capability.provider, path: relativePath, position: { line: position.line + 1, character: position.character + 1 }, status: "SUCCEEDED", readOnly: true, results: normalizeLocation(result ?? [], root) };
}

async function enterWorktree(args) {
  const repo = repository(args.repositoryId);
  const baseCommit = await resolveCommit(repo, args.baseCommit || args.revision);
  if (!COMMIT_PATTERN.test(baseCommit)) throw new Error("baseCommit must resolve to an exact commit");
  const workspaceId = safeWorkspaceId(args.workspaceId || `rw-${crypto.randomUUID()}`);
  if (workspaces.has(workspaceId) || fs.existsSync(path.join(WORKTREE_ROOT, workspaceId))) throw new Error("workspaceId already exists");
  const root = path.join(WORKTREE_ROOT, workspaceId);
  const serviceId = text(args.serviceId || "service").replace(/[^A-Za-z0-9._-]/g, "-").slice(0, 80) || "service";
  const branch = `ops/repair/${serviceId}/${workspaceId}`;
  await runGit(repo.root, ["cat-file", "-e", `${baseCommit}^{commit}`], { timeoutMs: 10_000 });
  await runGit(repo.root, ["worktree", "add", "-b", branch, root, baseCommit], { timeoutMs: 60_000 });
  const current = { workspaceId, repositoryId: repo.repositoryId, repositoryRoot: repo.root, root, baseCommit, branch, createdAt: new Date().toISOString() };
  workspaces.set(workspaceId, current);
  return { workspaceId, repositoryId: repo.repositoryId, baseCommit, branch, status: "ACTIVE", logicalRoot: workspaceId };
}

async function applyPatch(args) {
  const current = workspace(args.workspaceId);
  const mode = text(args.mode || (args.unifiedDiff ? "patch" : args.content != null ? "write" : "edit")).toLowerCase();
  if (mode === "patch") {
    const patch = String(args.unifiedDiff || "");
    if (!patch.trim() || patch.length > 2 * MAX_OUTPUT_BYTES) throw new Error("unifiedDiff is required and must be bounded");
    const check = await runProcess(GIT_BINARY, ["-C", current.root, "apply", "--check", "--whitespace=error", "-"], { cwd: current.root, input: patch, timeoutMs: 30_000, outputLimit: MAX_OUTPUT_BYTES });
    if (check.exitCode !== 0) throw new Error(check.stderr || check.stdout || "git apply --check failed");
    const numstat = await runProcess(GIT_BINARY, ["-C", current.root, "apply", "--numstat", "-"], { cwd: current.root, input: patch, timeoutMs: 30_000, outputLimit: MAX_OUTPUT_BYTES });
    if (numstat.exitCode !== 0) throw new Error(numstat.stderr || numstat.stdout || "git apply --numstat failed");
    const patchTargets = [];
    for (const line of numstat.stdout.split(/\r?\n/)) {
      if (!line) continue;
      const columns = line.split("\t");
      if (columns.length < 3) throw new Error("unifiedDiff target path is invalid");
      const target = columns.slice(2).join("\t");
      if (!target || target.includes(" => ") || target.includes("\0")) throw new Error("unifiedDiff rename/copy paths are not supported");
      const relativePath = safeRelativePath(target);
      resolveWritableFile(current.root, relativePath);
      patchTargets.push(relativePath);
    }
    if (patchTargets.length === 0) throw new Error("unifiedDiff contains no writable file targets");
    const applied = await runProcess(GIT_BINARY, ["-C", current.root, "apply", "--whitespace=error", "-"], { cwd: current.root, input: patch, timeoutMs: 30_000, outputLimit: MAX_OUTPUT_BYTES });
    if (applied.exitCode !== 0) throw new Error(applied.stderr || applied.stdout || "git apply failed");
    const changed = (await runGit(current.root, ["diff", "--name-only", current.baseCommit], { timeoutMs: 10_000 })).stdout.split(/\r?\n/).filter(Boolean);
    for (const file of changed) safeRelativePath(file);
    return { workspaceId: current.workspaceId, mode, changedFiles: changed, patchHash: sha256(patch), status: "APPLIED" };
  }
  const relativePath = safeRelativePath(args.path);
  const filePath = resolveWritableFile(current.root, relativePath);
  const existed = fs.existsSync(filePath);
  const before = existed ? fs.readFileSync(resolveExistingFile(current.root, relativePath), "utf8") : "";
  const beforeHash = sha256(before);
  const expected = text(args.expectedSha256).toLowerCase();
  if (existed && (!SHA256_PATTERN.test(expected) || expected !== beforeHash)) throw new Error("read-before-write SHA-256 CAS mismatch");
  let after;
  let replacements = 0;
  if (mode === "write") {
    after = String(args.content ?? "");
  } else if (mode === "edit") {
    const oldString = String(args.oldString ?? "");
    const newString = String(args.newString ?? "");
    if (!oldString) throw new Error("oldString is required");
    const parts = before.split(oldString);
    const count = parts.length - 1;
    if (count === 0 || (count > 1 && !args.replaceAll)) throw new Error(`oldString match count is invalid: ${count}`);
    replacements = args.replaceAll ? count : 1;
    after = args.replaceAll ? parts.join(newString) : before.replace(oldString, newString);
  } else throw new Error(`unsupported patch mode: ${mode}`);
  if (Buffer.byteLength(after, "utf8") > MAX_FILE_BYTES || after.includes("\0")) throw new Error("written file is binary or oversized");
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, after, "utf8");
  return { workspaceId: current.workspaceId, mode, path: relativePath, beforeSha256: beforeHash, afterSha256: sha256(after), created: !existed, replacements, status: "APPLIED" };
}

async function codeBash(args) {
  const workspaceId = safeWorkspaceId(args.workspaceId);
  const current = workspace(workspaceId);
  const action = text(args.action || "run").toLowerCase();
  if (["status", "logs", "stop"].includes(action)) {
    const record = executionFor(workspaceId, args.executionId);
    if (action === "stop") {
      if (record.status === "RUNNING") {
        record.status = "STOPPING";
        stopProcessTree({ ...record, status: "RUNNING" }, "SIGTERM");
        await new Promise((resolve) => setTimeout(resolve, 150));
        if (record.status === "STOPPING") {
          try { process.kill(record.pid, 0); stopProcessTree({ ...record, status: "RUNNING" }, "SIGKILL"); } catch { /* stopped */ }
          record.status = "STOPPED";
          record.finishedAt = new Date().toISOString();
        }
      }
      return { executionId: record.executionId, pid: record.pid, status: record.status, exitCode: record.exitCode, logRef: record.logRef };
    }
    if (action === "logs") {
      const output = readLog(record, boundedInt(args.limitBytes, MAX_OUTPUT_BYTES, 1024, MAX_LOG_BYTES));
      return { executionId: record.executionId, pid: record.pid, status: record.status, logRef: record.logRef, output, outputHash: sha256(output), truncated: fs.existsSync(record.logPath) && fs.statSync(record.logPath).size > Buffer.byteLength(output) };
    }
    return { executionId: record.executionId, pid: record.pid, status: record.status, exitCode: record.exitCode, signal: record.signal, startedAt: record.startedAt, finishedAt: record.finishedAt, logRef: record.logRef };
  }
  if (action !== "run") throw new Error(`unsupported code_bash action: ${action}`);
  const tokens = commandTokens(args.command);
  const effect = text(args.expectedEffect || "TEST_OR_BUILD").toUpperCase();
  validateCommand(tokens, current, effect);
  const cwdRelative = safeRelativePath(args.cwd || "", true);
  const cwdCandidate = cwdRelative ? path.join(current.root, cwdRelative) : current.root;
  const cwd = fs.realpathSync(cwdCandidate);
  assertContained(fs.realpathSync(current.root), cwd);
  if (!fs.statSync(cwd).isDirectory()) throw new Error("cwd is not a directory");
  const timeoutMs = boundedInt(args.timeoutMs, DEFAULT_TIMEOUT_MS, 1000, 300_000);
  if (args.background === true) return startBackground(current, tokens, cwd, timeoutMs);
  const result = await runProcess(tokens[0], tokens.slice(1), { cwd, env: scopedEnvironment(current), timeoutMs, outputLimit: MAX_OUTPUT_BYTES });
  const output = maskSecrets(`${result.stdout}${result.stderr ? `${result.stdout ? "\n" : ""}${result.stderr}` : ""}`);
  return {
    workspaceId, commandHash: sha256(tokens.join("\u0000")), exitCode: result.exitCode,
    status: result.exitCode === 0 ? "SUCCEEDED" : result.timedOut ? "TIMED_OUT" : "FAILED",
    output, outputHash: sha256(output), truncated: result.truncated, durationMs: result.durationMs, cwd: cwdRelative || ".", expectedEffect: effect,
  };
}

async function codeDiff(args) {
  const current = workspace(args.workspaceId);
  const diff = (await runGit(current.root, ["diff", "--binary", "--no-ext-diff", current.baseCommit], { timeoutMs: 30_000, outputLimit: 2 * MAX_OUTPUT_BYTES })).stdout;
  const changedFiles = (await runGit(current.root, ["diff", "--name-only", current.baseCommit], { timeoutMs: 10_000 })).stdout.split(/\r?\n/).filter(Boolean);
  const stat = (await runGit(current.root, ["diff", "--stat", current.baseCommit], { timeoutMs: 10_000 })).stdout;
  const head = (await runGit(current.root, ["rev-parse", "HEAD"], { outputLimit: 1024 })).stdout.trim().toLowerCase();
  return { workspaceId: current.workspaceId, baseCommit: current.baseCommit, currentHead: head, changedFiles, diffStat: stat, diffHash: sha256(diff), diffBytes: Buffer.byteLength(diff), diff: diff.length <= MAX_OUTPUT_BYTES ? diff : `${diff.slice(0, MAX_OUTPUT_BYTES)}\n... diff truncated ...` };
}

async function codeCommit(args) {
  const current = workspace(args.workspaceId);
  const before = await codeDiff({ workspaceId: current.workspaceId });
  if (before.changedFiles.length === 0) throw new Error("repair worktree has no changes to commit");
  const message = text(args.message || `ops repair ${current.workspaceId}`);
  if (!message || /[\r\n\0]/.test(message)) throw new Error("commit message is invalid");
  await runGit(current.root, ["config", "user.name", text(args.actor || "orbisops")], { timeoutMs: 10_000 });
  await runGit(current.root, ["config", "user.email", "ops-agent@local"], { timeoutMs: 10_000 });
  await runGit(current.root, ["add", "-A"], { timeoutMs: 30_000 });
  await runGit(current.root, ["commit", "-m", message.includes(current.workspaceId) ? message : `${message} [${current.workspaceId}]`], { timeoutMs: 60_000, outputLimit: MAX_OUTPUT_BYTES });
  const repairCommit = (await runGit(current.root, ["rev-parse", "HEAD"], { outputLimit: 1024 })).stdout.trim().toLowerCase();
  if (!COMMIT_PATTERN.test(repairCommit)) throw new Error("repair commit is invalid");
  return { workspaceId: current.workspaceId, baseCommit: current.baseCommit, repairCommit, diffHash: before.diffHash, changedFiles: before.changedFiles, status: "COMMITTED" };
}

async function cleanupWorkspace(args) {
  const workspaceId = safeWorkspaceId(args.workspaceId);
  const current = workspaces.get(workspaceId);
  let stopped = 0;
  for (const record of executions.values()) {
    if (record.workspaceId === workspaceId && record.status === "RUNNING") {
      stopProcessTree(record, "SIGKILL");
      record.status = "STOPPED";
      record.finishedAt = new Date().toISOString();
      if (record.timer) clearTimeout(record.timer);
      stopped += 1;
    }
  }
  let lspStopped = 0;
  const lspPrefix = `workspace:${workspaceId}:`;
  for (const [sessionKey, lsp] of [...lspSessions.entries()]) {
    if (!sessionKey.startsWith(lspPrefix)) continue;
    lsp.close();
    lspSessions.delete(sessionKey);
    if (lsp.dataDir && fs.existsSync(lsp.dataDir)) fs.rmSync(lsp.dataDir, { recursive: true, force: true });
    lspStopped += 1;
  }
  if (!current) return { workspaceId, status: "NO_TEMP_RESOURCE", worktreeRemoved: false, stoppedExecutions: stopped, lspStopped: lspStopped > 0, stoppedLspSessions: lspStopped };
  if (fs.existsSync(current.root)) {
    const remove = await runGit(current.repositoryRoot, ["worktree", "remove", "--force", current.root], { timeoutMs: 60_000, outputLimit: MAX_OUTPUT_BYTES, allowExitCodes: [0, 128] });
    if (remove.exitCode !== 0 && fs.existsSync(current.root)) throw new Error(remove.stderr || remove.stdout || "worktree cleanup failed");
  }
  workspaces.delete(workspaceId);
  return { workspaceId, status: "CLEANED", worktreeRemoved: true, stoppedExecutions: stopped, lspStopped: lspStopped > 0, stoppedLspSessions: lspStopped };
}

const tools = [
  tool("code_info", "Resolve a registered repository revision to an exact immutable commit and report Code Workspace capabilities.", {
    repositoryId: str(), revision: str(),
  }),
  tool("code_search", "Search tracked code or list files at an exact revision or inside an isolated repair worktree.", {
    repositoryId: str(), revision: str(), workspaceId: str(), mode: enumString(["text", "glob"]),
    query: str(), pattern: str(), limit: integer(), regex: bool(), caseSensitive: bool(), glob: str(),
  }),
  tool("code_read", "Read a bounded text file at an exact revision or repair workspace; returns SHA-256 for read-before-write CAS.", {
    repositoryId: str(), revision: str(), workspaceId: str(), path: str(),
  }, ["path"]),
  tool("code_lsp", "Read-only language intelligence for configured Java/Python LSP providers: definition, references, hover, symbols, diagnostics. File actions auto-detect language by extension; symbols may pass language explicitly. No rename/codeAction/workspaceEdit.", {
    repositoryId: str(), revision: str(), workspaceId: str(), language: enumString(["java", "python"]), action: enumString(["definition", "references", "hover", "symbols", "diagnostics"]), path: str(), line: integer(), character: integer(), query: str(),
  }, ["action"]),
  tool("code_enter_worktree", "Create an isolated Git worktree from a frozen exact baseCommit. Base repository remains read-only.", {
    repositoryId: str(), baseCommit: str(), workspaceId: str(), projectId: str(), serviceId: str(), runId: str(),
  }, ["repositoryId", "baseCommit"]),
  tool("code_apply_patch", "Apply a unified diff or CAS-protected edit/write inside an isolated repair worktree only.", {
    workspaceId: str(), mode: enumString(["patch", "edit", "write"]), path: str(), expectedSha256: str(), oldString: str(), newString: str(), replaceAll: bool(), content: str(), unifiedDiff: str(),
  }, ["workspaceId"]),
  tool("code_bash", "Run controlled development commands in a repair worktree. Supports background=true and action=status|logs|stop for owned executions.", {
    workspaceId: str(), action: enumString(["run", "status", "logs", "stop"]), command: str(), expectedEffect: str(), cwd: str(), timeoutMs: integer(), background: bool(), executionId: str(), limitBytes: integer(),
  }, ["workspaceId"]),
  tool("code_diff", "Compute real Git diff metadata and SHA-256 from frozen baseCommit to current repair worktree.", { workspaceId: str() }, ["workspaceId"]),
  tool("code_commit", "Create a real Git repair commit inside the isolated worktree; never pushes.", { workspaceId: str(), message: str(), actor: str() }, ["workspaceId"]),
  tool("code_cleanup", "Idempotently stop workspace-owned background processes/LSP and remove the repair worktree.", { workspaceId: str() }, ["workspaceId"]),
];

function str() { return { type: "string" }; }
function integer() { return { type: "integer" }; }
function bool() { return { type: "boolean" }; }
function enumString(values) { return { type: "string", enum: values }; }
function tool(name, description, properties, required = []) {
  return { name, description, inputSchema: { type: "object", properties, required, additionalProperties: false } };
}

async function callTool(name, args = {}) {
  if (name === "code_info") return codeInfo(args);
  if (name === "code_search") return codeSearch(args);
  if (name === "code_read") return codeRead(args);
  if (name === "code_lsp") return codeLsp(args);
  if (name === "code_enter_worktree") return enterWorktree(args);
  if (name === "code_apply_patch") return applyPatch(args);
  if (name === "code_bash") return codeBash(args);
  if (name === "code_diff") return codeDiff(args);
  if (name === "code_commit") return codeCommit(args);
  if (name === "code_cleanup") return cleanupWorkspace(args);
  throw new Error(`Unknown tool: ${name}`);
}

function write(message) { process.stdout.write(`${JSON.stringify(message)}\n`); }
function ok(id, result = {}) { write({ jsonrpc: "2.0", id, result }); }
function fail(id, code, message, data) { write({ jsonrpc: "2.0", id, error: { code, message, data } }); }
function toolResult(value) { return { content: [{ type: "text", text: JSON.stringify(value) }], isError: false }; }

async function handle(message) {
  if (!message || message.jsonrpc !== "2.0") return;
  const { id, method, params } = message;
  try {
    if (method === "initialize") {
      ok(id, { protocolVersion: params?.protocolVersion || "2024-11-05", capabilities: { tools: {} }, serverInfo: { name: "orbisops-code-workspace-mcp", version: SERVER_VERSION } });
      return;
    }
    if (method === "notifications/initialized") return;
    if (method === "ping") return ok(id, {});
    if (method === "tools/list") return ok(id, { tools });
    if (method === "tools/call") return ok(id, toolResult(await callTool(params?.name, params?.arguments || {})));
    fail(id, -32601, `Method not found: ${method}`);
  } catch (error) {
    fail(id, -32000, error?.message || "Tool execution failed");
  }
}

async function shutdown() {
  for (const record of executions.values()) if (record.status === "RUNNING") stopProcessTree(record, "SIGKILL");
  for (const session of lspSessions.values()) session.close();
  for (const [key, root] of analysisRoots.entries()) {
    const repositoryId = key.split(":", 1)[0];
    const repo = repositories.get(repositoryId);
    if (repo && fs.existsSync(root)) {
      try { await runGit(repo.root, ["worktree", "remove", "--force", root], { timeoutMs: 10_000, allowExitCodes: [0, 128] }); } catch { /* best effort on process shutdown */ }
    }
  }
}

process.on("SIGINT", async () => { await shutdown(); process.exit(0); });
process.on("SIGTERM", async () => { await shutdown(); process.exit(0); });
process.on("exit", () => {
  for (const record of executions.values()) if (record.status === "RUNNING") stopProcessTree(record, "SIGKILL");
  for (const session of lspSessions.values()) session.close();
});

const rl = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
rl.on("line", (line) => {
  if (!line.trim()) return;
  try { void handle(JSON.parse(line)); } catch (error) { fail(null, -32700, "Parse error", error.message); }
});
rl.on("close", async () => { await shutdown(); process.exit(0); });
