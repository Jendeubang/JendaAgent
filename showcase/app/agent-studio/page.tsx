"use client";

import {
  AppstoreOutlined, ArrowRightOutlined, CloudUploadOutlined, FileImageOutlined,
  HistoryOutlined, LoadingOutlined, PlusOutlined, SendOutlined, ThunderboltOutlined,
} from "@ant-design/icons";
import { Button, Empty, Segmented, Tag } from "antd";
import { Sender } from "@ant-design/x";
import { ChangeEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { uploadImageDirect, type DirectUploadedAsset } from "../../lib/cosDirectUpload";
import { agentFetch } from "../../lib/agentAuth";
import { AgentAccountMenu } from "../../components/AgentAccountMenu";
import styles from "./page.module.css";

type Mode = "plan-solve" | "react";
type AgentEvent = { eventId: string; messageType: string; status: string; agent: string; occurredAt?: string; payload: Record<string, unknown> };
type Session = { sessionId: string; latestRunId: string; mode: string; latestPrompt: string; status: string; updatedAt: string; eventCount: number };
type WorkspaceAsset = { assetId: string; runId: string; title: string; imageUrl: string; source: "reference" | "generated"; occurredAt?: string };
type WorkspaceSnapshot = { assets: WorkspaceAsset[] };

const apiBaseUrl = process.env.NEXT_PUBLIC_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const currentSessionStorageKey = "jenda-agent-current-session-id";
const examples = ["\u751f\u6210\u4e00\u5f20\u590f\u65e5\u996e\u54c1\u54c1\u724c\u6d77\u62a5", "\u628a\u53c2\u8003\u56fe\u66ff\u6362\u4e3a\u8d5b\u535a\u670b\u514b\u96e8\u591c\u80cc\u666f", "\u8bc6\u522b\u56fe\u7247\u4e2d\u7684\u5168\u90e8\u6587\u5b57\u5e76\u4fdd\u7559\u6362\u884c"];

function text(value: unknown, fallback = "") { return typeof value === "string" ? value : fallback; }
function eventTitle(event: AgentEvent) {
  const labels: Record<string, string> = {
    run_started: "\u591a\u6a21\u6001\u4e0a\u4e0b\u6587\u5df2\u9644\u52a0", plan: "\u4efb\u52a1\u89c4\u5212", task: "\u5b50\u4efb\u52a1\u6267\u884c",
    tool_call: "\u5de5\u5177\u8c03\u7528", tool_result: "\u5de5\u5177\u7ed3\u679c", image: "\u56fe\u50cf\u4ea7\u7269", summary: "\u4ea4\u4ed8\u6c47\u603b",
  };
  return text(event.payload.title, labels[event.messageType] ?? event.messageType);
}
function eventContent(event: AgentEvent) {
  if (event.messageType === "run_started") return `\u5df2\u5c06 ${Array.isArray(event.payload.imageUrls) ? event.payload.imageUrls.length : 0} \u5f20\u53c2\u8003\u56fe\u4ee5 image_url \u4f20\u5165\u667a\u80fd\u4f53\u4e0a\u4e0b\u6587\u3002`;
  return text(event.payload.content, text(event.payload.message, "\u7b49\u5f85\u667a\u80fd\u4f53\u8f93\u51fa\u3002"));
}
function formatTime(value?: string) { return value ? new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value)) : ""; }
function newSessionId() { return `session-${crypto.randomUUID()}`; }
function eventTone(type: string) { return ({ plan: "plan", task: "task", tool_call: "tool", tool_result: "tool", image: "image", summary: "summary" } as Record<string, string>)[type] ?? "default"; }

