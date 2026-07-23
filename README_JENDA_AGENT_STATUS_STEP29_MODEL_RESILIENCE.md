# Step 29: Model Resilience and Image Tool Routing

## Fixed

- Planning and summary calls now degrade to rule-based orchestration when the OpenAI-compatible model times out or returns an error.
- A failed planning model no longer terminates the SSE run before image tools can execute.
- Image generation is selected before image editing for text-only requests, including prompts mentioning anime, posters, or style.
- Image editing is selected only when at least one reference `image_url` exists.
- The internal Qwen image-edit gateway reports missing reference images as HTTP `400`, not `500`.

## Expected Behaviour

| Input | Selected image tool |
| --- | --- |
| `生成一张动漫风格人物图` with no upload | `IMAGE_GENERATE` |
| Upload image + `替换背景为雨夜城市` | `IMAGE_EDIT` |
| Upload image + `识别图片文字` | `OCR` |

Model timeout may still be recorded in the provider logs, but agent execution falls back to deterministic routing and remains visible in SSE history.
