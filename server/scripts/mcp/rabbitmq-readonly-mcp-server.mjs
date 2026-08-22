#!/usr/bin/env node

import readline from "node:readline";

const BASE_URL = String(process.env.RABBITMQ_MANAGEMENT_URL || "http://127.0.0.1:15672").replace(/\/+$/, "");
const USERNAME = process.env.RABBITMQ_USERNAME || "guest";
const PASSWORD = process.env.RABBITMQ_PASSWORD || "guest";
const ALLOWED_QUEUES = new Set(JSON.parse(process.env.RABBITMQ_ALLOWED_QUEUES_JSON || "[]"));
const MAX_ROWS = Number(process.env.RABBITMQ_MCP_MAX_ROWS || 100);
const TIMEOUT_MS = Number(process.env.RABBITMQ_TIMEOUT_MS || 10000);

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
  return { content: [{ type: "text", text: JSON.stringify(value, null, 2) }], isError: false };
}

function allowed(queue) {
  return ALLOWED_QUEUES.size === 0 || ALLOWED_QUEUES.has(queue);
}

async function request(path) {
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: {
      authorization: `Basic ${Buffer.from(`${USERNAME}:${PASSWORD}`).toString("base64")}`,
      accept: "application/json",
    },
    signal: AbortSignal.timeout(TIMEOUT_MS),
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`RabbitMQ HTTP ${response.status}: ${text.slice(0, 500)}`);
  return text ? JSON.parse(text) : {};
}

const tools = [
  {
    name: "rabbitmq_health",
    description: "Read RabbitMQ management health and cluster overview.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false },
  },
  {
    name: "rabbitmq_list_queues",
    description: "List allowed queues with ready, unacknowledged, consumer and publish/deliver rates.",
    inputSchema: {
      type: "object",
      properties: { vhost: { type: "string" }, limit: { type: "integer" } },
      additionalProperties: false,
    },
  },
  {
    name: "rabbitmq_queue_detail",
    description: "Read one allowed queue and its runtime statistics.",
    inputSchema: {
      type: "object",
      properties: { vhost: { type: "string" }, queue: { type: "string" } },
      required: ["queue"],
      additionalProperties: false,
    },
  },
  {
    name: "rabbitmq_list_consumers",
    description: "List consumers attached to allowed queues.",
    inputSchema: {
      type: "object",
      properties: { vhost: { type: "string" }, limit: { type: "integer" } },
      additionalProperties: false,
    },
  },
];

function queueView(queue) {
  return {
    vhost: queue.vhost,
    name: queue.name,
    state: queue.state,
    durable: queue.durable,
    messages: queue.messages,
    messagesReady: queue.messages_ready,
    messagesUnacknowledged: queue.messages_unacknowledged,
    consumers: queue.consumers,
    memory: queue.memory,
    publishRate: queue.message_stats?.publish_details?.rate ?? 0,
    deliverRate: queue.message_stats?.deliver_get_details?.rate ?? 0,
    ackRate: queue.message_stats?.ack_details?.rate ?? 0,
  };
}

async function callTool(name, args = {}) {
  if (name === "rabbitmq_health") {
    const [overview, alarms] = await Promise.all([request("/api/overview"), request("/api/health/checks/alarms")]);
    return {
      clusterName: overview.cluster_name,
      rabbitmqVersion: overview.rabbitmq_version,
      queueTotals: overview.queue_totals,
      objectTotals: overview.object_totals,
      messageStats: overview.message_stats,
      alarms,
    };
  }
  if (name === "rabbitmq_list_queues") {
    const vhost = String(args.vhost || "/");
    const limit = Math.min(Math.max(1, Number(args.limit || 50)), MAX_ROWS);
    const queues = await request(`/api/queues/${encodeURIComponent(vhost)}`);
    const rows = queues.filter((queue) => allowed(queue.name)).slice(0, limit).map(queueView);
    return { vhost, rowCount: rows.length, rows };
  }
  if (name === "rabbitmq_queue_detail") {
    const vhost = String(args.vhost || "/");
    const queue = String(args.queue || "");
    if (!allowed(queue)) throw new Error("Queue is outside the configured visibility scope");
    try {
      return queueView(await request(`/api/queues/${encodeURIComponent(vhost)}/${encodeURIComponent(queue)}`));
    } catch (error) {
      const message = String(error?.message || error || "");
      if (message.startsWith("RabbitMQ HTTP 404:")) {
        return {
          vhost,
          queue,
          status: "NOT_FOUND",
          exists: false,
          reasonCode: "RABBITMQ_QUEUE_NOT_FOUND",
          message: "RabbitMQ queue does not exist in the requested vhost.",
        };
      }
      throw error;
    }
  }
  if (name === "rabbitmq_list_consumers") {
    const vhost = String(args.vhost || "/");
    const limit = Math.min(Math.max(1, Number(args.limit || 100)), MAX_ROWS);
    let consumers;
    try {
      consumers = await request(`/api/consumers/${encodeURIComponent(vhost)}`);
    } catch (error) {
      const message = String(error?.message || error || "");
      if (message.includes("Stats in management UI are disabled on this node")) {
        return {
          vhost,
          status: "UNAVAILABLE",
          reasonCode: "RABBITMQ_MANAGEMENT_STATS_DISABLED",
          message: "RabbitMQ management statistics are disabled; consumer details are unavailable on this node.",
          rowCount: 0,
          rows: [],
        };
      }
      throw error;
    }
    const rows = consumers
      .filter((item) => allowed(item.queue?.name))
      .slice(0, limit)
      .map((item) => ({
        queue: item.queue?.name,
        consumerTag: item.consumer_tag,
        channelDetails: item.channel_details,
        ackRequired: item.ack_required,
        prefetchCount: item.prefetch_count,
        active: item.active,
      }));
    return { vhost, rowCount: rows.length, rows };
  }
  throw new Error(`Unknown tool: ${name}`);
}

async function handle(message) {
  if (!message || message.jsonrpc !== "2.0") return;
  const { id, method, params } = message;
  try {
    if (method === "initialize") {
      return ok(id, {
        protocolVersion: params?.protocolVersion || "2024-11-05",
        capabilities: { tools: {} },
        serverInfo: { name: "local-rabbitmq-readonly-mcp-server", version: "2.0.0" },
      });
    }
    if (method === "notifications/initialized") return;
    if (method === "ping") return ok(id, {});
    if (method === "tools/list") return ok(id, { tools });
    if (method === "tools/call") return ok(id, toolResult(await callTool(params?.name, params?.arguments || {})));
    return fail(id, -32601, `Method not found: ${method}`);
  } catch (error) {
    return fail(id, -32000, error.message || "Tool execution failed");
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
