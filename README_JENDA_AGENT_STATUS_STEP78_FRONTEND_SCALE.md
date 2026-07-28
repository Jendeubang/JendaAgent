# Step 78 - Frontend Display Scale

## Change

Desktop browser rendering now applies a global `1.12` CSS zoom through `showcase/app/globals.css` at viewport widths of 768px and above.

## Rationale

The product pages use many pixel-based component dimensions. Increasing the root font size would leave cards, images, icons, and spacing unchanged. CSS zoom scales the existing layout as a unit, preserving the established visual system and Ant Design X component proportions.

## Responsive behavior

- Desktop and tablet (768px and above): 112% display scale.
- Mobile: unchanged to avoid reducing available layout width or creating overflow.

No component colors, content, interaction, or layout structure changed.