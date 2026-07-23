# Step 57: PromptOpAgent RAG

## Delivered

PromptOpAgent now uses a Qdrant-backed retrieval path before optimizing an image-generation or image-editing prompt.

```text
image-guidelines.json (versioned seed source)
  -> OpenAI-compatible embedding API (DashScope text-embedding-v4 by default)
  -> Qdrant collection: jenda_prompt_knowledge_v1
  -> vector retrieval with score threshold
  -> PromptOpAgent
  -> prompt_optimization SSE event
  -> image tool receives optimized prompt
```

The local JSON file remains as the import seed and safe fallback. It is no longer the primary serving index when Qdrant and embeddings are available.

## Runtime Behavior

- ReAct optimizes IMAGE_GENERATE and IMAGE_EDIT input immediately before `Act`.
- Plan-Solve optimizes IMAGE_GENERATE and IMAGE_EDIT DAG tasks before they enter the observable executor.
- The `prompt_optimization` event contains original and optimized prompts, rule IDs, RAG hit source, score, and knowledge version.
- `agent_prompt_optimization_message` stores the new provenance fields in MySQL for replay.
- If Qdrant, embeddings, or the model are unavailable, PromptOp falls back to local keyword retrieval and finally to the original prompt. A RAG outage never blocks image execution.

## User Controls

`/zh/agent` has a **Prompt RAG optimization** checkbox in the model-preference popover.

- Enabled: image prompts are optimized and the UI shows before/after comparison plus retrieved knowledge sources.
- Disabled: the original prompt is sent to the image provider unchanged for that request.
- The choice is stored locally in the browser and is sent as `promptOptimizationEnabled` with each run request.

## Required Deployment Variables

Keep secrets only in `deploy/.env`, never in Git.

```env
AGENT_RAG_ENABLED=true
AGENT_RAG_QDRANT_API_KEY=your-random-key
AGENT_RAG_COLLECTION=jenda_prompt_knowledge_v1
AGENT_RAG_EMBEDDING_MODEL=text-embedding-v4
AGENT_RAG_EMBEDDING_DIMENSIONS=1024
```

Optional overrides are only needed when embeddings use a different DashScope workspace/key than the chat model:

```env
AGENT_RAG_EMBEDDING_BASE_URL=https://your-workspace.cn-beijing.maas.aliyuncs.com/compatible-mode/v1
AGENT_RAG_EMBEDDING_API_KEY=your-dashscope-key
```

When both optional values are empty, the backend reuses `AGENT_RUNTIME_MODEL_BASE_URL` and `AGENT_RUNTIME_MODEL_API_KEY`.

## Apply and Verify

From the repository root:

```powershell
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml up --build -d
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml ps
```

Qdrant is internal-only; port `6333` is intentionally not published to the host. Use a backend image task in `/zh/agent` to trigger the initial import. The first image task may take slightly longer because it embeds and imports the five seed rules.

Check backend logs without exposing the vector database:

```powershell
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml logs --tail=120 backend
```

Expected message:

```text
Imported 5 PromptOp knowledge records into Qdrant version image-guidelines-v1-...
```

## Knowledge Versioning

The version is derived from the seed content hash. Changing `src/main/resources/prompt-op/image-guidelines.json` generates a new versioned point ID on the next new backend process. Keep collection name and embedding dimension stable. To change embedding dimension, create a new collection name, for example `jenda_prompt_knowledge_v2`.

## Follow-up Work

1. Add a protected knowledge-management page for uploading new style/tool guidance files.
2. Add a background reindex job and delete stale knowledge versions after approval.
3. Add per-user custom knowledge namespaces and retrieval filters.
4. Add retrieval quality metrics, prompt A/B evaluation, and provider token-cost tracking.
