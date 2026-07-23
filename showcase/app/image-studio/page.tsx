"use client";

import {
  DownloadOutlined,
  EyeOutlined,
  FileImageOutlined,
  LoadingOutlined,
  PartitionOutlined,
  PlusOutlined,
  ReloadOutlined,
  RetweetOutlined,
  SaveOutlined,
  UploadOutlined,
} from "@ant-design/icons";
import { Button, Input, Slider, Tag, Tooltip } from "antd";
import { ChangeEvent, useEffect, useState } from "react";
import { uploadImageDirect, type DirectUploadedAsset } from "../../lib/cosDirectUpload";
import { agentFetch } from "../../lib/agentAuth";
import { AgentAccountMenu } from "../../components/AgentAccountMenu";
import styles from "./page.module.css";

type AgentEvent = {
  eventId: string;
  messageType: string;
  status: string;
  agent: string;
  payload: Record<string, unknown>;
};

type WorkspaceAsset = {
  assetId: string;
  runId: string;
  title: string;
  imageUrl: string;
  source: "reference" | "generated";
  occurredAt: string;
};

type WorkspaceSnapshot = { assets: WorkspaceAsset[] };
type ImageSize = { width: number; height: number };

const apiBaseUrl = process.env.NEXT_PUBLIC_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const storageKey = "jenda-image-studio-session";

const copy = {
  sourceImage: "\u539f\u59cb\u56fe\u7247",
  optionalMask: "\u53ef\u9009\u906e\u7f69\u56fe",
  uploadSource: "\u4e0a\u4f20\u9700\u8981\u7f16\u8f91\u7684\u56fe\u7247",
  whiteEdit: "\u767d\u8272\u533a\u57df\u7f16\u8f91\uff0c\u9ed1\u8272\u533a\u57df\u4fdd\u7559",
  titleLead: "\u7cbe\u4fee\u6bcf\u4e00\u4e2a\u50cf\u7d20\u3002",
  titleEm: "\u4fdd\u7559\u6bcf\u4e00\u4efd\u8bc1\u636e\u3002",
  intro: "\u539f\u56fe\u3001\u906e\u7f69\u5f15\u5bfc\u7f16\u8f91\u3001SSE \u8fdb\u5ea6\u3001COS \u4ea7\u7269\u53ca\u53ef\u8ffd\u6eaf\u4f1a\u8bdd\u8bb0\u5f55\u3002",
  activeSession: "\u5f53\u524d\u4f1a\u8bdd",
  refreshAssets: "\u5237\u65b0\u8d44\u4ea7",
  inputs: "\u8f93\u5165\u56fe\u7247",
  maskProtocol: "\u906e\u7f69\u8bf4\u660e",
  maskDetail: "\u4e0a\u4f20\u9ed1\u767d\u906e\u7f69\u56fe\u4f5c\u4e3a\u7b2c\u4e8c\u5f20\u8f93\u5165\u56fe\u3002\u767d\u8272\u533a\u57df\u5141\u8bb8\u4fee\u6539\uff0c\u9ed1\u8272\u533a\u57df\u5e94\u4fdd\u6301\u4e0d\u53d8\u3002",
  editDirection: "\u7f16\u8f91\u6307\u4ee4",
  runEdit: "\u5f00\u59cb\u56fe\u50cf\u7f16\u8f91",
  compareCanvas: "\u5bf9\u6bd4\u753b\u5e03",
  beforeAfter: "\u7f16\u8f91\u524d\u540e\u5bf9\u6bd4",
  readyEdit: "\u7b49\u5f85\u7f16\u8f91",
  archived: "\u5df2\u5f52\u6863\u81f3 COS",
  startSource: "\u5148\u4e0a\u4f20\u539f\u59cb\u56fe\u7247",
  startSourceDetail: "\u4e0a\u4f20\u4eba\u50cf\u3001\u4ea7\u54c1\u56fe\u6216\u573a\u666f\u56fe\u3002\u53ea\u6709\u9700\u8981\u5c40\u90e8\u9650\u5b9a\u4fee\u6539\u65f6\u624d\u9700\u8981\u4e0a\u4f20\u906e\u7f69\u56fe\u3002",
  sourceReady: "\u539f\u59cb\u56fe\u5df2\u51c6\u5907\u597d\uff0c\u8bf7\u8f93\u5165\u7f16\u8f91\u6307\u4ee4\u3002",
  editing: "\u667a\u80fd\u4f53\u6b63\u5728\u7f16\u8f91\u56fe\u7247\u3002",
  editingDetail: "\u89c4\u5212\u3001\u5de5\u5177\u8c03\u7528\u548c COS \u5f52\u6863\u8fdb\u5ea6\u4f1a\u663e\u793a\u5728\u6267\u884c\u8f68\u8ff9\u4e2d\u3002",
  original: "\u539f\u56fe",
  edited: "\u7f16\u8f91\u540e",
  liveTrace: "\u5b9e\u65f6\u6267\u884c\u8f68\u8ff9",
  noEvents: "\u5c1a\u65e0\u5de5\u5177\u4e8b\u4ef6\u3002",
  cosAssets: "COS \u8d44\u4ea7",
  noAssets: "\u751f\u6210\u7684 COS \u56fe\u7247\u4f1a\u663e\u793a\u5728\u8fd9\u91cc\u3002",
  inspector: "\u8d44\u4ea7\u8be6\u60c5",
  preview: "\u9884\u89c8",
  download: "\u4e0b\u8f7d",
  storage: "\u5b58\u50a8",
  asset: "\u8d44\u4ea7 ID",
  resolution: "\u5206\u8fa8\u7387",
  run: "\u8fd0\u884c ID",
  loading: "\u52a0\u8f7d\u4e2d",
  defaultInstruction: "\u5c06\u80cc\u666f\u66ff\u6362\u4e3a\u96e8\u591c\u8d5b\u535a\u670b\u514b\u8857\u9053\uff0c\u4fdd\u6301\u4eba\u7269\u4e3b\u4f53\u4e0d\u53d8\u3002",
};

