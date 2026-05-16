# Database migrations

`manifest.tsv` is the authoritative OrbisOps MySQL migration inventory.

- Apply migrations in ascending version order.
- Verify each SQL file against the SHA-256 checksum before execution.
- Migration `001` is schema-only and contains no default users, credentials, model providers, projects, or MCP bindings.
- Production credentials are external configuration and must never be stored in migration files.
