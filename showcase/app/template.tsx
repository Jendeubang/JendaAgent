"use client";

import "@ant-design/v5-patch-for-react-19";
import type { ReactNode } from "react";

/** Loads the official Ant Design v5 bridge before route components mount. */
export default function Template({ children }: { children: ReactNode }) {
  return <>{children}</>;
}
