"use client";

import { ConfigProvider } from "antd";
import { Provider } from "react-redux";
import { store } from "../store";

export function Providers({ children }: { children: React.ReactNode }) {
  return <Provider store={store}><ConfigProvider theme={{ token: { colorPrimary: "#0f766e", borderRadius: 12, fontFamily: "Aptos, 'Noto Sans SC', 'Microsoft YaHei', sans-serif" } }}>{children}</ConfigProvider></Provider>;
}