function stringValue(value: unknown, fallback = "") {
  return typeof value === "string" ? value : fallback;
}

function imageFromEvent(event: AgentEvent) {
  return stringValue(event.payload.imageUrl);
}

function eventDetail(event: AgentEvent) {
  if (event.messageType !== "prompt_optimization") {
    return stringValue(event.payload.content, stringValue(event.payload.message, ""));
  }
  const original = stringValue(event.payload.originalPrompt);
  const optimized = stringValue(event.payload.optimizedPrompt);
  const rules = Array.isArray(event.payload.retrievedRules)
    ? event.payload.retrievedRules.filter((value): value is string => typeof value === "string").join(", ")
    : "";
  return `Original: ${original}\nOptimized: ${optimized}\nRetrieved rules: ${rules || "none"}`;
}

function assetLabel(asset: WorkspaceAsset) {
  return asset.source === "generated" ? "COS OUTPUT" : "REFERENCE";
}

async function downloadImage(url: string, name: string) {
  try {
    const response = await fetch(url);
    if (!response.ok) throw new Error("download failed");
    const blob = await response.blob();
    const objectUrl = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = objectUrl;
    anchor.download = name;
    anchor.click();
    URL.revokeObjectURL(objectUrl);
  } catch {
    window.open(url, "_blank", "noopener,noreferrer");
  }
}

