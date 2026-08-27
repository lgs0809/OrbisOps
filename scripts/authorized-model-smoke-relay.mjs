// Scoped test relay. Node's native HTTP parser handles Spring's chunked body.
// Secrets enter through stdin; no endpoint, body, or credential is logged.
import http from "node:http";
import { createHash } from "node:crypto";

let configText = "";
for await (const chunk of process.stdin) {
  configText += chunk;
  if (configText.length > 65536) throw new Error("Relay configuration too large");
}
const config = JSON.parse(configText);
const url = new URL(config.url);
if (url.protocol !== "https:" || url.username || url.password || !config.key) {
  throw new Error("Relay requires an existing HTTPS provider");
}
const attempts = [];
const hash = (data) => createHash("sha256").update(data).digest("hex");
const server = http.createServer(async (request, response) => {
  const respond = (status, data) => {
    response.writeHead(status, { "Content-Type": "application/json", "Content-Length": Buffer.byteLength(data) });
    response.end(data);
  };
  if (request.method !== "POST" || request.url !== "/v1/chat/completions"
      || request.headers.authorization !== "Bearer local-evaluation-relay") {
    respond(403, '{"error":{"message":"Relay scope rejected"}}');
    return;
  }
  let record;
  try {
    const chunks = [];
    let size = 0;
    for await (const chunk of request) {
      size += chunk.length;
      if (size > 1024 * 1024) throw new Error("Request too large");
      chunks.push(chunk);
    }
    const body = Buffer.concat(chunks);
    const payload = JSON.parse(body.toString());
    if (payload.model !== "gpt-5.6-luna" || payload.stream === true) {
      respond(400, '{"error":{"message":"Only the agreed non-streaming Luna smoke request is allowed"}}');
      return;
    }
    record = { requestSha256: hash(body), requestedModel: payload.model };
    attempts.push(record);
    const upstream = await fetch(url, {
      method: "POST", headers: { Authorization: `Bearer ${config.key}`, "Content-Type": "application/json" },
      body, signal: AbortSignal.timeout(40000), redirect: "error",
    });
    record.httpStatus = upstream.status;
    if (!upstream.ok) {
      // Provider error details may contain private metadata; retain status only.
      await upstream.body?.cancel();
      respond(upstream.status, '{"error":{"message":"Authorized upstream rejected this attempt"}}');
      return;
    }
    const chunksOut = [];
    let responseSize = 0;
    for await (const chunk of upstream.body) {
      responseSize += chunk.length;
      if (responseSize > 4 * 1024 * 1024) throw new Error("Response too large");
      chunksOut.push(chunk);
    }
    const data = Buffer.concat(chunksOut);
    const parsed = JSON.parse(data.toString());
    record.responseSha256 = hash(data);
    record.returnedModel = parsed.model;
    // Some gateways add account/billing receipts inside usage. Keep token counts
    // only; the hash still identifies the unchanged full provider response.
    record.usage = Object.fromEntries(["prompt_tokens", "completion_tokens", "total_tokens"]
      .filter((name) => Number.isFinite(parsed.usage?.[name]))
      .map((name) => [name, parsed.usage[name]]));
    respond(upstream.status, data); // Preserve actual provider bytes and identity.
  } catch {
    if (record) record.transportFailure = true;
    respond(502, '{"error":{"message":"Authorized upstream attempt unavailable"}}');
  }
});
server.requestTimeout = 50000;
server.listen(0, "127.0.0.1", () => process.stdout.write(JSON.stringify({ port: server.address().port }) + "\n"));
process.on("SIGTERM", () => {
  server.close(() => {
    process.stdout.write(JSON.stringify({ attempts }) + "\n");
    process.exit(0);
  });
});
