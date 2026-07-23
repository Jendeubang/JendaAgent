$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$utf8 = New-Object System.Text.UTF8Encoding($false)
$pagePath = Join-Path $root "showcase\app\agent-studio\page.tsx"
$cssPath = Join-Path $root "showcase\app\agent-studio\page.module.css"
$readmePath = Join-Path $root "README_JENDA_AGENT_STATUS_STEP33_AGENT_EXPERIENCE.md"

$page = @'
"use client";

import {
  AppstoreOutlined, ArrowRightOutlined, CloudUploadOutlined, FileImageOutlined,
  HistoryOutlined, LoadingOutlined, PlusOutlined, SendOutlined, SparklesOutlined,
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
        {sessions.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="\u5b8c\u6210\u7b2c\u4e00\u4e2a\u4efb\u52a1\u540e\u663e\u793a" /> : <div className={styles.sessionList}>{sessions.map((session) => <button key={session.sessionId} className={session.sessionId === viewSessionId ? styles.selected : ""} onClick={() => setViewSessionId(session.sessionId)}><b>{session.latestPrompt || "\u672a\u547d\u540d\u4efb\u52a1"}</b><span>{formatTime(session.updatedAt)} · {session.mode}</span></button>)}</div>}
        <div className={styles.sideFooter}><span className={running ? styles.live : ""} /> {running ? "Agent \u6b63\u5728\u5de5\u4f5c" : "3 agents online"}</div>
      </aside>
      <section className={styles.thread}>
        <div className={styles.runMeta}><span className={running || uploading || loadingView ? styles.runningDot : ""} /><b>{phase}</b><code>{viewSessionId.slice(-12) || "new session"}</code>{isReadOnly && <Tag color="gold">\u53ea\u8bfb\u56de\u653e</Tag>}</div>
        {loadingView ? <div className={styles.loading}><LoadingOutlined spin /> \u6b63\u5728\u6062\u590d\u4f1a\u8bdd...</div> : events.length === 0 ? <section className={styles.hero}><p>AUTONOMOUS CREATIVE AGENT</p><h1>\u628a\u4f60\u7684\u60f3\u6cd5<br /><em>\u4ea4\u7ed9\u591a\u667a\u80fd\u4f53</em></h1><span>\u4ece\u4e00\u53e5\u8bdd\u5230\u53ef\u4ea4\u4ed8\u7684\u56fe\u50cf\u6210\u679c\u3002\u667a\u80fd\u4f53\u4f1a\u81ea\u4e3b\u89c4\u5212\u3001\u8c03\u7528\u5de5\u5177\u5e76\u4fdd\u7559\u5168\u7a0b\u8ffd\u6eaf\u3002</span><div className={styles.capabilities}><b>PLAN</b><b>VISION</b><b>OCR</b><b>IMAGE EDIT</b></div><div className={styles.exampleRow}>{examples.map((example) => <button key={example} onClick={() => setPrompt(example)}>{example}<ArrowRightOutlined /></button>)}</div></section> : <div className={styles.events}>{events.map((event, index) => <article key={event.eventId} className={`${styles.event} ${styles[`tone_${eventTone(event.messageType)}`]}`}><div className={styles.eventRail}><span>{String(index + 1).padStart(2, "0")}</span><i /></div><div className={styles.eventBody}><header><Tag>{event.messageType.replace("_", " ")}</Tag><small>{event.agent} / {event.status}</small></header><h2>{eventTitle(event)}</h2><p>{eventContent(event)}</p></div></article>)}</div>}
        {error && <div className={styles.error}>{error}</div>}
        {!isReadOnly && <div className={styles.composerWrap}>{upload && <div className={styles.uploadPreview}><FileImageOutlined /><span>{fileName}</span><button onClick={() => { setUpload(undefined); setFileName(""); }}>×</button></div>}<label className={styles.uploadButton}><input type="file" accept="image/jpeg,image/png,image/webp,image/gif" onChange={uploadReference} disabled={uploading || running} />{uploading ? <LoadingOutlined spin /> : <CloudUploadOutlined />}{uploading ? "\u4e0a\u4f20\u4e2d" : upload ? "\u66ff\u6362\u53c2\u8003\u56fe" : "\u4e0a\u4f20\u53c2\u8003\u56fe"}</label><Sender value={prompt} onChange={setPrompt} onSubmit={run} loading={running} placeholder="\u8bf4\u51fa\u4f60\u60f3\u521b\u4f5c\u7684\u4efb\u52a1..." suffix={<Button type="primary" shape="circle" icon={<SendOutlined />} onClick={() => void run()} />} footer={<span>Enter \u53d1\u9001 · Shift + Enter \u6362\u884c · \u56fe\u7247\u5c06\u4f5c\u4e3a image_url \u4f20\u5165</span>} /></div>}
      </section>
      <aside className={styles.workspace}><div className={styles.workspaceHead}><span>WORKSPACE</span><Button type="text" icon={<AppstoreOutlined />} onClick={() => router.push("/assets")} /></div><h2>\u672c\u6b21\u4ea4\u4ed8</h2><p>\u53c2\u8003\u56fe\u4e0e\u751f\u6210\u7ed3\u679c\u4f1a\u7ed1\u5b9a\u5230\u5f53\u524d\u4f1a\u8bdd\u3002</p>{displayedAssets.length === 0 ? <div className={styles.emptyAsset}><SparklesOutlined /><b>\u7b49\u5f85\u4ea7\u7269</b><span>\u8c03\u7528\u56fe\u50cf\u5de5\u5177\u540e\uff0c\u7ed3\u679c\u4f1a\u51fa\u73b0\u5728\u8fd9\u91cc\u3002</span></div> : <div className={styles.assetList}>{displayedAssets.map((asset) => <article key={asset.assetId}><img src={asset.imageUrl} alt={asset.title} /><div><Tag color={asset.source === "generated" ? "magenta" : "cyan"}>{asset.source === "generated" ? "GENERATED" : "REFERENCE"}</Tag><b>{asset.title}</b><a href={asset.imageUrl} target="_blank" download>\u9884\u89c8 / \u4e0b\u8f7d <ArrowRightOutlined /></a></div></article>)}</div>}<div className={styles.workspaceFooter}><span>{displayedAssets.length}</span> assets in this session</div></aside>
    </section>
  </main>;
}
'@

