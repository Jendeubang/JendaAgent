# Step 21 Validation Fix

## Problem

The normalized internal tool request originally required `image_urls` for every tool.
This made text-to-image requests fail with HTTP 400 before they reached the Qwen image gateway.

## Fix

- `image_urls` is optional in the shared request DTO.
- The OCR controller explicitly requires at least one image URL.
- Text-to-image requests can now use an empty `image_urls` array.

## Apply

Run `tools/fix-step21-image-generate-validation.ps1`, then rebuild and restart the backend.
