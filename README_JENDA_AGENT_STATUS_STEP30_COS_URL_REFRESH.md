# Step 30 Follow-up: COS URL Refresh

## Issue

The asset table contains the URL returned when an upload or a generation was
completed. COS GET URLs are intentionally short lived, so using the stored URL
directly made old image cards fail after the configured expiry time.

## Fix

`AgentAssetService.page` now creates a fresh signed COS GET URL for every asset
returned to the current JWT user. It uses the persisted `object_key`; for older
generated records without that column populated, it safely derives the object
key from a URL under the configured COS prefix.

## Safety

- The URL is refreshed only after the existing `owner_user_id` scoped query.
- Only objects under `agent.storage.cos.prefix` are re-signed or deleted.
- Non-COS external URLs remain unchanged.
