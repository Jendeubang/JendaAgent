"use client";

import { AppstoreOutlined, CameraOutlined, CaretDownOutlined, CopyOutlined, DownloadOutlined, EditOutlined, ExpandOutlined, FileImageOutlined, LinkOutlined, LoadingOutlined, PictureOutlined, SearchOutlined, SendOutlined } from "@ant-design/icons";
import { Button, Checkbox, Modal, Popover, Radio, message } from "antd";
import { Sender, XProvider } from "@ant-design/x";
import { ChangeEvent, useEffect, useState } from "react";
import { agentFetch } from "../../../lib/agentAuth";
import { uploadImageDirect, type DirectUploadedAsset } from "../../../lib/cosDirectUpload";
import { CrispixHeader } from "../../../components/CrispixHeader";
import styles from "./page.module.css";

type Mode = "plan-solve" | "react";
type PreferredTool = "OCR" | "IMAGE_GENERATE" | "IMAGE_EDIT";
type ImageProvider = "qwen" | "seedream" | "gemini-nano-banana-2" | "gemini-nano-banana-pro";
type AgentEvent = { eventId: string; runId: string; messageType: string; status: string; agent: string; payload: Record<string, unknown> };
type WorkspaceAsset = { assetId: string; title: string; imageUrl: string; source: "reference" | "generated" };
type WorkspaceSnapshot = { assets: WorkspaceAsset[] };

const apiBaseUrl = process.env.NEXT_PUBLIC_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const sessionStorageKey = "jenda-agent-crispix-session";
const toolPreferenceStorageKey = "jenda-agent-tool-preferences";
const imageProviderStorageKey = "jenda-agent-image-provider";
const promptOptimizationStorageKey = "jenda-agent-prompt-optimization";
const imageProviderOptions: Array<{ value: ImageProvider; label: string; description: string }> = [
  { value: "qwen", label: "Qwen Image", description: "\u5f53\u524d\u5df2\u63a5\u5165\u7684\u901a\u4e49\u56fe\u50cf\u751f\u6210\u4e0e\u7f16\u8f91" },
  { value: "seedream", label: "SeedDream 4.5", description: "\u586b\u5165\u706b\u5c71\u65b9\u821f\u51ed\u8bc1\u540e\u53ef\u7acb\u5373\u542f\u7528" },
  { value: "gemini-nano-banana-2", label: "NanoBanana 2", description: "Gemini \u5feb\u901f\u56fe\u50cf\u751f\u6210\u4e0e\u56fe\u50cf\u7f16\u8f91" },
  { value: "gemini-nano-banana-pro", label: "NanoBanana Pro", description: "\u9ad8\u8d28\u91cf\u3001\u590d\u6742\u6784\u56fe\u4e0e\u9ad8\u6e05\u8f93\u51fa\uff08\u9700\u670d\u52a1\u7aef\u5f00\u5173\uff09" },
];
const toolOptions: Array<{ value: PreferredTool; label: string; description: string }> = [
  { value: "OCR", label: "OCR \u6587\u5b57\u8bc6\u522b", description: "\u4ece\u53c2\u8003\u56fe\u4e2d\u63d0\u53d6\u6587\u5b57" },
  { value: "IMAGE_GENERATE", label: "\u56fe\u50cf\u751f\u6210", description: "\u6839\u636e\u63d0\u793a\u8bcd\u521b\u4f5c\u89c6\u89c9\u5185\u5bb9" },
  { value: "IMAGE_EDIT", label: "\u56fe\u50cf\u7f16\u8f91", description: "\u57fa\u4e8e\u53c2\u8003\u56fe\u8fdb\u884c\u7f16\u8f91\u548c\u91cd\u7ed8" },
];
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
  preferenceTitle: "\u6a21\u578b\u504f\u597d",
  preferenceHint: "\u9009\u62e9\u4f7f\u7528\u7684\u5de5\u5177\uff08\u53ef\u591a\u9009\uff09",
  preferenceAuto: "\u6062\u590d\u81ea\u52a8\u9009\u62e9",
  preferenceAria: "\u914d\u7f6e\u6a21\u578b\u5de5\u5177\u504f\u597d",
  providerLabel: "\u56fe\u50cf\u6a21\u578b",
  providerAuto: "\u8ddf\u968f\u670d\u52a1\u7aef\u9ed8\u8ba4",
  promptOptimizationLabel: "\u63d0\u793a\u8bcd RAG \u4f18\u5316",
  promptOptimizationHint: "\u8c03\u7528\u77e5\u8bc6\u5e93\u540e\u4f18\u5316\u56fe\u50cf\u751f\u6210\u4e0e\u7f16\u8f91\u63d0\u793a\u8bcd",
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

