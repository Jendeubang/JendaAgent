"use client";

import { AppstoreOutlined, LoginOutlined, LogoutOutlined, UserOutlined } from "@ant-design/icons";
import { Button, Dropdown } from "antd";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { logoutAgentSession, readAgentAuthSession, type AgentAuthSession } from "../lib/agentAuth";

export function AgentAccountMenu() {
  const router = useRouter();
  const [session, setSession] = useState<AgentAuthSession>();
  useEffect(() => setSession(readAgentAuthSession()), []);
  if (!session) return <Button icon={<LoginOutlined />} onClick={() => router.push("/login")}>{"\u767b\u5f55"}</Button>;
  return <Dropdown menu={{ items: [
    { key: "identity", disabled: true, label: `\u8d26\u53f7\uff1a${session.username}` },
    { type: "divider" },
    { key: "assets", icon: <AppstoreOutlined />, label: "\u8d44\u4ea7\u4e2d\u5fc3", onClick: () => router.push("/assets") },
    { key: "logout", icon: <LogoutOutlined />, label: "\u9000\u51fa\u767b\u5f55", onClick: () => { void logoutAgentSession().finally(() => router.replace("/login")); } },
  ] }} trigger={["click"]}>
    <Button icon={<UserOutlined />}>{session.username}</Button>
  </Dropdown>;
}