$css = @'
.page{min-height:100vh;color:#f5f6ef;background:#111311;font-family:"Aptos","Noto Sans SC","Microsoft YaHei",sans-serif}.topbar{position:sticky;top:0;z-index:5;display:flex;align-items:center;height:70px;padding:0 26px;background:#121412e8;border-bottom:1px solid #2b2e2a;backdrop-filter:blur(16px)}.brand{display:flex;align-items:center;gap:9px;padding:0;color:#f7f9ee;cursor:pointer;background:transparent;border:0;text-align:left}.brand span{display:grid;width:31px;height:31px;place-items:center;color:#121412;background:#d4ff4f;border-radius:50%;font-family:Georgia,serif;font-size:20px;font-weight:700}.brand b{font-size:12px;line-height:.84;letter-spacing:.1em}.brand i{color:#9b9d96;font-size:9px;font-style:normal;font-weight:400;letter-spacing:.24em}.topbar nav{display:flex;gap:23px;margin-left:55px}.topbar nav button{padding:25px 0;color:#8b8f87;cursor:pointer;background:transparent;border:0;font-size:13px}.topbar nav button:hover,.topbar nav .active{color:#f7f9ee;box-shadow:inset 0 -2px #d4ff4f}.topActions{display:flex;align-items:center;gap:12px;margin-left:auto}.topActions :global(.ant-segmented){color:#d9ded3;background:#262923}.topActions :global(.ant-segmented-item-selected){color:#121412;background:#d4ff4f}.topActions :global(.ant-btn){color:#e8ece3;background:#252823;border-color:#3c4039}.layout{display:grid;grid-template-columns:250px minmax(500px,1fr) 310px;min-height:calc(100vh - 70px)}.sessions,.workspace{background:#171a17}.sessions{display:flex;flex-direction:column;padding:18px 14px;border-right:1px solid #2a2e29}.sideHead,.workspaceHead{display:flex;align-items:center;justify-content:space-between;color:#a9ada4;font-size:10px;font-weight:800;letter-spacing:.18em}.sideHead :global(.ant-btn),.workspaceHead :global(.ant-btn){color:#d4ff4f}.currentSession{display:flex;gap:10px;align-items:center;width:100%;padding:12px;margin-top:21px;color:#f2f4ed;text-align:left;cursor:pointer;background:#242822;border:1px solid #3b4237;border-radius:10px}.currentSession:hover{border-color:#d4ff4f}.currentSession>i{width:8px;height:8px;background:#d4ff4f;border-radius:50%;box-shadow:0 0 13px #d4ff4f}.currentSession small{display:block;margin-bottom:4px;color:#989e94;font-size:10px}.currentSession b{font-size:12px}.historyHead{margin:28px 8px 12px;color:#777d74;font-size:10px;font-weight:800;letter-spacing:.12em}.sessionList{display:flex;flex-direction:column;gap:5px;overflow:auto}.sessionList button{display:flex;flex-direction:column;gap:5px;width:100%;padding:10px;color:#aeb4aa;text-align:left;cursor:pointer;background:transparent;border:1px solid transparent;border-radius:8px}.sessionList button:hover{background:#20241f}.sessionList button.selected{color:#eff3e9;background:#2a3027;border-color:#495443}.sessionList b{overflow:hidden;font-size:12px;text-overflow:ellipsis;white-space:nowrap}.sessionList span{color:#747b72;font-size:10px}.sessions :global(.ant-empty-description){color:#70766e;font-size:11px}.sideFooter{padding:13px 5px 2px;margin-top:auto;color:#858b82;border-top:1px solid #2c302b;font-size:11px}.sideFooter span{display:inline-block;width:7px;height:7px;margin-right:6px;background:#80c77e;border-radius:50%}.sideFooter .live{background:#d4ff4f;box-shadow:0 0 10px #d4ff4f}.thread{position:relative;min-height:calc(100vh - 70px);padding:22px 28px 175px;background:radial-gradient(circle at 54% 0,#2a342466,transparent 31rem),#101210}.runMeta{display:flex;align-items:center;gap:8px;max-width:820px;padding-bottom:16px;margin:0 auto;color:#a2a99d;border-bottom:1px solid #282c27;font-size:11px}.runMeta>span{width:7px;height:7px;background:#75be72;border-radius:50%}.runMeta .runningDot{background:#d4ff4f;box-shadow:0 0 12px #d4ff4f;animation:blink 1s infinite}.runMeta code{margin-left:auto;color:#6f766d;font-family:Consolas,monospace;font-size:10px}.hero{max-width:700px;padding:115px 0 60px;margin:0 auto}.hero>p{margin:0 0 15px;color:#d4ff4f;font-size:10px;font-weight:800;letter-spacing:.21em}.hero h1{margin:0;color:#f4f6ef;font-family:Georgia,"Noto Serif SC",serif;font-size:54px;font-weight:400;letter-spacing:-.06em;line-height:1.05}.hero h1 em{color:#d4ff4f;font-style:normal}.hero>span{display:block;max-width:500px;margin-top:23px;color:#a4aba1;font-size:14px;line-height:1.8}.capabilities{display:flex;flex-wrap:wrap;gap:7px;margin-top:28px}.capabilities b{padding:6px 9px;color:#d0d6ca;border:1px solid #454b42;border-radius:999px;font-size:10px;letter-spacing:.1em}.exampleRow{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-top:42px}.exampleRow button{display:flex;align-items:flex-start;justify-content:space-between;min-height:82px;padding:13px;color:#d7dcd2;text-align:left;cursor:pointer;background:#1c201c;border:1px solid #343a33;border-radius:9px;font-size:12px;line-height:1.5}.exampleRow button:hover{color:#101210;background:#d4ff4f;border-color:#d4ff4f}.events{max-width:820px;padding:27px 0 25px;margin:auto}.event{display:grid;grid-template-columns:44px 1fr;padding:0 0 21px}.eventRail{display:flex;flex-direction:column;align-items:center;color:#7d837b;font-family:Consolas,monospace;font-size:10px}.eventRail i{width:1px;flex:1;margin-top:8px;background:#343932}.event:last-child .eventRail i{display:none}.eventBody{padding:13px 15px;margin-top:-5px;background:#191d19;border:1px solid #31372f;border-radius:10px}.eventBody header{display:flex;gap:8px;align-items:center}.eventBody header small{color:#878e85;font-size:10px}.eventBody h2{margin:10px 0 6px;color:#f0f3eb;font-size:15px}.eventBody p{margin:0;color:#abb1a8;font-size:12px;line-height:1.7;white-space:pre-wrap}.tone_plan .eventBody{border-left:3px solid #d4ff4f}.tone_tool .eventBody{border-left:3px solid #5a9fff}.tone_image .eventBody{border-left:3px solid #fa8d6e}.tone_summary .eventBody{border-left:3px solid #d9a6ff}.loading{max-width:820px;padding:90px 0;margin:auto;color:#a6ada2;text-align:center}.error{max-width:820px;padding:12px;margin:0 auto;color:#ffc4ba;background:#3b1e1b;border:1px solid #75413a;border-radius:8px;font-size:12px}.composerWrap{position:absolute;right:28px;bottom:22px;left:28px;max-width:820px;margin:auto}.composerWrap :global(.ant-sender){color:#eef2e9;background:#1e221e;border:1px solid #3d453a;border-radius:12px;box-shadow:0 17px 45px #0008}.composerWrap :global(.ant-sender textarea){color:#eff3eb}.composerWrap :global(.ant-sender textarea::placeholder){color:#798177}.composerWrap :global(.ant-btn-primary){color:#111311;background:#d4ff4f}.uploadButton,.uploadPreview{display:inline-flex;align-items:center;gap:7px;padding:7px 10px;margin:0 0 8px;color:#d4ff4f;background:#20261e;border:1px dashed #667a51;border-radius:7px;font-size:11px}.uploadButton{cursor:pointer}.uploadButton input{display:none}.uploadPreview{color:#c9d0c3;border-style:solid}.uploadPreview button{color:#d8ded1;cursor:pointer;background:none;border:0;font-size:17px}.workspace{padding:20px 16px;border-left:1px solid #2a2e29}.workspace h2{margin:16px 0 7px;color:#f2f4ec;font-family:Georgia,"Noto Serif SC",serif;font-size:25px;font-weight:400}.workspace>p{margin:0;color:#858c83;font-size:11px;line-height:1.6}.emptyAsset{display:flex;flex-direction:column;gap:8px;align-items:center;padding:48px 20px;margin-top:30px;color:#939a91;text-align:center;background:#1d211d;border:1px dashed #3b4339;border-radius:10px;font-size:11px}.emptyAsset .anticon{color:#d4ff4f;font-size:25px}.emptyAsset b{color:#dfe4dc}.assetList{display:flex;flex-direction:column;gap:12px;margin-top:20px}.assetList article{overflow:hidden;background:#1d211d;border:1px solid #343b32;border-radius:9px}.assetList img{display:block;width:100%;height:150px;object-fit:cover;background:#252a24}.assetList article>div{display:flex;flex-direction:column;gap:7px;padding:10px}.assetList b{overflow:hidden;color:#e7ebe3;font-size:12px;text-overflow:ellipsis;white-space:nowrap}.assetList a{color:#d4ff4f;font-size:11px}.workspaceFooter{padding-top:15px;margin-top:18px;color:#7e867c;border-top:1px solid #2c312c;font-size:11px}.workspaceFooter span{margin-right:5px;color:#d4ff4f;font-size:17px;font-weight:700}@keyframes blink{50%{opacity:.25}}@media(max-width:1120px){.layout{grid-template-columns:220px 1fr}.workspace{display:none}}@media(max-width:720px){.topbar{height:auto;min-height:65px;padding:12px 15px}.topbar nav{display:none}.topActions :global(.ant-segmented){display:none}.layout{display:block}.sessions{display:none}.thread{min-height:calc(100vh - 65px);padding:18px 14px 170px}.hero{padding:80px 6px 45px}.hero h1{font-size:41px}.exampleRow{grid-template-columns:1fr}.exampleRow button{min-height:56px}.composerWrap{right:14px;bottom:14px;left:14px}.runMeta code{display:none}}
'@

$readme = @'
# Step 33: Crispix-Inspired Agent Studio

## Product Goal

`/agent-studio` is the deployed product workspace. It is inspired by the
creative-agent information architecture of Crispix Agent, but does not copy
its assets, brand, or source. The implementation retains JendaAgent's real
JWT-protected APIs and replaces the old administration-style view with a
creative production workspace.

## Layout

```text
Top navigation: Agent / Image Studio / Assets / mode / account
Left rail: current session and persistent session history
Center canvas: prompt launchpad and streamed Plan-Solve or ReAct trace
Right rail: reference images and generated COS assets for the session
```

## Working Interactions

- Example prompts populate the real task input.
- Upload uses the existing STS-first COS flow and is delivered as `image_url`.
- Sending a task calls the existing SSE run endpoint.
- Streamed `plan`, `task`, `tool_call`, `tool_result`, `image`, and `summary`
  events render as process cards.
- Session selection uses the existing persisted history endpoints in read-only
  mode; the plus button creates a separate session id.
- Workspace cards use signed COS URLs and open/download the actual asset.

## Validation

```powershell
cd F:\JendaAgent\JendaAgent\showcase
corepack pnpm exec tsc --noEmit --skipLibCheck
corepack pnpm build
```

Rebuild the Docker frontend after source changes:

```powershell
cd F:\JendaAgent\JendaAgent
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml up --build -d
```
'@

[System.IO.File]::WriteAllText($pagePath, $page + [Environment]::NewLine, $utf8)
[System.IO.File]::WriteAllText($cssPath, $css + [Environment]::NewLine, $utf8)
[System.IO.File]::WriteAllText($readmePath, $readme + [Environment]::NewLine, $utf8)
Write-Host "Applied Step 33 Crispix-inspired Agent Studio UI."
