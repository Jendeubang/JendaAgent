$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$showcaseRoot = Join-Path $repoRoot "showcase"

function Write-Source([string]$relativePath, [string]$content) {
    $path = Join-Path $showcaseRoot $relativePath
    $directory = Split-Path -Parent $path
    [System.IO.Directory]::CreateDirectory($directory) | Out-Null
    [System.IO.File]::WriteAllText($path, $content, [System.Text.UTF8Encoding]::new($false))
}

$pageSource = @'
"use client";

import { AppstoreOutlined, CameraOutlined, EditOutlined, FileImageOutlined, LinkOutlined, LoadingOutlined, PictureOutlined, SearchOutlined, SendOutlined } from "@ant-design/icons";
import { Button, Tag } from "antd";
import { Bubble, Sender, XProvider } from "@ant-design/x";
import { ChangeEvent, useEffect, useState } from "react";
import { agentFetch } from "../../../lib/agentAuth";
import { uploadImageDirect, type DirectUploadedAsset } from "../../../lib/cosDirectUpload";
import { CrispixHeader } from "../../../components/CrispixHeader";
import styles from "./page.module.css";

type Mode = "plan-solve" | "react";
type AgentEvent = { eventId: string; messageType: string; status: string; agent: string; payload: Record<string, unknown> };
type WorkspaceAsset = { assetId: string; title: string; imageUrl: string; source: "reference" | "generated" };
type WorkspaceSnapshot = { assets: WorkspaceAsset[] };

const apiBaseUrl = process.env.NEXT_PUBLIC_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const sessionStorageKey = "jenda-agent-crispix-session";
const copy = {
  badge: "Jenda Agent \u00b7 \u5bf9\u8bdd\u5f0f\u56fe\u50cf\u521b\u4f5c\u52a9\u624b",
  lineOne: "\u4e0d\u5fc5\u4e00\u6b21\u8bf4\u5168\uff0c",
  lineTwo: "\u4e00\u4e2a\u611f\u53d7\u5c31\u8db3\u591f\u5f00\u573a",
  subtitle: "\u4ece\u4e00\u4efd\u611f\u53d7\u51fa\u53d1\uff0c\u56fe\u50cf\u7531\u6211\u6765\u6253\u9020 \u2014 \u63cf\u8ff0\u4f60\u7684\u753b\u9762\uff0c\u8ba9\u7075\u611f\u5373\u523b\u6210\u5f62\u3002",
  placeholder: "\u63cf\u8ff0\u60a8\u60f3\u8981\u7684\u56fe\u50cf\u6216\u63d0\u51fa\u95ee\u9898... \u4f8b\u5982\uff1a\u65e5\u843d\u65f6\u5206\u7684\u4eac\u90fd\u7af9\u6797\uff0c\u80f6\u7247\u8d28\u611f",
  planOn: "\u5f00\u542f",
  planOff: "\u5173\u95ed",
  planMode: "\u89c4\u5212\u6a21\u5f0f",
  upload: "\u6dfb\u52a0\u53c2\u8003\u56fe",
  uploaded: "\u5df2\u6dfb\u52a0\u53c2\u8003\u56fe",
  uploadBusy: "\u4e0a\u4f20\u4e2d",
  eventTitle: "\u667a\u80fd\u4f53\u5de5\u4f5c\u6d41",
  eventHint: "\u4f60\u53ef\u4ee5\u7ee7\u7eed\u8865\u5145\u8981\u6c42\u6216\u4e0a\u4f20\u53c2\u8003\u56fe\u3002",
  waiting: "\u6b63\u5728\u601d\u8003\u4e2d...",
  error: "\u667a\u80fd\u4f53\u8bf7\u6c42\u5931\u8d25",
  viewAsset: "\u67e5\u770b\u56fe\u7247\u4ea7\u7269",
};

const suggestions = [
  { icon: EditOutlined, text: "\u65e5\u843d\u65f6\u5206\u7684\u4eac\u90fd\u7af9\u6797\uff0c\u80f6\u7247\u8d28\u611f\uff0c\u6d45\u666f\u6df1", tag: "\u98ce\u666f\u63d2\u753b" },
  { icon: CameraOutlined, text: "\u96e8\u591c\u9713\u8679\u8857\u5934\uff0c\u8d5b\u535a\u670b\u514b\u6c1b\u56f4\uff0c\u7535\u5f71\u7ea7\u51b7\u6696\u5149", tag: "\u6c1b\u56f4\u573a\u666f" },
  { icon: PictureOutlined, text: "\u8bbe\u8ba1\u4e00\u5f20\u5c0f\u7ea2\u4e66\u7b14\u8bb0\u5c01\u9762\uff1a\u6e05\u723d\u6392\u7248\uff0c\u4e3b\u89c6\u89c9\u7a81\u51fa\uff0c\u6696\u8c03\u7559\u767d", tag: "\u5c01\u9762\u8bbe\u8ba1" },
];