const eventStageLabels: Record<string, string> = {
  run_started: "\u4efb\u52a1\u5df2\u542f\u52a8",
  plan: "\u4efb\u52a1\u89c4\u5212",
  task: "\u5b50\u4efb\u52a1\u8c03\u5ea6",
  tool_call: "\u5de5\u5177\u8c03\u7528",
  tool_result: "\u5de5\u5177\u7ed3\u679c",
  image: "\u56fe\u50cf\u4ea7\u7269",
  summary: "\u4ea4\u4ed8\u6c47\u603b",
  confirmation_required: "\u7b49\u5f85\u4eba\u5de5\u786e\u8ba4",
  react_think: "ReAct \u601d\u8003",
  react_act: "ReAct \u6267\u884c",
  react_observation: "ReAct \u89c2\u5bdf",
  react_decision: "ReAct \u4e0b\u4e00\u6b65\u51b3\u7b56",
  react_terminated: "ReAct \u5df2\u7ec8\u6b62",
  prompt_optimization: "\u63d0\u793a\u8bcd\u4f18\u5316",
};

function eventStage(event: AgentEvent) {
  return eventStageLabels[event.messageType] ?? event.messageType;
}
const reactEventTypes = new Set(["react_think", "react_act", "react_observation", "react_decision", "react_terminated"]);
function isReactEvent(event: AgentEvent) { return reactEventTypes.has(event.messageType); }
function reactRound(event: AgentEvent) { return typeof event.payload.round === "number" ? event.payload.round : 0; }
function reactEventContent(event: AgentEvent) {
  const fields: string[] = [];
  const reasoning = typeof event.payload.reasoningSummary === "string" ? event.payload.reasoningSummary : "";
  const action = typeof event.payload.action === "string" ? event.payload.action : "";
  const toolInput = typeof event.payload.toolInput === "string" ? event.payload.toolInput : "";
  const toolResult = typeof event.payload.toolResult === "string" ? event.payload.toolResult : "";
  const nextDecision = typeof event.payload.nextDecision === "string" ? event.payload.nextDecision : "";
  if (reasoning) fields.push(`Reasoning: ${reasoning}`);
  if (action) fields.push(`Action: ${action}`);
  if (toolInput) fields.push(`Tool input: ${toolInput}`);
  if (toolResult) fields.push(`Observation: ${toolResult}`);
  if (nextDecision) fields.push(`Next: ${nextDecision}`);
  return fields.length ? fields.join("\n") : getEventContent(event);
}

function promptText(payload: Record<string, unknown>, field: string) { return typeof payload[field] === "string" ? payload[field] : ""; }
function PromptOptimizationView({ event }: { event: AgentEvent }) {
  const original = promptText(event.payload, "originalPrompt");
  const optimized = promptText(event.payload, "optimizedPrompt");
  const applied = event.payload.applied === true;
  const ragUsed = event.payload.ragUsed === true;
  const version = promptText(event.payload, "knowledgeVersion");
  const hits = Array.isArray(event.payload.knowledgeHits) ? event.payload.knowledgeHits.filter((hit): hit is Record<string, unknown> => typeof hit === "object" && hit !== null) : [];
  return <div className={styles.promptOptimization}>
    <div className={styles.promptOptimizationHead}><span className={applied ? styles.promptApplied : styles.promptSkipped}>{applied ? "已应用" : "未改写"}</span><span>{ragUsed ? "Qdrant RAG" : "规则回退"}{version ? ` · ${version}` : ""}</span></div>
    <div className={styles.promptComparison}><div><small>原始提示词</small><pre>{original}</pre></div><div><small>优化后提示词</small><pre>{optimized}</pre></div></div>
    {hits.length > 0 && <div className={styles.knowledgeHits}>{hits.map((hit, index) => <span key={`${String(hit.id ?? "hit")}-${index}`}>{String(hit.title ?? hit.id ?? "知识规则")}<em>{typeof hit.score === "number" ? hit.score.toFixed(2) : ""}</em></span>)}</div>}
  </div>;
}
function eventStateClass(event: AgentEvent) {
  if (event.status === "failed") return styles.processFailed;
  if (event.status === "waiting_confirmation") return styles.processWaiting;
  if (event.status === "running") return styles.processRunning;
  return styles.processComplete;
}
function assetDownloadName(asset: WorkspaceAsset) {
  const safeTitle = (asset.title || "jenda-output").replace(/[^a-zA-Z0-9._-]+/g, "-");
  return /\.(png|jpe?g|webp|gif)$/i.test(safeTitle) ? safeTitle : `${safeTitle}.png`;
}

