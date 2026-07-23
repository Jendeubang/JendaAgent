# Step 13 Frontend API Environment

## Local Development Address

The Spring Boot development server runs on port `19090`. Next.js only loads
`.env.local` automatically; it does not load `.env.example`.

`showcase/.env.local` therefore sets:

```text
NEXT_PUBLIC_AGENT_API_BASE_URL=http://127.0.0.1:19090
```

Restart `corepack pnpm dev` after changing this value. The value is embedded in
the browser bundle at Next.js startup, so changing a running backend alone is
not sufficient.