export default function ImageStudioPage() {
  const [sessionId, setSessionId] = useState("");
  const [source, setSource] = useState<DirectUploadedAsset>();
  const [mask, setMask] = useState<DirectUploadedAsset>();
  const [sourceName, setSourceName] = useState(copy.sourceImage);
  const [maskName, setMaskName] = useState(copy.optionalMask);
  const [instruction, setInstruction] = useState(copy.defaultInstruction);
  const [outputUrl, setOutputUrl] = useState("");
  const [workspaceAssets, setWorkspaceAssets] = useState<WorkspaceAsset[]>([]);
  const [selectedAsset, setSelectedAsset] = useState<WorkspaceAsset>();
  const [events, setEvents] = useState<AgentEvent[]>([]);
  const [split, setSplit] = useState(52);
  const [uploading, setUploading] = useState<"source" | "mask" | "">("");
  const [running, setRunning] = useState(false);
  const [status, setStatus] = useState("Create a source image, then direct the edit.");
  const [error, setError] = useState("");
  const [dimensions, setDimensions] = useState<Record<string, ImageSize>>({});

  useEffect(() => {
    const stored = window.localStorage.getItem(storageKey);
    const id = stored || `image-studio-${crypto.randomUUID()}`;
    if (!stored) window.localStorage.setItem(storageKey, id);
    setSessionId(id);
  }, []);

  const loadWorkspace = async () => {
    if (!sessionId) return;
    try {
      const response = await agentFetch(`${apiBaseUrl}/api/v1/agent/sessions/${encodeURIComponent(sessionId)}/workspace`);
      if (!response.ok) return;
      const snapshot = await response.json() as WorkspaceSnapshot;
      setWorkspaceAssets(snapshot.assets);
      const latest = snapshot.assets.find((asset) => asset.source === "generated");
      if (latest) {
        setOutputUrl(latest.imageUrl);
        setSelectedAsset(latest);
      }
    } catch {
      // The live result remains usable even when persistent workspace refresh is delayed.
    }
  };

  const upload = async (kind: "source" | "mask", event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file || !sessionId || uploading || running) return;
    setUploading(kind);
    setError("");
    setStatus(kind === "source" ? "Requesting a secure upload ticket for the source image." : "Uploading the mask as the second image input.");
    try {
      const asset = await uploadImageDirect(apiBaseUrl, sessionId, file);
      if (kind === "source") {
        setSource(asset);
        setSourceName(file.name);
      } else {
        setMask(asset);
        setMaskName(file.name);
      }
      setStatus(kind === "source" ? "Source image is ready for editing." : "Mask is ready. White means editable; black means preserve.");
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : "Image upload failed.");
      setStatus("Upload failed.");
    } finally {
      setUploading("");
    }
  };

  const consume = async (response: Response) => {
    if (!response.body) throw new Error("Streaming response is unavailable.");
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    while (true) {
      const { done, value } = await reader.read();
      buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done });
      const frames = buffer.split("\n\n");
      buffer = frames.pop() ?? "";
      for (const frame of frames) {
        const name = frame.split("\n").find((line) => line.startsWith("event:"))?.slice(6).trim();
        const data = frame.split("\n").filter((line) => line.startsWith("data:")).map((line) => line.slice(5).trim()).join("\n");
        if (name !== "agent-event" || !data) continue;
        const agentEvent = JSON.parse(data) as AgentEvent;
        if (agentEvent.messageType === "heartbeat") continue;
        setEvents((current) => [...current, agentEvent]);
        if (agentEvent.messageType === "image") {
          const imageUrl = imageFromEvent(agentEvent);
          if (imageUrl) setOutputUrl(imageUrl);
        }
        if (agentEvent.messageType === "run_completed") {
          setRunning(false);
          setStatus("Edit completed. The final image has been archived to COS.");
          void loadWorkspace();
        } else if (agentEvent.messageType === "error") {
          setRunning(false);
          setError(stringValue(agentEvent.payload.message, "Image edit failed."));
        } else {
          setStatus(`${agentEvent.agent}: ${stringValue(agentEvent.payload.title, agentEvent.messageType)}`);
        }
      }
      if (done) break;
    }
  };

  const runEdit = async () => {
    if (!source || !instruction.trim() || !sessionId || running || uploading) return;
    setEvents([]);
    setOutputUrl("");
    setError("");
    setRunning(true);
    setStatus("Starting the image-edit Plan-Solve run.");
    const maskInstruction = mask
      ? " The first image is the source. The second image is a binary mask: edit white areas only and preserve black areas."
      : " The first image is the source image. Preserve all details not explicitly mentioned.";
    const prompt = `Edit the attached image. ${instruction.trim()}${maskInstruction}`;
    try {
      const response = await agentFetch(`${apiBaseUrl}/api/v2/agent/sessions/${encodeURIComponent(sessionId)}/runs`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
        body: JSON.stringify({
          prompt,
          mode: "plan-solve",
          imageUrls: mask ? [source.imageUrl, mask.imageUrl] : [source.imageUrl],
        }),
      });
      if (!response.ok) throw new Error(`Image edit request failed: ${response.status}`);
      await consume(response);
    } catch (runError) {
      setRunning(false);
      setError(runError instanceof Error ? runError.message : "Unable to connect to the image-edit service.");
      setStatus("Edit request failed.");
    }
  };

  const markDimensions = (url: string, image: HTMLImageElement) => {
    setDimensions((current) => ({ ...current, [url]: { width: image.naturalWidth, height: image.naturalHeight } }));
  };

  const history = workspaceAssets.filter((asset) => asset.source === "generated");
  const detail = selectedAsset ?? (outputUrl ? {
    assetId: "live-output",
    runId: "current",
    title: "Latest edit output",
    imageUrl: outputUrl,
    source: "generated" as const,
    occurredAt: new Date().toISOString(),
  } : undefined);

  return <main className={styles.page}>
    <header className={styles.header}>
      <div>
        <p>JENDA AGENT / IMAGE WORKBENCH</p>
        <h1>{copy.titleLead} <em>{copy.titleEm}</em></h1>
        <span>{copy.intro}</span>
      </div>
      <div className={styles.session}>
        <span>{copy.activeSession}</span>
        <strong>{sessionId || "initializing"}</strong>
        <Button icon={<ReloadOutlined />} onClick={() => void loadWorkspace()} disabled={!sessionId || running}>{copy.refreshAssets}</Button>
        <AgentAccountMenu />
      </div>
    </header>

    <section className={styles.workspace}>
      <aside className={styles.controlRail}>
        <div className={styles.railHeading}><PartitionOutlined /><span>{copy.inputs}</span></div>
        <label className={`${styles.dropzone} ${source ? styles.ready : ""}`}>
          <input type="file" accept="image/jpeg,image/png,image/webp,image/gif" onChange={(event) => upload("source", event)} disabled={Boolean(uploading) || running} />
          {uploading === "source" ? <LoadingOutlined /> : source ? <EyeOutlined /> : <UploadOutlined />}
          <b>01 / {copy.sourceImage}</b>
          <small>{source ? sourceName : copy.uploadSource}</small>
        </label>
        <label className={`${styles.dropzone} ${mask ? styles.ready : ""} ${!source ? styles.disabled : ""}`}>
          <input type="file" accept="image/jpeg,image/png,image/webp,image/gif" onChange={(event) => upload("mask", event)} disabled={!source || Boolean(uploading) || running} />
          {uploading === "mask" ? <LoadingOutlined /> : mask ? <FileImageOutlined /> : <PlusOutlined />}
          <b>02 / {copy.optionalMask}</b>
          <small>{mask ? maskName : copy.whiteEdit}</small>
        </label>
        <div className={styles.maskNote}><span>{copy.maskProtocol}</span><p>{copy.maskDetail}</p></div>
        <div className={styles.promptBox}>
          <label htmlFor="edit-instruction">{copy.editDirection}</label>
          <Input.TextArea id="edit-instruction" value={instruction} onChange={(event) => setInstruction(event.target.value)} autoSize={{ minRows: 5, maxRows: 8 }} disabled={running} />
          <Button type="primary" icon={running ? <LoadingOutlined /> : <RetweetOutlined />} onClick={runEdit} disabled={!source || !instruction.trim() || Boolean(uploading)} loading={running}>{copy.runEdit}</Button>
        </div>
        <div className={styles.status}><i className={running || uploading ? styles.pulse : ""} />{status}</div>
        {error && <div className={styles.error}>{error}</div>}
      </aside>

      <section className={styles.canvasPanel}>
        <div className={styles.canvasHeading}>
          <div><span>{copy.compareCanvas}</span><h2>{outputUrl ? copy.beforeAfter : copy.readyEdit}</h2></div>
          {outputUrl && <Tag color="green">{copy.archived}</Tag>}
        </div>
        {!source ? <div className={styles.emptyCanvas}><FileImageOutlined /><h2>{copy.startSource}</h2><p>{copy.startSourceDetail}</p></div> : outputUrl ? <>
          <div className={styles.compare}>
            <img src={source.imageUrl} alt="Original source" onLoad={(event) => markDimensions(source.imageUrl, event.currentTarget)} />
            <div className={styles.after} style={{ clipPath: `inset(0 ${100 - split}% 0 0)` }}>
              <img src={outputUrl} alt="Edited result" onLoad={(event) => markDimensions(outputUrl, event.currentTarget)} />
            </div>
            <div className={styles.splitLine} style={{ left: `${split}%` }}><i /></div>
            <span className={styles.beforeLabel}>{copy.original}</span><span className={styles.afterLabel}>{copy.edited}</span>
          </div>
          <div className={styles.compareControls}><span>{copy.original}</span><Slider value={split} onChange={setSplit} min={5} max={95} tooltip={{ formatter: (value) => `${value}%` }} /><span>{copy.edited}</span></div>
        </> : <div className={styles.awaiting}><LoadingOutlined spin={running} /><h2>{running ? copy.editing : copy.sourceReady}</h2><p>{running ? copy.editingDetail : copy.startSourceDetail}</p><img src={source.imageUrl} alt="Source preview" onLoad={(event) => markDimensions(source.imageUrl, event.currentTarget)} /></div>}
        <div className={styles.trace}><span>{copy.liveTrace}</span>{events.length === 0 ? <p>{copy.noEvents}</p> : events.slice(-6).map((event) => <article key={event.eventId}><Tag color={event.status === "failed" ? "red" : event.messageType === "image" ? "green" : "blue"}>{event.messageType}</Tag><b>{stringValue(event.payload.title, event.agent)}</b><small>{eventDetail(event)}</small></article>)}</div>
      </section>

      <aside className={styles.assetRail}>
        <div className={styles.railHeading}><SaveOutlined /><span>{copy.cosAssets}</span><b>{history.length}</b></div>
        <div className={styles.assetList}>{history.length === 0 ? <p className={styles.muted}>{copy.noAssets}</p> : history.map((asset) => <button key={asset.assetId} onClick={() => { setSelectedAsset(asset); setOutputUrl(asset.imageUrl); }} className={detail?.assetId === asset.assetId ? styles.assetSelected : ""}><img src={asset.imageUrl} alt={asset.title} onLoad={(event) => markDimensions(asset.imageUrl, event.currentTarget)} /><span><Tag color="magenta">{assetLabel(asset)}</Tag><strong>{asset.title}</strong></span></button>)}</div>
        {detail && <section className={styles.inspector}>
          <span>{copy.inspector}</span>
          <img src={detail.imageUrl} alt={detail.title} onLoad={(event) => markDimensions(detail.imageUrl, event.currentTarget)} />
          <strong>{detail.title}</strong>
          <dl><dt>{copy.storage}</dt><dd>COS signed URL</dd><dt>{copy.asset}</dt><dd>{detail.assetId}</dd><dt>{copy.resolution}</dt><dd>{dimensions[detail.imageUrl] ? `${dimensions[detail.imageUrl].width} x ${dimensions[detail.imageUrl].height}` : copy.loading}</dd><dt>{copy.run}</dt><dd>{detail.runId}</dd></dl>
          <div><Tooltip title="Open the signed COS asset in a new tab"><Button icon={<EyeOutlined />} onClick={() => window.open(detail.imageUrl, "_blank", "noopener,noreferrer")}>{copy.preview}</Button></Tooltip><Button type="primary" icon={<DownloadOutlined />} onClick={() => void downloadImage(detail.imageUrl, `${detail.assetId}.png`)}>{copy.download}</Button></div>
        </section>}
      </aside>
    </section>
  </main>;
}
