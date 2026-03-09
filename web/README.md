# OrbisOps Web

OrbisOps Web is the browser console for **OrbisOps — Governed Agentic Operations Platform**.

It provides the user and administrator experiences for:

- project-scoped Agent chat and execution history
- specialized Workflow design and publication
- MCP, model, Skill, memory, RAG, channel, schedule, alert, and incident management
- ChangePackage review, approval, Landing visibility, verification, and rollback status
- durable run inspection and operational audit evidence

The UI does not embed a demo account, password, API key, or service token.

## Requirements

- Node.js 20+
- npm 10+
- a running OrbisOps server

## Development

Install dependencies:

```bash
npm ci
```

Run the development server against a local backend:

```bash
PUBLIC_API_BASE_DOMAIN=http://127.0.0.1:8099 npm run dev
```

The default local web port is controlled by Rsbuild. API configuration is centralized in `src/config/api.ts` and can be supplied through build/runtime environment variables instead of editing source code.

## Verification

```bash
npm run verify
```

`verify` performs:

1. public-release hygiene checks
2. TypeScript type checking
3. Vitest unit/contract tests
4. production build

For browser E2E tests:

```bash
npm run test:e2e
```

## Workflow execution contract

The Workflow builder exposes three canonical Agent execution modes:

- `DIRECT` — deterministic authorized action execution
- `LLM` — one model call without an autonomous tool loop
- `REACT` — bounded autonomous reason/action/observation execution

Review/report/extract/classify are LLM roles, not additional execution engines.

Workflow capability bindings point to MCP, Skill, RAG, Tool, and other Agent resources governed by the OrbisOps server.

## Docker

Build a local image:

```bash
IMAGE_NAME=orbisops/web IMAGE_TAG=0.1.0 ./build.sh
```

Build and publish a multi-architecture image:

```bash
IMAGE_NAME=your-registry/orbisops-web \
IMAGE_TAG=0.1.0 \
./build-arm-amd.sh
```

The Dockerfile uses public Node and Nginx base images. The backend endpoint is a build argument and is not hard-coded to a private deployment.

## Configuration

See `config/orbisops-web.env.example` for the public API endpoint variable.

Never place backend provider keys, database credentials, administrator passwords, or service tokens in frontend environment variables. Browser configuration must contain public endpoint information only.

## License

Apache License 2.0. See [LICENSE](../LICENSE).