async function downloadAsset(asset: WorkspaceAsset) {
  try {
    const response = await fetch(asset.imageUrl);
    if (!response.ok) throw new Error(`download failed: ${response.status}`);
    const blob = await response.blob();
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = objectUrl;
    link.download = assetDownloadName(asset);
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(objectUrl);
    message.success("\u56fe\u7247\u5df2\u5f00\u59cb\u4e0b\u8f7d");
  } catch {
    window.open(asset.imageUrl, "_blank", "noopener,noreferrer");
    message.info("\u5df2\u6253\u5f00\u56fe\u7247\u94fe\u63a5\uff0c\u8bf7\u5728\u65b0\u7a97\u53e3\u4fdd\u5b58");
  }
}

async function copyAsset(asset: WorkspaceAsset) {
  try {
    const response = await fetch(asset.imageUrl);
    if (!response.ok) throw new Error("image unavailable");
    const blob = await response.blob();
    if (navigator.clipboard?.write && typeof ClipboardItem !== "undefined") {
      await navigator.clipboard.write([new ClipboardItem({ [blob.type || "image/png"]: blob })]);
      message.success("\u56fe\u7247\u5df2\u590d\u5236\uff0c\u53ef\u76f4\u63a5\u7c98\u8d34");
      return;
    }
    await navigator.clipboard.writeText(asset.imageUrl);
    message.success("\u56fe\u7247\u94fe\u63a5\u5df2\u590d\u5236");
  } catch {
    try {
      await navigator.clipboard.writeText(asset.imageUrl);
      message.success("\u56fe\u7247\u94fe\u63a5\u5df2\u590d\u5236");
    } catch {
      message.error("\u590d\u5236\u5931\u8d25\uff0c\u8bf7\u4f7f\u7528\u4e0b\u8f7d\u6309\u94ae");
    }
  }
}

function DeliveryCard({ asset, onPreview }: { asset: WorkspaceAsset; onPreview: (asset: WorkspaceAsset) => void }) {
  const [busy, setBusy] = useState<"copy" | "download">();
  const copy = async () => { setBusy("copy"); try { await copyAsset(asset); } finally { setBusy(undefined); } };
  const download = async () => { setBusy("download"); try { await downloadAsset(asset); } finally { setBusy(undefined); } };
  return <article className={styles.deliveryCard}>
    <button type="button" className={styles.deliveryImageButton} onClick={() => onPreview(asset)} aria-label="\u9884\u89c8\u751f\u6210\u56fe\u7247">
      <img src={asset.imageUrl} alt={asset.title} referrerPolicy="no-referrer" />
      <span><ExpandOutlined /> {"\u70b9\u51fb\u67e5\u770b\u5927\u56fe"}</span>
    </button>
    <div className={styles.deliveryInfo}>
      <div><em>JENDA OUTPUT</em><strong>{asset.title || "\u751f\u6210\u7ed3\u679c"}</strong></div>
      <small>{"\u5df2\u5f52\u6863\u5230\u60a8\u7684 COS \u8d44\u4ea7\u5e93"}</small>
      <div className={styles.deliveryActions}>
        <Button size="small" icon={<ExpandOutlined />} onClick={() => onPreview(asset)}>{"\u9884\u89c8"}</Button>
        <Button size="small" icon={<CopyOutlined />} loading={busy === "copy"} onClick={() => void copy()}>{"\u590d\u5236"}</Button>
        <Button size="small" type="primary" icon={<DownloadOutlined />} loading={busy === "download"} onClick={() => void download()}>{"\u4e0b\u8f7d"}</Button>
      </div>
    </div>
  </article>;
}
function ReferenceCard({ asset, selected, onSelect, onPreview }: { asset: WorkspaceAsset; selected: boolean; onSelect: (checked: boolean) => void; onPreview: (asset: WorkspaceAsset) => void }) {
  return <article className={`${styles.referenceCard} ${selected ? styles.referenceSelected : ""}`}>
    <button type="button" className={styles.referenceImageButton} onClick={() => onPreview(asset)} aria-label="\u9884\u89c8\u53c2\u8003\u56fe"><img src={asset.imageUrl} alt={asset.title} referrerPolicy="no-referrer" /></button>
    <div><Checkbox checked={selected} onChange={(event) => onSelect(event.target.checked)}>\u4f5c\u4e3a\u672c\u6b21\u53c2\u8003\u56fe</Checkbox><small>{asset.title || "\u5df2\u4e0a\u4f20\u56fe\u7247"}</small></div>
  </article>;
}
function newSessionId() { return `session-${crypto.randomUUID()}`; }

