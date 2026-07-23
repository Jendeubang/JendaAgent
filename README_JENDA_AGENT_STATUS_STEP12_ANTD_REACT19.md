# Step 12 Ant Design React 19 Compatibility

## Change

The showcase uses React 19 with Ant Design v5. The official
`@ant-design/v5-patch-for-react-19` package is installed and imported from the
new Next.js client template entry point.

## Reason

Ant Design v5 primarily targets React 16 through 18. The bridge restores the
legacy rendering behavior needed by v5 static APIs such as Message,
Notification, and Modal when running with React 19.

## Verification

Restart the Next.js development server after this dependency change. The
`[antd: compatible]` browser-console warning should no longer appear.

## Technical Debt

The root layout could not be modified because of the current workspace source
file isolation issue. The template entry provides the required global import.
When normal source edits are available, the import can be moved to
`showcase/app/layout.tsx` and the template can be removed.
