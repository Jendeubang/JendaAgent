# Step 65: OCR Text Normalization

## Problem

Some OCR providers return a geometry-oriented row format:

```text
x,y,width,height,confidence,recognized text
```

The raw rows were passed through tool events and final delivery, exposing coordinates and confidence values to end users.

## Implementation

`OcrTextNormalizer` now removes a prefix only when a line contains exactly five numeric comma-separated geometry fields followed by text. It preserves:

- the recognized text;
- line breaks;
- ordinary numeric content that does not match the geometry-row format.

The normalization runs inside `QwenOcrGatewayClient` before the normalized tool result is emitted. Therefore ReAct observations, tool results, summaries, history replay, and workspace output all receive clean text for newly created OCR runs.

## Verification

`OcrTextNormalizerTest` covers coordinate removal and preserves ordinary numeric input:

```powershell
mvn -q -Dtest=OcrTextNormalizerTest test
```

Existing historical events are immutable and keep their stored raw response. Run OCR again to obtain normalized output.