# OrbisOps Web

Browser console for OrbisOps.

## Requirements

- Node.js 20+
- npm 10+
- an OrbisOps backend for interactive use

## Development

```bash
npm ci
npm run dev
```

The development client uses `http://127.0.0.1:8099` as a convenience default. Point it at another backend without editing source:

```bash
ORBISOPS_API_BASE_URL=http://localhost:8099 npm run dev
```

Production builds default to a relative API URL. The Docker image serves the SPA through Nginx and proxies `/api/` to `ORBISOPS_BACKEND_UPSTREAM`, so a deployment does not embed a developer host address in browser assets.

## Verification

```bash
npm run verify
npm run test:e2e
```

`verify` runs repository release hygiene, TypeScript checking, Vitest and a production build. Playwright covers the product navigation and critical interaction flows in Chrome, including narrow layouts.

## Docker

The repository root `compose.yml` is the supported full deployment path. To build the web image independently:

```bash
IMAGE_NAME=orbisops/web IMAGE_TAG=2.0.0 ./build.sh
```

Runtime backend routing is configurable:

```text
ORBISOPS_BACKEND_UPSTREAM=http://backend:8099
```

Do not place model keys, database credentials, administrator passwords or service tokens in frontend build variables. Browser configuration contains only public routing information.

## Workflow execution contract

Workflow Agent nodes use three execution modes:

- `DIRECT` — deterministic authorized application/tool execution
- `LLM` — one bounded model call
- `REACT` — bounded autonomous reason/action/observation execution

MCP, Skills, RAG and other resources are capabilities bound to the Agent/runtime; they are not independent workflow execution engines.

## License

Apache License 2.0. See [LICENSE](../LICENSE).
