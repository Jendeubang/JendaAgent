"use client";

import { Button, Card, Input, Select, Tag, message } from "antd";
import { useEffect, useState } from "react";
import { agentFetch, readAgentAuthSession } from "../../lib/agentAuth";
import styles from "./page.module.css";

type Usage = { planCode: string; dailyModelLimit: number; todayModelCalls: number; monthModelCalls: number; monthCostMicros: number; monthlyCostLimitMicros: number };
const apiBaseUrl = process.env.NEXT_PUBLIC_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";

export default function AdminPage() {
  const session = readAgentAuthSession();
  const [usage, setUsage] = useState<Usage>();
  const [userId, setUserId] = useState(session?.userId ?? "");
  const [planCode, setPlanCode] = useState("PRO");
  const [loading, setLoading] = useState(false);

  const loadUsage = async () => {
    const response = await agentFetch(`${apiBaseUrl}/api/v1/account/usage`);
    if (!response.ok) throw new Error(response.status === 401 ? "登录已失效" : "无法读取用量");
    setUsage(await response.json() as Usage);
  };
  useEffect(() => { void loadUsage().catch((error) => message.error(error.message)); }, []);

  const updatePlan = async () => {
    if (!userId.trim()) return message.warning("请输入目标用户 ID");
    setLoading(true);
    try {
      const response = await agentFetch(`${apiBaseUrl}/api/v1/admin/users/${encodeURIComponent(userId.trim())}/plan`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ planCode }) });
      if (!response.ok) throw new Error(response.status === 403 ? "当前账号不是管理员，请检查 AGENT_AUTH_ADMIN_USERNAMES" : "套餐更新失败");
      message.success("套餐已更新");
      await loadUsage();
    } catch (error) { message.error(error instanceof Error ? error.message : "套餐更新失败"); }
    finally { setLoading(false); }
  };

  return <main className={styles.page}><section className={styles.hero}><span>JENDA / ADMIN</span><h1>安全与成本控制台</h1><p>查看当前用量，并由管理员调整用户模型调用套餐。</p></section><section className={styles.grid}>
    <Card title="我的用量" className={styles.card} extra={<Button onClick={() => void loadUsage()}>刷新</Button>}>
      {usage ? <div className={styles.metrics}><Tag color="blue">{usage.planCode}</Tag><p>今日模型调用 <strong>{usage.todayModelCalls}</strong> / {usage.dailyModelLimit}</p><p>本月调用 <strong>{usage.monthModelCalls}</strong></p><p>本月估算成本 <strong>{(usage.monthCostMicros / 1_000_000).toFixed(2)}</strong> 元</p></div> : <p>正在读取用量...</p>}
    </Card>
    <Card title="用户套餐" className={styles.card}>
      <label>目标用户 ID<Input value={userId} onChange={(event) => setUserId(event.target.value)} placeholder="从用户资产或会话记录中复制 userId" /></label>
      <label>套餐<Select value={planCode} onChange={setPlanCode} options={[{ value: "FREE", label: "FREE · 20 次/日" }, { value: "PRO", label: "PRO · 200 次/日" }, { value: "ADMIN", label: "ADMIN · 10000 次/日" }]} /></label>
      <Button type="primary" loading={loading} onClick={() => void updatePlan()}>保存套餐</Button>
      <small>后端将校验当前登录用户名是否在 `AGENT_AUTH_ADMIN_USERNAMES` 中。</small>
    </Card>
  </section></main>;
}