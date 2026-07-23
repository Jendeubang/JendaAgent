# Step 20 Addendum: Dynamic Tool Routing Priority

## Observed Issue

A prompt such as `生成一张赛博朋克风格城市海报` with a reference image was routed to OCR and image editing because the old rule treated every image as OCR input and matched `风格` before generation.

## Fix

Dynamic Plan-Solve now uses this priority:

```text
explicit generation -> IMAGE_GENERATE
otherwise explicit edit -> IMAGE_EDIT
explicit OCR wording with an image -> OCR
```

An attached image is now multimodal context by default, not an implicit OCR request. The selection uses Java Unicode escapes in the compatibility script so Windows PowerShell encoding cannot corrupt Chinese keyword matching.