function getEventContent(event: AgentEvent) {
  const title = typeof event.payload.title === "string" ? event.payload.title : event.messageType;
  const content = typeof event.payload.content === "string" ? event.payload.content : typeof event.payload.message === "string" ? event.payload.message : "";
  return `${title}${content ? `\n${content}` : ""}`;
}

function newSessionId() { return `session-${crypto.randomUUID()}`; }

export default function JendaAgentPage() {
  const [sessionId, setSessionId] = useState("");
  const [mode, setMode] = useState<Mode>("react");
  const [prompt, setPrompt] = useState("");
  const [events, setEvents] = useState<AgentEvent[]>([]);
  const [assets, setAssets] = useState<WorkspaceAsset[]>([]);
  const [upload, setUpload] = useState<DirectUploadedAsset>();
  const [fileName, setFileName] = useState("");
  const [running, setRunning] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    const stored = window.localStorage.getItem(sessionStorageKey);
    const next = stored || newSessionId();
    if (!stored) window.localStorage.setItem(sessionStorageKey, next);
    setSessionId(next);
  }, []);

  const uploadReference = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file || !sessionId || uploading || running) return;
    setUploading(true); setError("");
    try {
      const asset = await uploadImageDirect(apiBaseUrl, sessionId, file);
      setUpload(asset); setFileName(file.name);
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : copy.error);
    } finally { setUploading(false); }
  };

  const loadWorkspace = async () => {
    const response = await agentFetch(`${apiBaseUrl}/api/v1/agent/sessions/${encodeURIComponent(sessionId)}/workspace`);
    if (response.ok) setAssets((await response.json() as WorkspaceSnapshot).assets);
  };

  const consumeStream = async (response: Response) => {
    if (!response.body) throw new Error(copy.error);
    const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = "";
    while (true) {
      const { done, value } = await reader.read();
      buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done });
      const frames = buffer.split("\n\n"); buffer = frames.pop() ?? "";
      for (const frame of frames) {
        const name = frame.split("\n").find((line) => line.startsWith("event:"))?.slice(6).trim();
        const payload = frame.split("\n").filter((line) => line.startsWith("data:")).map((line) => line.slice(5).trim()).join("\n");
        if (name !== "agent-event" || !payload) continue;
        const agentEvent = JSON.parse(payload) as AgentEvent;
        if (agentEvent.messageType === "heartbeat") continue;
        if (agentEvent.messageType === "run_completed") { setRunning(false); continue; }
        if (agentEvent.messageType === "error") { setRunning(false); setError(typeof agentEvent.payload.message === "string" ? agentEvent.payload.message : copy.error); continue; }
        setEvents((current) => [...current, agentEvent]);
      }
      if (done) break;
    }
  };

  const run = async () => {
    const task = prompt.trim();
    if (!task || !sessionId || running || uploading) return;
    setRunning(true); setError(""); setEvents([]); setAssets([]);
    try {
      const response = await agentFetch(`${apiBaseUrl}/api/v1/agent/sessions/${encodeURIComponent(sessionId)}/runs`, {
        method: "POST", headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
        body: JSON.stringify({ prompt: task, mode, imageUrls: upload ? [upload.imageUrl] : [] }),
      });
      if (!response.ok) throw new Error(`${copy.error}: ${response.status}`);
      await consumeStream(response); await loadWorkspace();
    } catch (runError) {
      setError(runError instanceof Error ? runError.message : copy.error);
    } finally { setRunning(false); }
  };

  const bubbleItems = events.map((event) => ({ key: event.eventId, role: "assistant", content: getEventContent(event), loading: false }));

  return <XProvider theme={{ token: { colorPrimary: "#3a86ff", borderRadius: 8 } }}>
    <main className={styles.page}>
      <CrispixHeader />
      <section className={styles.canvas}>
        {events.length === 0 ? <div className={styles.hero}>
          <div className={styles.badge}><span className={styles.badgeMark}>J</span><span>{copy.badge}</span><b>NEW</b></div>
          <h1><span>{copy.lineOne}</span><span>{copy.lineTwo}<em>。</em></span></h1>
          <p>{copy.subtitle}</p>
        </div> : <section className={styles.resultPanel}>
          <div className={styles.resultHead}><span className={running ? styles.liveDot : ""} /> <b>{copy.eventTitle}</b><small>{running ? copy.waiting : "SSE complete"}</small></div>
          <Bubble.List className={styles.bubbles} role={{ assistant: { placement: "start" } }} items={bubbleItems} />
          {assets.length > 0 && <div className={styles.assetGrid}>{assets.map((asset) => <a key={asset.assetId} href={asset.imageUrl} target="_blank" rel="noreferrer"><img src={asset.imageUrl} alt={asset.title} /><span>{asset.source === "generated" ? "JENDA OUTPUT" : "REFERENCE"}</span></a>)}</div>}
        </section>}
        <section className={styles.composerSection}>
          {upload && <div className={styles.attachment}><FileImageOutlined /><span>{fileName}</span><button type="button" onClick={() => { setUpload(undefined); setFileName(""); }}>x</button></div>}
          <Sender value={prompt} onChange={setPrompt} onSubmit={() => void run()} loading={running} placeholder={copy.placeholder}
            suffix={<Button type="primary" shape="circle" aria-label="send" icon={<SendOutlined />} disabled={!prompt.trim() || uploading} onClick={() => void run()} />}
            footer={<div className={styles.senderFooter}><div><label className={styles.iconButton}><input type="file" accept="image/jpeg,image/png,image/webp,image/gif" onChange={uploadReference} disabled={uploading || running} />{uploading ? <LoadingOutlined spin /> : <LinkOutlined />}</label><button type="button" className={styles.iconButton}><AppstoreOutlined /></button><button type="button" className={`${styles.modeButton} ${mode === "plan-solve" ? styles.modeActive : ""}`} onClick={() => setMode((current) => current === "plan-solve" ? "react" : "plan-solve")}>{copy.planMode}: <b>{mode === "plan-solve" ? copy.planOn : copy.planOff}</b></button></div><span>{upload ? copy.uploaded : copy.upload}</span></div>}
          />
          {error && <div className={styles.error}>{error}</div>}
        </section>
        {events.length === 0 && <section className={styles.suggestions}>{suggestions.map((suggestion) => { const Icon = suggestion.icon; return <button type="button" key={suggestion.text} onClick={() => setPrompt(suggestion.text)}><Icon /><span>{suggestion.text}</span><b>{suggestion.tag}</b>{suggestion.tag.includes("\u5c01") && <SearchOutlined />}</button>; })}</section>}
      </section>
    </main>
  </XProvider>;
}
'@

