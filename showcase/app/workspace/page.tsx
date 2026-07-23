"use client";

import { AppstoreOutlined, FolderOpenOutlined, ReloadOutlined } from "@ant-design/icons";
import { Button, Empty, Tag } from "antd";
import { useEffect, useState } from "react";
import styles from "./page.module.css";

type Session = {
  sessionId: string;
  latestRunId: string;
  mode: string;
  latestPrompt: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  runCount: number;
  eventCount: number;
};

type Asset = { assetId: string; runId: string; title: string; imageUrl: string; source: "reference" | "generated"; occurredAt: string };
type WorkspaceSnapshot = { session: Session; assets: Asset[] };
type AgentEvent = { eventId: string; messageType: string; status: string; agent: string; occurredAt: string; payload: Record<string, unknown> };

const apiBaseUrl = process.env.NEXT_PUBLIC_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";

function eventText(value: unknown, fallback = "") { return typeof value === "string" ? value : fallback; }
function time(value?: string) { return value ? new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value)) : ""; }
function title(event: AgentEvent) { return eventText(event.payload.title, event.messageType); }
function content(event: AgentEvent) { return eventText(event.payload.content, eventText(event.payload.message, "无文本内容")); }

export default function WorkspacePage() {
  const [sessions, setSessions] = useState<Session[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [events, setEvents] = useState<AgentEvent[]>([]);
  const [snapshot, setSnapshot] = useState<WorkspaceSnapshot>();
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    fetch(`${apiBaseUrl}/api/v1/agent/sessions?limit=40`, { headers: { Accept: "application/json" } })
      .then(async (response) => {
        if (!response.ok) throw new Error(`会话列表请求失败: ${response.status}`);
        return response.json() as Promise<Session[]>;
      })
      .then((items) => {
        if (cancelled) return;
        setSessions(items);
        setSelectedId((current) => items.some((item) => item.sessionId === current) ? current : (items[0]?.sessionId ?? ""));
        setError("");
      })
      .catch((requestError) => !cancelled && setError(requestError instanceof Error ? requestError.message : "无法读取会话列表"))
      .finally(() => !cancelled && setLoading(false));
    return () => { cancelled = true; };
  }, [refreshKey]);

  useEffect(() => {
    if (!selectedId) { setEvents([]); setSnapshot(undefined); return; }
    let cancelled = false;
    setDetailLoading(true);
    Promise.all([
      fetch(`${apiBaseUrl}/api/v1/agent/sessions/${encodeURIComponent(selectedId)}/events`).then(async (response) => {
        if (!response.ok) throw new Error(`任务时间线请求失败: ${response.status}`);
        return response.json() as Promise<AgentEvent[]>;
      }),
      fetch(`${apiBaseUrl}/api/v1/agent/sessions/${encodeURIComponent(selectedId)}/workspace`).then(async (response) => {
        if (!response.ok) throw new Error(`Workspace 请求失败: ${response.status}`);
        return response.json() as Promise<WorkspaceSnapshot>;
      }),
    ]).then(([timeline, workspace]) => {
      if (cancelled) return;
      setEvents(timeline.filter((event) => event.messageType !== "heartbeat" && event.messageType !== "run_completed"));
      setSnapshot(workspace);
      setError("");
    }).catch((requestError) => !cancelled && setError(requestError instanceof Error ? requestError.message : "无法读取会话详情"))
      .finally(() => !cancelled && setDetailLoading(false));
    return () => { cancelled = true; };
  }, [selectedId, refreshKey]);

  return <main className={styles.page}>
    <header className={styles.header}>
      <div><p>JENDA AGENT / WORKSPACE</p><h1>Every run leaves a <em>trace.</em></h1><span>浏览会话、复盘智能体过程、查看输入参考图与工具产物。</span></div>
      <Button icon={<ReloadOutlined />} loading={loading} onClick={() => setRefreshKey((value) => value + 1)}>刷新工作空间</Button>
    </header>
    {error && <div className={styles.error}>{error}</div>}
    <section className={styles.layout}>
      <aside className={styles.sessions}><div className={styles.panelTitle}><AppstoreOutlined /><span>会话</span><b>{sessions.length}</b></div>
        {loading ? <p className={styles.muted}>正在读取会话...</p> : sessions.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无已持久化会话" /> : <div className={styles.sessionList}>{sessions.map((session) => <button key={session.sessionId} className={session.sessionId === selectedId ? styles.selected : ""} onClick={() => setSelectedId(session.sessionId)}><strong>{session.latestPrompt || "未命名任务"}</strong><span>{time(session.updatedAt)}</span><small>{session.mode} · {session.eventCount} events</small></button>)}</div>}
      </aside>
      <section className={styles.timeline}><div className={styles.panelTitle}><FolderOpenOutlined /><span>任务过程</span>{snapshot && <Tag color="green">{snapshot.session.status}</Tag>}</div>
        {detailLoading ? <p className={styles.muted}>正在恢复任务过程...</p> : events.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="选择会话以查看过程" /> : events.map((event) => <article key={event.eventId} className={`${styles.event} ${styles[`event_${event.messageType}`] ?? ""}`}><div><Tag>{event.messageType}</Tag><small>{event.agent} / {event.status}</small><time>{time(event.occurredAt)}</time></div><h2>{title(event)}</h2><p>{content(event)}</p></article>)}
      </section>
      <aside className={styles.assets}><div className={styles.panelTitle}><FolderOpenOutlined /><span>Workspace 产物</span><b>{snapshot?.assets.length ?? 0}</b></div>
        {detailLoading ? <p className={styles.muted}>正在读取资产...</p> : !snapshot?.assets.length ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该会话暂无图片资产" /> : <div className={styles.assetList}>{snapshot.assets.map((asset) => <article key={asset.assetId}><img src={asset.imageUrl} alt={asset.title} /><div><Tag color={asset.source === "generated" ? "magenta" : "cyan"}>{asset.source === "generated" ? "工具产物" : "参考图"}</Tag><strong>{asset.title}</strong><small>{time(asset.occurredAt)}</small><a href={asset.imageUrl} target="_blank" download>预览 / 下载</a></div></article>)}</div>}
      </aside>
    </section>
  </main>;
}