export default function JendaAgentPage() {
  const [sessionId, setSessionId] = useState("");
  const [mode, setMode] = useState<Mode>("react");
  const [preferredTools, setPreferredTools] = useState<PreferredTool[]>([]);
  const [imageProvider, setImageProvider] = useState<ImageProvider>();
  const [promptOptimizationEnabled, setPromptOptimizationEnabled] = useState(true);
  const [prompt, setPrompt] = useState("");
  const [events, setEvents] = useState<AgentEvent[]>([]);
  const [assets, setAssets] = useState<WorkspaceAsset[]>([]);
  const [previewAsset, setPreviewAsset] = useState<WorkspaceAsset>();
  const [upload, setUpload] = useState<DirectUploadedAsset>();
  const [fileName, setFileName] = useState("");
  const [running, setRunning] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState("");
  const [reactTimelineOpen, setReactTimelineOpen] = useState(true);
  const [referenceOpen, setReferenceOpen] = useState(false);
  const [deliveryOpen, setDeliveryOpen] = useState(false);
  const [selectedReferenceIds, setSelectedReferenceIds] = useState<string[]>([]);

  useEffect(() => {
    const stored = window.localStorage.getItem(sessionStorageKey);
    const next = stored || newSessionId();
    if (!stored) window.localStorage.setItem(sessionStorageKey, next);
    setSessionId(next);
  }, []);  useEffect(() => {
    const stored = window.localStorage.getItem(toolPreferenceStorageKey);
    if (!stored) return;
    try {
      const parsed = JSON.parse(stored);
      if (Array.isArray(parsed)) {
        setPreferredTools(parsed.filter((value): value is PreferredTool => toolOptions.some((tool) => tool.value === value)));
      }
    } catch {
      window.localStorage.removeItem(toolPreferenceStorageKey);
    }
  }, []);
  useEffect(() => {
    window.localStorage.setItem(toolPreferenceStorageKey, JSON.stringify(preferredTools));
  }, [preferredTools]);
  useEffect(() => {
    const stored = window.localStorage.getItem(imageProviderStorageKey);
    if (stored === "qwen" || stored === "seedream" || stored === "gemini-nano-banana-2" || stored === "gemini-nano-banana-pro") setImageProvider(stored);
  }, []);
  useEffect(() => {
    if (imageProvider) window.localStorage.setItem(imageProviderStorageKey, imageProvider);
    else window.localStorage.removeItem(imageProviderStorageKey);
  }, [imageProvider]);  useEffect(() => {
    const stored = window.localStorage.getItem(promptOptimizationStorageKey);
    if (stored === "false") setPromptOptimizationEnabled(false);
  }, []);
  useEffect(() => {
    window.localStorage.setItem(promptOptimizationStorageKey, String(promptOptimizationEnabled));
  }, [promptOptimizationEnabled]);

  const uploadReference = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file || !sessionId || uploading || running) return;
    setUploading(true); setError("");
    try {
      const asset = await uploadImageDirect(apiBaseUrl, sessionId, file);
      setUpload(asset); setFileName(file.name);
      setAssets((current) => current.some((item) => item.assetId === asset.assetId) ? current : [{ assetId: asset.assetId, title: file.name, imageUrl: asset.imageUrl, source: "reference" }, ...current]);
      setSelectedReferenceIds((current) => current.includes(asset.assetId) ? current : [...current, asset.assetId]);
      setReferenceOpen(true);
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : copy.error);
    } finally { setUploading(false); }
  };

  const loadWorkspace = async () => {
    const response = await agentFetch(`${apiBaseUrl}/api/v1/agent/sessions/${encodeURIComponent(sessionId)}/workspace`);
    if (response.ok) setAssets((await response.json() as WorkspaceSnapshot).assets);
  };

  useEffect(() => {
    if (!sessionId) return;
    void agentFetch(`${apiBaseUrl}/api/v1/agent/sessions/${encodeURIComponent(sessionId)}/workspace`)
      .then(async (response) => { if (response.ok) setAssets((await response.json() as WorkspaceSnapshot).assets); })
      .catch(() => undefined);
  }, [sessionId]);

  useEffect(() => {
    const referenceIds = new Set(assets.filter((asset) => asset.source === "reference").map((asset) => asset.assetId));
    setSelectedReferenceIds((current) => current.filter((assetId) => referenceIds.has(assetId)));

  }, [assets]);

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

  const resolveConfirmation = async (event: AgentEvent, approved: boolean) => {
    const approvalId = typeof event.payload.approvalId === "string" ? event.payload.approvalId : "";
    if (!approvalId || !event.runId || running) return;
    setRunning(true); setError("");
    try {
      const response = await agentFetch(`${apiBaseUrl}/api/v1/agent/sessions/${encodeURIComponent(sessionId)}/runs/${encodeURIComponent(event.runId)}/approvals/${encodeURIComponent(approvalId)}`, {
        method: "POST", headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
        body: JSON.stringify({ approved, note: "" }),
      });
      if (!response.ok) throw new Error(`${copy.error}: ${response.status}`);
      await consumeStream(response); await loadWorkspace();
    } catch (approvalError) {
      setError(approvalError instanceof Error ? approvalError.message : copy.error);
    } finally { setRunning(false); }
  };
  const run = async () => {
    const task = prompt.trim();
    if (!task || !sessionId || running || uploading) return;
    const imageUrls = assets.filter((asset) => asset.source === "reference" && selectedReferenceIds.includes(asset.assetId)).map((asset) => asset.imageUrl);

    setRunning(true); setError(""); setEvents([]); setAssets((current) => current.filter((asset) => asset.source === "reference"));
    try {
      const response = await agentFetch(`${apiBaseUrl}/api/v1/agent/sessions/${encodeURIComponent(sessionId)}/runs`, {
        method: "POST", headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
        body: JSON.stringify({ prompt: task, mode, imageUrls: [...new Set(imageUrls)], preferredTools, imageProvider, promptOptimizationEnabled }),
      });
      if (!response.ok) throw new Error(`${copy.error}: ${response.status}`);
      await consumeStream(response); await loadWorkspace();
    } catch (runError) {
      setError(runError instanceof Error ? runError.message : copy.error);
    } finally { setRunning(false); }
  };


  const reactEvents = events.filter(isReactEvent);
  const standardEvents = events.filter((event) => !isReactEvent(event));
  const referenceAssets = assets.filter((asset) => asset.source === "reference");
  const generatedAssets = assets.filter((asset) => asset.source === "generated");
  return <XProvider theme={{ token: { colorPrimary: "#3a86ff", borderRadius: 8 } }}>
    <main className={styles.page}>
      <CrispixHeader />
      <section className={styles.canvas}>
        {events.length === 0 && assets.length === 0 ? <div className={styles.hero}>
          <div className={styles.badge}><span className={styles.badgeMark}>J</span><span>{copy.badge}</span><b>NEW</b></div>
          <h1><span>{copy.lineOne}</span><span>{copy.lineTwo}<em>.</em></span></h1>
          <p>{copy.subtitle}</p>
        </div> : <section className={styles.resultPanel}>
          <div className={styles.resultHead}><span className={running ? styles.liveDot : ""} /> <b>{copy.eventTitle}</b><small>{running ? copy.waiting : "SSE complete"}</small></div>
          {reactEvents.length > 0 && <section className={styles.reactTimeline} aria-live="polite">
            <button type="button" className={styles.reactTimelineToggle} onClick={() => setReactTimelineOpen((open) => !open)} aria-expanded={reactTimelineOpen}>
              <span><b>ReAct Timeline</b><small>{reactEvents.length} events / {new Set(reactEvents.map(reactRound).filter(Boolean)).size} rounds</small></span><em>{reactTimelineOpen ? "Hide" : "Show"}</em>
            </button>
            {reactTimelineOpen && <div className={styles.reactTimelineBody}>
              {reactEvents.map((event) => <article key={event.eventId} className={`${styles.reactStep} ${eventStateClass(event)}`}>
                <div className={styles.reactRound}>R{reactRound(event) || "-"}</div>
                <div><div className={styles.reactStepHead}><b>{eventStage(event)}</b><em>{event.status}</em></div><pre>{reactEventContent(event)}</pre></div>
              </article>)}
            </div>}
          </section>}
          <div className={styles.processList} aria-live="polite">
            {standardEvents.map((event, index) => <article key={event.eventId} className={`${styles.processItem} ${eventStateClass(event)}`}>
              <div className={styles.processRail}><span>{index + 1}</span></div>
              <div className={styles.processBody}>
                <div className={styles.processMeta}><div><b>{eventStage(event)}</b><small>{event.agent}</small></div><em>{event.status}</em></div>
                {event.messageType === "prompt_optimization" ? <PromptOptimizationView event={event} /> : <p>{getEventContent(event)}</p>}
                {event.messageType === "confirmation_required" && <div className={styles.confirmationActions}>
                  <Button type="primary" size="small" loading={running} onClick={() => void resolveConfirmation(event, true)}>{"\u786e\u8ba4\u7ee7\u7eed"}</Button>
                  <Button size="small" disabled={running} onClick={() => void resolveConfirmation(event, false)}>{"\u62d2\u7edd"}</Button>
                </div>}
              </div>
            </article>)}
            {running && <article className={`${styles.processItem} ${styles.processPending}`}><div className={styles.processRail}><span>...</span></div><div className={styles.processBody}><div className={styles.processMeta}><div><b>{copy.waiting}</b><small>Jenda Agent</small></div><em>live</em></div></div></article>}
          </div>
          {referenceAssets.length > 0 && <section className={styles.referenceSection}>
            <button type="button" className={styles.assetSectionToggle} onClick={() => setReferenceOpen((open) => !open)} aria-expanded={referenceOpen}>
              <span><em>REFERENCE</em><b>{"\u4e0a\u4f20\u7684\u53c2\u8003\u56fe"}</b><small>{referenceAssets.length} {"\u5f20"} · {selectedReferenceIds.length} {"\u5df2\u9009"}</small></span><CaretDownOutlined className={referenceOpen ? styles.toggleOpen : ""} />
            </button>
            {referenceOpen && <div className={styles.referenceGrid}>{referenceAssets.map((asset) => <ReferenceCard key={asset.assetId} asset={asset} selected={selectedReferenceIds.includes(asset.assetId)} onPreview={setPreviewAsset} onSelect={(checked) => setSelectedReferenceIds((current) => checked ? (current.includes(asset.assetId) ? current : [...current, asset.assetId]) : current.filter((assetId) => assetId !== asset.assetId))} />)}</div>}
          </section>}
          {generatedAssets.length > 0 && <section className={styles.deliverySection}>
            <button type="button" className={styles.assetSectionToggle} onClick={() => setDeliveryOpen((open) => !open)} aria-expanded={deliveryOpen}>
              <span><em>{"\u4efb\u52a1\u5b8c\u6210"}</em><b>{"\u4f60\u7684\u56fe\u50cf\u4ea4\u4ed8\u7269"}</b><small>{generatedAssets.length} {"\u5f20\u6210\u54c1"}</small></span><CaretDownOutlined className={deliveryOpen ? styles.toggleOpen : ""} />
            </button>
            {deliveryOpen && <div className={styles.deliveryGrid}>{generatedAssets.map((asset) => <DeliveryCard key={asset.assetId} asset={asset} onPreview={setPreviewAsset} />)}</div>}
          </section>}
        </section>}
        {previewAsset && <Modal open footer={null} onCancel={() => setPreviewAsset(undefined)} width={860} centered className={styles.previewModal} title={previewAsset.title || "\u56fe\u50cf\u4ea4\u4ed8\u7269"}>
          <img className={styles.previewImage} src={previewAsset.imageUrl} alt={previewAsset.title} referrerPolicy="no-referrer" />
          <div className={styles.previewActions}><Button icon={<CopyOutlined />} onClick={() => void copyAsset(previewAsset)}>{"\u590d\u5236\u56fe\u7247"}</Button><Button type="primary" icon={<DownloadOutlined />} onClick={() => void downloadAsset(previewAsset)}>{"\u4e0b\u8f7d\u56fe\u7247"}</Button></div>
        </Modal>}
        <section className={styles.composerSection}>
          {selectedReferenceIds.length > 0 && <div className={styles.attachment}><FileImageOutlined /><span>{`${selectedReferenceIds.length} \u5f20\u53c2\u8003\u56fe\u5df2\u9009\u4e2d`}</span><button type="button" onClick={() => { setSelectedReferenceIds([]); setUpload(undefined); setFileName(""); }}>x</button></div>}
          <Sender value={prompt} onChange={setPrompt} onSubmit={() => void run()} loading={running} placeholder={copy.placeholder}
            suffix={<Button type="primary" shape="circle" aria-label="send" icon={<SendOutlined />} disabled={!prompt.trim() || uploading} onClick={() => void run()} />}
            footer={<div className={styles.senderFooter}><div><label className={styles.iconButton}><input type="file" accept="image/jpeg,image/png,image/webp,image/gif" onChange={uploadReference} disabled={uploading || running} />{uploading ? <LoadingOutlined spin /> : <LinkOutlined />}</label><Popover
  trigger="click"
  placement="topLeft"
  overlayClassName={styles.preferencePopover}
  content={<div className={styles.preferencePanel}>
    <div className={styles.preferenceHead}><div><b>{copy.preferenceTitle}</b><p>{copy.preferenceHint}</p></div><span>{preferredTools.length || "AUTO"}</span></div>
    <div className={styles.promptOptimizationToggle}><Checkbox checked={promptOptimizationEnabled} onChange={(event) => setPromptOptimizationEnabled(event.target.checked)}><span><b>{copy.promptOptimizationLabel}</b><small>{copy.promptOptimizationHint}</small></span></Checkbox></div>
    <div className={styles.providerChoice}><b>{copy.providerLabel}</b><Radio.Group value={imageProvider ?? ""} onChange={(event) => setImageProvider(event.target.value || undefined)}>
      <Radio value="">{copy.providerAuto}</Radio>
      {imageProviderOptions.map((provider) => <Radio value={provider.value} key={provider.value}><span><b>{provider.label}</b><small>{provider.description}</small></span></Radio>)}
    </Radio.Group></div>
    <Checkbox.Group value={preferredTools} onChange={(values) => setPreferredTools(values as PreferredTool[])} className={styles.preferenceChoices}>
      {toolOptions.map((tool) => <Checkbox value={tool.value} key={tool.value}><span><b>{tool.label}</b><small>{tool.description}</small></span></Checkbox>)}
    </Checkbox.Group>
    <button type="button" className={styles.resetPreference} onClick={() => setPreferredTools([])} disabled={preferredTools.length === 0}>{copy.preferenceAuto}</button>
  </div>}
>
  <button type="button" className={`${styles.iconButton} ${preferredTools.length || imageProvider ? styles.preferenceActive : ""}`} aria-label={copy.preferenceAria}>
    <AppstoreOutlined />{preferredTools.length > 0 && <span className={styles.preferenceCount}>{preferredTools.length}</span>}
  </button>
</Popover><button type="button" className={`${styles.modeButton} ${mode === "plan-solve" ? styles.modeActive : ""}`} onClick={() => setMode((current) => current === "plan-solve" ? "react" : "plan-solve")}>{copy.planMode}: <b>{mode === "plan-solve" ? copy.planOn : copy.planOff}</b></button></div><span>{upload ? copy.uploaded : copy.upload}</span></div>}
          />
          {error && <div className={styles.error}>{error}</div>}
        </section>
        {events.length === 0 && <section className={styles.suggestions}>{suggestions.map((suggestion) => { const Icon = suggestion.icon; return <button type="button" key={suggestion.text} onClick={() => setPrompt(suggestion.text)}><Icon /><span>{suggestion.text}</span><b>{suggestion.tag}</b>{suggestion.tag.includes("\u5c01") && <SearchOutlined />}</button>; })}</section>}
      </section>
    </main>
  </XProvider>;
}