"use client";

import { LockOutlined, MobileOutlined, SafetyCertificateOutlined, UserOutlined } from "@ant-design/icons";
import { Button, Input, Tabs, message } from "antd";
import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { saveAgentAuthSession, type AgentAuthSession } from "../../lib/agentAuth";
import styles from "./page.module.css";

const apiBaseUrl = process.env.NEXT_PUBLIC_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
type Mode = "login" | "register" | "reset";

export default function LoginPage() {
  const router = useRouter();
  const [mode, setMode] = useState<Mode>("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [sendingCode, setSendingCode] = useState(false);

  const sendCode = async () => {
    setSendingCode(true);
    try {
      const purpose = mode === "register" ? "REGISTER" : "RESET_PASSWORD";
      const response = await fetch(`${apiBaseUrl}/api/v1/auth/sms/code`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ phone, purpose }) });
      const payload = await response.json().catch(() => undefined) as { debugCode?: string; message?: string } | undefined;
      if (!response.ok) throw new Error(payload?.message || `SMS request failed: ${response.status}`);
      message.success(payload?.debugCode ? `\u5f00\u53d1\u9a8c\u8bc1\u7801\uff1a${payload.debugCode}` : "\u9a8c\u8bc1\u7801\u5df2\u53d1\u9001");
    } catch (error) { message.error(error instanceof Error ? error.message : "\u9a8c\u8bc1\u7801\u53d1\u9001\u5931\u8d25"); }
    finally { setSendingCode(false); }
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setLoading(true);
    try {
      const endpoint = mode === "login" ? "login" : mode === "register" ? "register" : "password/reset";
      const body = mode === "login" ? { username, password } : mode === "register" ? { username, password, phone, code } : { phone, password, code };
      const response = await fetch(`${apiBaseUrl}/api/v1/auth/${endpoint}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
      const payload = await response.json().catch(() => undefined) as AgentAuthSession & { message?: string };
      if (!response.ok) throw new Error(payload?.message || `Request failed: ${response.status}`);
      saveAgentAuthSession(payload);
      message.success(mode === "login" ? "\u767b\u5f55\u6210\u529f" : mode === "register" ? "\u6ce8\u518c\u6210\u529f" : "\u5bc6\u7801\u5df2\u91cd\u7f6e");
      router.replace("/agent-studio");
    } catch (error) { message.error(error instanceof Error ? error.message : "\u64cd\u4f5c\u5931\u8d25"); }
    finally { setLoading(false); }
  };

  const requiresCode = mode !== "login";
  return <main className={styles.page}><section className={styles.card}>
    <p>JENDA AGENT / ACCOUNT</p><h1>{"\u8fdb\u5165\u4f60\u7684"}<br /><em>{"\u79c1\u4eba\u5de5\u4f5c\u533a"}</em></h1>
    <span>{"\u77ed\u671f Access Token + \u53ef\u64a4\u9500 Refresh Token\uff0c\u767b\u51fa\u540e\u7acb\u5373\u5931\u6548\u3002"}</span>
    <Tabs activeKey={mode} onChange={(value) => setMode(value as Mode)} items={[{ key: "login", label: "\u767b\u5f55" }, { key: "register", label: "\u77ed\u4fe1\u6ce8\u518c" }, { key: "reset", label: "\u627e\u56de\u5bc6\u7801" }]} />
    <form onSubmit={(event) => void submit(event)}>
      {mode !== "reset" && <label>{"\u8d26\u53f7"}<Input prefix={<UserOutlined />} value={username} onChange={(event) => setUsername(event.target.value)} placeholder="3-32 \u4f4d\u5b57\u6bcd\u3001\u6570\u5b57\u3001_ \u6216 -" /></label>}
      {requiresCode && <label>{"\u624b\u673a\u53f7"}<Input prefix={<MobileOutlined />} value={phone} onChange={(event) => setPhone(event.target.value)} placeholder="11 \u4f4d\u4e2d\u56fd\u5927\u9646\u624b\u673a\u53f7" /></label>}
      <label>{"\u5bc6\u7801"}<Input.Password prefix={<LockOutlined />} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="\u81f3\u5c11 8 \u4f4d" /></label>
      {requiresCode && <label>{"\u77ed\u4fe1\u9a8c\u8bc1\u7801"}<div className={styles.codeLine}><Input prefix={<SafetyCertificateOutlined />} value={code} onChange={(event) => setCode(event.target.value)} placeholder="6 \u4f4d\u9a8c\u8bc1\u7801" /><Button htmlType="button" loading={sendingCode} onClick={() => void sendCode()}>{"\u53d1\u9001\u9a8c\u8bc1\u7801"}</Button></div></label>}
      <Button type="primary" htmlType="submit" loading={loading} block>{mode === "login" ? "\u767b\u5f55" : mode === "register" ? "\u9a8c\u8bc1\u5e76\u6ce8\u518c" : "\u91cd\u7f6e\u5bc6\u7801"}</Button>
    </form>
  </section></main>;
}