$cssSource = @'
.page{min-height:100vh;color:#1e293b;background:radial-gradient(circle at 50% 23%,#f4f8ff 0,rgba(244,248,255,.2) 23%,transparent 52%),#fff}.canvas{width:min(100%,1040px);min-height:calc(100vh - 76px);padding:122px 24px 76px;margin:auto}.hero{max-width:760px;margin:0 auto 44px;text-align:center;animation:reveal .55s ease both}.badge{display:inline-flex;align-items:center;gap:8px;padding:6px 8px 6px 6px;color:#526178;background:#fff;border:1px solid #e5eaf3;border-radius:999px;box-shadow:0 6px 21px rgba(58,134,255,.08);font-size:12px}.badgeMark{display:grid;width:21px;height:21px;place-items:center;color:#fff;background:#3a86ff;border-radius:50%;font-family:Georgia,serif;font-size:13px;font-weight:700}.badge b{padding:3px 7px;color:#2d75e7;background:#edf5ff;border-radius:999px;font-size:9px;letter-spacing:.08em}.hero h1{display:grid;margin:26px 0 18px;color:#1d2d42;font-family:Georgia,"Noto Serif SC",serif;font-size:clamp(40px,5.5vw,64px);font-weight:400;letter-spacing:-.075em;line-height:1.12}.hero h1 span{display:block}.hero h1 em{color:#3a86ff;font-style:normal}.hero p{max-width:610px;margin:auto;color:#728097;font-size:15px;line-height:1.85}.composerSection{max-width:820px;margin:auto}.composerSection :global(.ant-sender){padding:12px 14px;background:#fff;border:1px solid #dce5f1;border-radius:14px;box-shadow:0 12px 36px rgba(38,79,128,.09);transition:border-color .2s,box-shadow .2s}.composerSection :global(.ant-sender:focus-within){border-color:#79aaff;box-shadow:0 13px 38px rgba(58,134,255,.16)}.composerSection :global(.ant-sender-content textarea){min-height:94px!important;padding:9px 7px;color:#24344a;font-size:15px;line-height:1.7}.composerSection :global(.ant-sender-actions-list-presets .ant-btn-primary){background:#3a86ff}.senderFooter{display:flex;align-items:center;justify-content:space-between;padding:3px 0 0;color:#9aa7b9;font-size:11px}.senderFooter>div{display:flex;align-items:center;gap:8px}.iconButton,.modeButton{display:inline-flex;align-items:center;justify-content:center;min-width:28px;height:28px;padding:0;color:#627087;cursor:pointer;background:#fff;border:1px solid #e0e7f0;border-radius:7px}.iconButton input{display:none}.modeButton{gap:4px;padding:0 9px;font-size:11px}.modeButton b{color:#7b8798;font-weight:500}.modeActive{color:#286fdb;background:#eff6ff;border-color:#a8c8ff}.modeActive b{color:#286fdb}.attachment{display:flex;align-items:center;gap:8px;width:max-content;max-width:100%;padding:7px 10px;margin:0 0 9px;color:#516178;background:#f5f9ff;border:1px solid #d9e7fa;border-radius:8px;font-size:12px}.attachment span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.attachment button{margin-left:4px;color:#7e8da2;cursor:pointer;background:transparent;border:0}.suggestions{display:grid;grid-template-columns:1fr 1fr;gap:10px;max-width:820px;padding-top:16px;margin:auto}.suggestions button{display:grid;grid-template-columns:18px minmax(0,1fr) auto;align-items:center;gap:9px;padding:13px 14px;color:#516078;cursor:pointer;text-align:left;background:rgba(255,255,255,.75);border:1px solid #e7edf5;border-radius:10px;transition:.2s}.suggestions button:nth-child(3){grid-column:1 / -1}.suggestions button:hover{border-color:#9ec1fa;background:#fff;box-shadow:0 8px 18px rgba(58,134,255,.1);transform:translateY(-1px)}.suggestions button>span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px}.suggestions button>b{padding:3px 6px;color:#6580a5;background:#f0f5fb;border-radius:999px;font-size:9px;font-weight:500;white-space:nowrap}.suggestions button :global(.anticon){color:#3a86ff;font-size:15px}.resultPanel{max-width:820px;padding:18px 18px 12px;margin:0 auto 28px;background:#fff;border:1px solid #e0e8f1;border-radius:14px;box-shadow:0 12px 35px rgba(41,78,120,.08)}.resultHead{display:flex;align-items:center;gap:8px;padding:0 2px 14px;color:#40516a;border-bottom:1px solid #edf1f6;font-size:13px}.resultHead small{margin-left:auto;color:#8b99ab}.liveDot{width:7px;height:7px;background:#3a86ff;border-radius:50%;box-shadow:0 0 0 4px #eaf3ff;animation:pulse 1.2s infinite}.bubbles{padding:17px 0}.bubbles :global(.ant-bubble-content){max-width:100%;color:#40516a!important;background:#f7faff!important;border:1px solid #e1eafa!important;border-radius:10px!important;white-space:pre-wrap}.assetGrid{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;padding-top:4px}.assetGrid a{position:relative;overflow:hidden;display:block;aspect-ratio:1;border-radius:9px;background:#eef4fb}.assetGrid img{width:100%;height:100%;object-fit:cover}.assetGrid span{position:absolute;right:6px;bottom:6px;padding:3px 5px;color:#fff;background:#15243bcc;border-radius:4px;font-size:8px;letter-spacing:.06em}.error{padding:9px 11px;margin-top:10px;color:#c83a3a;background:#fff2f0;border:1px solid #ffccc7;border-radius:8px;font-size:12px}@keyframes reveal{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:none}}@keyframes pulse{50%{opacity:.45;box-shadow:0 0 0 8px #eaf3ff}}@media(max-width:720px){.canvas{padding:76px 16px 40px}.hero{margin-bottom:30px}.hero h1{font-size:40px;letter-spacing:-.06em}.hero p{font-size:13px}.suggestions{grid-template-columns:1fr}.suggestions button:nth-child(3){grid-column:auto}.suggestions button>b{display:none}.senderFooter>span{display:none}.assetGrid{grid-template-columns:repeat(2,1fr)}}
'@

Write-Source "app\zh\agent\page.tsx" $pageSource
Write-Source "app\zh\agent\page.module.css" $cssSource
Write-Output "Applied Step 36 Jenda Agent Crispix-style layout."
