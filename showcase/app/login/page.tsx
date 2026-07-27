"use client";

import { LockOutlined, MobileOutlined, ReloadOutlined, SafetyCertificateOutlined, UserOutlined } from "@ant-design/icons";
import { Button, Input, Tabs, message } from "antd";
import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { saveAgentAuthSession, type AgentAuthSession } from "../../lib/agentAuth";
import styles from "./page.module.css";

const apiBaseUrl = process.env.NEXT_PUBLIC_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
type Mode = "login" | "register" | "reset";
type Captcha = { captchaId: string; imageDataUrl: string };

export default function LoginPage() {
  const router = useRouter();
  const [mode, setMode] = useState<Mode>("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [captchaAnswer, setCaptchaAnswer] = useState("");
  const [captcha, setCaptcha] = useState<Captcha>();
  const [loading, setLoading] = useState(false);
  const [sendingCode, setSendingCode] = useState(false);

  const loadCaptcha = async () => {
    try {
      const response = await fetch(`${apiBaseUrl}/api/v1/auth/captcha`, { credentials: "include" });
      if (!response.ok) return;
      const payload = await response.json() as Captcha;
      setCaptcha(payload); setCaptchaAnswer("");
    } catch { /* CAPTCHA can be disabled in local development. */ }
  };
  useEffect(() => { void loadCaptcha(); }, []);

  const sendCode = async () => {
    setSendingCode(true);
    try {
      const purpose = mode === "register" ? "REGISTER" : "RESET_PASSWORD";
      const response = await fetch(`${apiBaseUrl}/api/v1/auth/sms/code`, {
        method: "POST", credentials: "include", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ phone, purpose, captchaId: captcha?.captchaId, captchaAnswer }),
      });
      const payload = await response.json().catch(() => undefined) as { debugCode?: string; message?: string } | undefined;
      if (!response.ok) throw new Error(payload?.message || `SMS request failed: ${response.status}`);
      message.success(payload?.debugCode ? `开发验证码：${payload.debugCode}` : "验证码已发送");
      await loadCaptcha();
    } catch (error) { message.error(error instanceof Error ? error.message : "验证码发送失败"); await loadCaptcha(); }
    finally { setSendingCode(false); }
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); setLoading(true);
    try {
      const endpoint = mode === "login" ? "login" : mode === "register" ? "register" : "password/reset";
      const body = mode === "login"
        ? { username, password, captchaId: captcha?.captchaId, captchaAnswer }
        : mode === "register" ? { username, password, phone, code } : { phone, password, code };
      const response = await fetch(`${apiBaseUrl}/api/v1/auth/${endpoint}`, { method: "POST", credentials: "include", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
      const payload = await response.json().catch(() => undefined) as AgentAuthSession & { message?: string } | undefined;
      if (!response.ok) throw new Error(payload?.message || `Request failed: ${response.status}`);
      saveAgentAuthSession(payload as AgentAuthSession);
      message.success(mode === "login" ? "登录成功" : mode === "register" ? "注册成功" : "密码已重置");
      router.replace("/zh/agent");
    } catch (error) { message.error(error instanceof Error ? error.message : "操作失败"); await loadCaptcha(); }
    finally { setLoading(false); }
  };

  const requiresCode = mode !== "login";
  return <main className={styles.page}><section className={styles.card}>
    <p>JENDA AGENT / ACCOUNT</p><h1>进入你的<br /><em>私人工作区</em></h1>
    <span>短期 Access Token + HttpOnly Refresh Cookie，退出后立即失效。</span>
    <Tabs activeKey={mode} onChange={(value) => setMode(value as Mode)} items={[{ key: "login", label: "登录" }, { key: "register", label: "短信注册" }, { key: "reset", label: "找回密码" }]} />
    <form onSubmit={(event) => void submit(event)}>
      {mode !== "reset" && <label>账号<Input prefix={<UserOutlined />} value={username} onChange={(event) => setUsername(event.target.value)} placeholder="3-32 位字母、数字、_ 或 -" /></label>}
      {requiresCode && <label>手机号<Input prefix={<MobileOutlined />} value={phone} onChange={(event) => setPhone(event.target.value)} placeholder="11 位中国大陆手机号" /></label>}
      <label>密码<Input.Password prefix={<LockOutlined />} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="至少 8 位" /></label>
      {captcha && <label>安全验证码<div className={styles.captchaLine}><Input prefix={<SafetyCertificateOutlined />} value={captchaAnswer} onChange={(event) => setCaptchaAnswer(event.target.value.toUpperCase())} placeholder="输入图片字符" /><button className={styles.captchaImage} type="button" onClick={() => void loadCaptcha()} title="换一张验证码"><img src={captcha.imageDataUrl} alt="安全验证码" /><ReloadOutlined /></button></div></label>}
      {requiresCode && <label>短信验证码<div className={styles.codeLine}><Input prefix={<SafetyCertificateOutlined />} value={code} onChange={(event) => setCode(event.target.value)} placeholder="6 位验证码" /><Button htmlType="button" loading={sendingCode} onClick={() => void sendCode()}>发送验证码</Button></div></label>}
      <Button type="primary" htmlType="submit" loading={loading} block>{mode === "login" ? "登录" : mode === "register" ? "验证并注册" : "重置密码"}</Button>
    </form>
  </section></main>;
}