export default function AgentStudioPage() {
  const router = useRouter();
  const [currentSessionId, setCurrentSessionId] = useState("");
  const [viewSessionId, setViewSessionId] = useState("");
  const [sessions, setSessions] = useState<Session[]>([]);
  const [events, setEvents] = useState<AgentEvent[]>([]);
  const [assets, setAssets] = useState<WorkspaceAsset[]>([]);
  const [mode, setMode] = useState<Mode>("plan-solve");
  const [prompt, setPrompt] = useState("");
  const [upload, setUpload] = useState<DirectUploadedAsset>();
  const [fileName, setFileName] = useState("");
  const [running, setRunning] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [loadingView, setLoadingView] = useState(false);
  const [phase, setPhase] = useState("\u51c6\u5907\u65b0\u4efb\u52a1");
  const [error, setError] = useState("");
  const isReadOnly = Boolean(viewSessionId && currentSessionId && viewSessionId !== currentSessionId);

  const refreshSessions = async () => {
    const response = await agentFetch(`${apiBaseUrl}/api/v1/agent/sessions?limit=40`, { headers: { Accept: "application/json" } });
    if (!response.ok) throw new Error(`\u4f1a\u8bdd\u5217\u8868\u8bf7\u6c42\u5931\u8d25: ${response.status}`);
    setSessions(await response.json() as Session[]);
  };
  const loadSession = async (sessionId: string) => {
    setLoadingView(true); setError("");
    try {
      const [eventResponse, workspaceResponse] = await Promise.all([
        agentFetch(`${apiBaseUrl}/api/v1/agent/sessions/${encodeURIComponent(sessionId)}/events`),
        agentFetch(`${apiBaseUrl}/api/v1/agent/sessions/${encodeURIComponent(sessionId)}/workspace`),
      ]);
      if (!eventResponse.ok) throw new Error(`\u4f1a\u8bdd\u56de\u653e\u8bf7\u6c42\u5931\u8d25: ${eventResponse.status}`);
      const history = await eventResponse.json() as AgentEvent[];
      setEvents(history.filter((event) => event.messageType !== "heartbeat" && event.messageType !== "run_completed"));
      setAssets(workspaceResponse.ok ? (await workspaceResponse.json() as WorkspaceSnapshot).assets : []);
      setPhase(sessionId === currentSessionId ? "\u5f53\u524d\u4f1a\u8bdd\u5df2\u6062\u590d" : "\u6b63\u5728\u53ea\u8bfb\u56de\u653e\u5386\u53f2\u4f1a\u8bdd");
    } catch (loadError) { setError(loadError instanceof Error ? loadError.message : "\u65e0\u6cd5\u52a0\u8f7d\u4f1a\u8bdd"); }
    finally { setLoadingView(false); }
  };
  useEffect(() => {
    const savedSessionId = window.localStorage.getItem(currentSessionStorageKey);
    const sessionId = savedSessionId || newSessionId();
    if (!savedSessionId) window.localStorage.setItem(currentSessionStorageKey, sessionId);
    setCurrentSessionId(sessionId); setViewSessionId(sessionId); setPhase("\u65b0\u4f1a\u8bdd\u5df2\u5c31\u7eea");
    refreshSessions().catch((loadError) => setError(loadError instanceof Error ? loadError.message : "\u65e0\u6cd5\u8bfb\u53d6\u4f1a\u8bdd\u5217\u8868"));
  }, []);
  useEffect(() => {
    if (!viewSessionId || (viewSessionId === currentSessionId && !sessions.some((session) => session.sessionId === viewSessionId))) return;
    void loadSession(viewSessionId);
  }, [viewSessionId, currentSessionId, sessions.length]);
  const createSession = () => {
    const sessionId = newSessionId();
    window.localStorage.setItem(currentSessionStorageKey, sessionId);
    setCurrentSessionId(sessionId); setViewSessionId(sessionId); setEvents([]); setAssets([]); setPrompt(""); setUpload(undefined); setFileName(""); setError(""); setPhase("\u65b0\u4f1a\u8bdd\u5df2\u521b\u5efa");
  };
  const uploadReference = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]; event.target.value = "";
    if (!file || uploading || running || isReadOnly || !currentSessionId) return;
    setUploading(true); setError(""); setPhase("\u6b63\u5728\u4e0a\u4f20\u53c2\u8003\u56fe");
    try { const asset = await uploadImageDirect(apiBaseUrl, currentSessionId, file); setUpload(asset); setFileName(file.name); setPhase("\u53c2\u8003\u56fe\u5df2\u52a0\u5165\u591a\u6a21\u6001\u4e0a\u4e0b\u6587"); }
    catch (uploadError) { setError(uploadError instanceof Error ? uploadError.message : "\u56fe\u7247\u4e0a\u4f20\u5931\u8d25"); setPhase("\u4e0a\u4f20\u5931\u8d25"); }
    finally { setUploading(false); }
  };
  const consumeStream = async (response: Response) => {
    if (!response.body) throw new Error("\u6d4f\u89c8\u5668\u4e0d\u652f\u6301\u6d41\u5f0f\u54cd\u5e94");
    const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = "";
    while (true) {
      const { done, value } = await reader.read(); buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done });
      const frames = buffer.split("\n\n"); buffer = frames.pop() ?? "";
      for (const frame of frames) {
        const name = frame.split("\n").find((line) => line.startsWith("event:"))?.slice(6).trim();
        const data = frame.split("\n").filter((line) => line.startsWith("data:")).map((line) => line.slice(5).trim()).join("\n");
        if (name !== "agent-event" || !data) continue;
        const agentEvent = JSON.parse(data) as AgentEvent;
        if (agentEvent.messageType === "heartbeat") continue;
        setEvents((current) => [...current, agentEvent]);
        if (agentEvent.messageType === "run_completed") { setRunning(false); setPhase("\u4efb\u52a1\u5b8c\u6210\uff0c\u5df2\u5f52\u6863"); }
        else if (agentEvent.messageType === "error") { setRunning(false); setError(text(agentEvent.payload.message, "\u667a\u80fd\u4f53\u6267\u884c\u5931\u8d25")); setPhase("\u4efb\u52a1\u5931\u8d25"); }
        else setPhase(`${agentEvent.agent}: ${eventTitle(agentEvent)}`);
      }
      if (done) break;
    }
  };
  const run = async () => {
    const task = prompt.trim();
    if (!task || running || uploading || isReadOnly || !currentSessionId) return;
    setError(""); setEvents([]); setAssets([]); setRunning(true); setPhase("\u6b63\u5728\u8fde\u63a5 SSE");
    try {
      const response = await agentFetch(`${apiBaseUrl}/api/v1/agent/sessions/${currentSessionId}/runs`, { method: "POST", headers: { "Content-Type": "application/json", Accept: "text/event-stream" }, body: JSON.stringify({ prompt: task, mode, imageUrls: upload ? [upload.imageUrl] : [] }) });
      if (!response.ok) throw new Error(`\u667a\u80fd\u4f53\u8bf7\u6c42\u5931\u8d25: ${response.status}`);
      await consumeStream(response); await refreshSessions(); await loadSession(currentSessionId);
    } catch (runError) { setRunning(false); setError(runError instanceof Error ? runError.message : "\u65e0\u6cd5\u8fde\u63a5 SSE \u670d\u52a1"); setPhase("\u4efb\u52a1\u5931\u8d25"); }
  };
  const displayedAssets = upload && !isReadOnly && !assets.some((asset) => asset.imageUrl === upload.imageUrl)
    ? [{ assetId: upload.assetId, runId: "pending", title: fileName || "\u5f53\u524d\u53c2\u8003\u56fe", imageUrl: upload.imageUrl, source: "reference" as const }, ...assets] : assets;

  return <main className={styles.page}>
    <header className={styles.topbar}>
      <button className={styles.brand} onClick={createSession}><span>J</span><b>JENDA<br /><i>AGENT</i></b></button>
      <nav><button className={styles.active}>Agent</button><button onClick={() => router.push("/image-studio")}>Image Studio</button><button onClick={() => router.push("/assets")}>Assets</button></nav>
      <div className={styles.topActions}><Segmented value={mode} disabled={isReadOnly || running} onChange={(value) => setMode(value as Mode)} options={[{ label: "Plan", value: "plan-solve" }, { label: "ReAct", value: "react" }]} /><AgentAccountMenu /></div>
    </header>
    <section className={styles.layout}>
      <aside className={styles.sessions}>
        <div className={styles.sideHead}><span>SESSIONS</span><Button type="text" icon={<PlusOutlined />} onClick={createSession} disabled={running || uploading}>\u65b0\u5efa</Button></div>
        <button className={styles.currentSession} onClick={() => setViewSessionId(currentSessionId)}><i /><div><small>\u5f53\u524d\u5de5\u4f5c\u533a</small><b>{isReadOnly ? "\u6b63\u5728\u67e5\u770b\u5386\u53f2\u4f1a\u8bdd" : "\u65b0\u7684\u521b\u4f5c\u4efb\u52a1"}</b></div></button>
        <div className={styles.historyHead}><HistoryOutlined /> \u6700\u8fd1\u4efb\u52a1</div>
        {sessions.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="\u5b8c\u6210\u7b2c\u4e00\u4e2a\u4efb\u52a1\u540e\u663e\u793a" /> : <div className={styles.sessionList}>{sessions.map((session) => <button key={session.sessionId} className={session.sessionId === viewSessionId ? styles.selected : ""} onClick={() => setViewSessionId(session.sessionId)}><b>{session.latestPrompt || "\u672a\u547d\u540d\u4efb\u52a1"}</b><span>{formatTime(session.updatedAt)} 路 {session.mode}</span></button>)}</div>}
        <div className={styles.sideFooter}><span className={running ? styles.live : ""} /> {running ? "Agent \u6b63\u5728\u5de5\u4f5c" : "3 agents online"}</div>
      </aside>
      <section className={styles.thread}>
        <div className={styles.runMeta}><span className={running || uploading || loadingView ? styles.runningDot : ""} /><b>{phase}</b><code>{viewSessionId.slice(-12) || "new session"}</code>{isReadOnly && <Tag color="gold">\u53ea\u8bfb\u56de\u653e</Tag>}</div>
        {loadingView ? <div className={styles.loading}><LoadingOutlined spin /> \u6b63\u5728\u6062\u590d\u4f1a\u8bdd...</div> : events.length === 0 ? <section className={styles.hero}><p>AUTONOMOUS CREATIVE AGENT</p><h1>\u628a\u4f60\u7684\u60f3\u6cd5<br /><em>\u4ea4\u7ed9\u591a\u667a\u80fd\u4f53</em></h1><span>\u4ece\u4e00\u53e5\u8bdd\u5230\u53ef\u4ea4\u4ed8\u7684\u56fe\u50cf\u6210\u679c\u3002\u667a\u80fd\u4f53\u4f1a\u81ea\u4e3b\u89c4\u5212\u3001\u8c03\u7528\u5de5\u5177\u5e76\u4fdd\u7559\u5168\u7a0b\u8ffd\u6eaf\u3002</span><div className={styles.capabilities}><b>PLAN</b><b>VISION</b><b>OCR</b><b>IMAGE EDIT</b></div><div className={styles.exampleRow}>{examples.map((example) => <button key={example} onClick={() => setPrompt(example)}>{example}<ArrowRightOutlined /></button>)}</div></section> : <div className={styles.events}>{events.map((event, index) => <article key={event.eventId} className={`${styles.event} ${styles[`tone_${eventTone(event.messageType)}`]}`}><div className={styles.eventRail}><span>{String(index + 1).padStart(2, "0")}</span><i /></div><div className={styles.eventBody}><header><Tag>{event.messageType.replace("_", " ")}</Tag><small>{event.agent} / {event.status}</small></header><h2>{eventTitle(event)}</h2><p>{eventContent(event)}</p></div></article>)}</div>}
        {error && <div className={styles.error}>{error}</div>}
        {!isReadOnly && <div className={styles.composerWrap}>{upload && <div className={styles.uploadPreview}><FileImageOutlined /><span>{fileName}</span><button onClick={() => { setUpload(undefined); setFileName(""); }}>脳</button></div>}<label className={styles.uploadButton}><input type="file" accept="image/jpeg,image/png,image/webp,image/gif" onChange={uploadReference} disabled={uploading || running} />{uploading ? <LoadingOutlined spin /> : <CloudUploadOutlined />}{uploading ? "\u4e0a\u4f20\u4e2d" : upload ? "\u66ff\u6362\u53c2\u8003\u56fe" : "\u4e0a\u4f20\u53c2\u8003\u56fe"}</label><Sender value={prompt} onChange={setPrompt} onSubmit={run} loading={running} placeholder="\u8bf4\u51fa\u4f60\u60f3\u521b\u4f5c\u7684\u4efb\u52a1..." suffix={<Button type="primary" shape="circle" icon={<SendOutlined />} onClick={() => void run()} />} footer={<span>Enter \u53d1\u9001 路 Shift + Enter \u6362\u884c 路 \u56fe\u7247\u5c06\u4f5c\u4e3a image_url \u4f20\u5165</span>} /></div>}
      </section>
      <aside className={styles.workspace}><div className={styles.workspaceHead}><span>WORKSPACE</span><Button type="text" icon={<AppstoreOutlined />} onClick={() => router.push("/assets")} /></div><h2>\u672c\u6b21\u4ea4\u4ed8</h2><p>\u53c2\u8003\u56fe\u4e0e\u751f\u6210\u7ed3\u679c\u4f1a\u7ed1\u5b9a\u5230\u5f53\u524d\u4f1a\u8bdd\u3002</p>{displayedAssets.length === 0 ? <div className={styles.emptyAsset}><ThunderboltOutlined /><b>\u7b49\u5f85\u4ea7\u7269</b><span>\u8c03\u7528\u56fe\u50cf\u5de5\u5177\u540e\uff0c\u7ed3\u679c\u4f1a\u51fa\u73b0\u5728\u8fd9\u91cc\u3002</span></div> : <div className={styles.assetList}>{displayedAssets.map((asset) => <article key={asset.assetId}><img src={asset.imageUrl} alt={asset.title} /><div><Tag color={asset.source === "generated" ? "magenta" : "cyan"}>{asset.source === "generated" ? "GENERATED" : "REFERENCE"}</Tag><b>{asset.title}</b><a href={asset.imageUrl} target="_blank" download>\u9884\u89c8 / \u4e0b\u8f7d <ArrowRightOutlined /></a></div></article>)}</div>}<div className={styles.workspaceFooter}><span>{displayedAssets.length}</span> assets in this session</div></aside>
    </section>
  </main>;
}
