# Step 40: Curated Tool Catalog And Interactive Filters

## Catalog

The Tool page now contains exactly eight user-facing tools.

### Image Processing

- Image Upscale
- SeedVR2 Enhancement
- Image Layering
- Product Refinement

### Creative Generation

- Character Setting Sheet
- Emoji Sticker Pack

### Commerce Tools

- Ecommerce Promotion Poster
- Product Detail Image

Removed catalog entries:

- Background Removal
- Figure Style Conversion
- Floating Island 3D Model
- Christmas Decoration 3D Character
- Ecommerce Scene Image

## Interaction

- `全部` / `图像处理` / `创意生成` / `电商工具` now filter the cards immediately.
- Search filters by each tool's name and description while keeping the active category filter.
- The four new tool routes have their screenshot-derived title, theme color, upload label, parameters, JSON preview, empty result, processing, result, download, and mobile layout.

## Routes

- `/zh/tool/character-setting-sheet`
- `/zh/tool/ecommerce-promotion-poster`
- `/zh/tool/emoji-sticker`
- `/zh/tool/product-detail-image`

## Validation

- TypeScript: passed.
- Next.js production build: passed.
- Deployment verification is performed after the showcase Docker container rebuild.
