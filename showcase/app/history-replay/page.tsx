"use client";

import { ClockCircleOutlined, HistoryOutlined, ReloadOutlined } from "@ant-design/icons";
import { Button, Input, Tag } from "antd";
import { useState } from "react";
import styles from "./page.module.css";

type BackendEvent = {
  eventId: string;
  runId: string;
  messageType: string;
  status: string;
  agent: string;
  occurredAt?: string;
  payload: Record<string, unknown>;
};

const apiBaseUrl = process.env.NEXT_PUBLIC_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const defaultSessionId = "showcase-live-multimodal-session";

function textOf(value: unknown, fallback = "") {
  return typeof value === "string" ? value : fallback;
}

function eventTitle(event: BackendEvent) {
  const titles: Record<string, string> = {
    run_started: "多模态上下文已附加",
    plan: "任务规划",
    task: "子任务执行",
    tool_call: "工具调用",
    tool_result: "工具结果",
    image: "图像产物",
    summary: "交付汇总",
    run_completed: "任务完成",
  };
  return textOf(event.payload.title, titles[event.messageType] ?? event.messageType);
}

function eventContent(event: BackendEvent) {
  if (event.messageType === "run_started") {
    const images = Array.isArray(event.payload.imageUrls) ? event.payload.imageUrls.length : 0;
    return `已把 ${images} 张参考图作为 image_url 发送给智能体。`;
  }
  return textOf(event.payload.content, textOf(event.payload.message, "该事件未包含可展示文本。"));
}

function occurredAt(event: BackendEvent) {
  if (!event.occurredAt) return "历史记录";
  return new Intl.DateTimeFormat("zh-CN", { hour: "2-digit", minute: "2-digit", second: "2-digit" }).format(new Date(event.occurredAt));
}

export default function HistoryReplayPage() {
  const [sessionId, setSessionId] = useState(defaultSessionId);
  const [events, setEvents] = useState<BackendEvent[]>([]);
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState("");

  const replay = async () => {
    const targetSessionId = sessionId.trim();
    if (!targetSessionId || loading) return;
    setLoading(true);
    setError("");
    try {
      const response = await fetch(`${apiBaseUrl}/api/v1/agent/sessions/${encodeURIComponent(targetSessionId)}/events`, {
        headers: { Accept: "application/json" },
      });
      if (!response.ok) throw new Error(`历史记录请求失败: ${response.status}`);
      const records = (await response.json()) as BackendEvent[];
      setEvents(records.filter((event) => event.messageType !== "heartbeat"));
      setLoaded(true);
    } catch (requestError) {
      setEvents([]);
      setLoaded(true);
      setError(requestError instanceof Error ? requestError.message : "无法读取会话历史");
    } finally {
      setLoading(false);
    }
  };

  const runCount = new Set(events.map((event) => event.runId)).size;

  return <main className={styles.page}>
    <header className={styles.header}>
      <div>
        <p>AGENT EVENT ARCHIVE</p>
        <h1>Replay the <em>work</em>, not just the answer.</h1>
        <span>从 MySQL 的统一事件信封读取原始顺序，恢复 PlanningAgent、ExecutorAgent、ToolRouter 与 SummaryAgent 的任务过程。</span>
      </div>
      <div className={styles.counts}>
        <b>{events.length}</b><span>事件</span><b>{runCount}</b><span>运行</span>
      </div>
    </header>

    <section className={styles.controls}>
      <label>会话 ID</label>
      <Input value={sessionId} onChange={(event) => setSessionId(event.target.value)} onPressEnter={replay} placeholder="输入要回放的 sessionId" />
      <Button type="primary" icon={<HistoryOutlined />} loading={loading} onClick={replay}>回放历史</Button>
      {loaded && <Button icon={<ReloadOutlined />} disabled={loading} onClick={replay}>刷新</Button>}
    </section>

    {error && <div className={styles.error}>{error}</div>}
    {!loaded && !error && <section className={styles.empty}><ClockCircleOutlined /><h2>选择一个会话开始回放</h2><p>默认会话会保存 `/live-multimodal` 产生的事件。点击“回放历史”不会再次调用模型或工具。</p></section>}
    {loaded && !error && events.length === 0 && <section className={styles.empty}><HistoryOutlined /><h2>没有可回放的事件</h2><p>请先在 `/live-multimodal` 完成一次任务，或输入已有的会话 ID。</p></section>}
    {events.length > 0 && <section className={styles.timeline}>
      {events.map((event, index) => <article key={event.eventId} className={`${styles.event} ${styles[`event_${event.messageType}`] ?? ""}`}>
        <div className={styles.rail}><i>{index + 1}</i><span /></div>
        <div className={styles.card}>
          <div className={styles.meta}><Tag>{event.messageType}</Tag><small>{event.agent} / {event.status}</small><time>{occurredAt(event)}</time></div>
          <h2>{eventTitle(event)}</h2>
          <p>{eventContent(event)}</p>
          {event.messageType === "image" && typeof event.payload.imageUrl === "string" && <a href={event.payload.imageUrl} target="_blank">打开图像产物</a>}
        </div>
      </article>)}
    </section>}
  </main>;
}
