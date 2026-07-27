# Step 68: ReAct Sequential Tool Continuation

## Problem Fixed

A ReAct request such as "recognize the text, extract the copy, then generate a promotional poster" executed OCR in round one but ended in round two. The deterministic fallback treated any observation as a completed task.

## Runtime Rule

When all of the following are true:

- OCR completed successfully in an earlier observation.
- The original user request includes both text extraction and visual generation intent.
- No image generation observation exists yet.

Then a `FINISH` decision is replaced with `IMAGE_GENERATE`. The generated tool input includes the original request and the OCR observation so the poster can use the extracted copy.

## Guardrails

- Ordinary OCR-only requests still finish after OCR.
- Once `IMAGE_GENERATE` has been observed, the loop may finish normally.
- Existing max-step, timeout, prompt optimization, and repeated-tool protections remain unchanged.

## Verification

In ReAct mode, upload an image containing text and enter:

```text
识别图片文字，提取核心文案，再生成一张对应主题的宣传海报
```

Expected timeline: `OCR` in round 1, `IMAGE_GENERATE` in round 2, image output, then delivery summary.