"use client";

import { CloudUploadOutlined, LoadingOutlined, PlayCircleFilled, SafetyCertificateOutlined } from "@ant-design/icons";
import { Button, Segmented, Tag } from "antd";
import { Sender } from "@ant-design/x";
import { ChangeEvent, useState } from "react";
import { uploadImageDirect, type DirectUploadedAsset } from "../../lib/cosDirectUpload";
import styles from "./page.module.css";

type Mode = "plan-solve" | "react";

type BackendEvent = {
  eventId: string;
  messageType: string;
  status: string;
  agent: string;
  payload: Record<string, unknown>;
};

const apiBaseUrl = process.env.NEXT_PUBLIC_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const sessionId = "showcase-live-multimodal-session";

function textOf(value: unknown, fallback = "") {
  return typeof value === "string" ? value : fallback;
}

function eventTitle(event: BackendEvent) {
  const titles: Record<string, string> = {
    run_started: "多模态上下文已附加",
    plan: "任务规划",
    task: "子任务执行",
    tool_result: "工具结果",
    image: "图像产物",
    summary: "交付汇总",
  };
  return titles[event.messageType] ?? event.messageType;
}

function eventContent(event: BackendEvent) {
  if (event.messageType === "run_started") {
    const images = Array.isArray(event.payload.imageUrls) ? event.payload.imageUrls.length : 0;
    return `已把 ${images} 张参考图作为 image_url 发送给智能体。`;
  }
  return textOf(event.payload.content, textOf(event.payload.message, "等待智能体输出。"));
}

export default function LiveMultimodalPage() {
  const [mode, setMode] = useState<Mode>("plan-solve");
  const [prompt, setPrompt] = useState("");
  const [asset, setAsset] = useState<DirectUploadedAsset>();
  const [fileName, setFileName] = useState("");
  const [events, setEvents] = useState<BackendEvent[]>([]);
  const [uploading, setUploading] = useState(false);
  const [running, setRunning] = useState(false);
  const [phase, setPhase] = useState("等待任务");
  const [error, setError] = useState("");

  const upload = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file || uploading || running) return;
    setUploading(true);
    setError("");
    setPhase("正在申请 STS 临时凭证");
    try {
      const uploaded = await uploadImageDirect(apiBaseUrl, sessionId, file);
      setAsset(uploaded);
      setFileName(file.name);
      setPhase("参考图已进入本次会话");
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : "图片直传失败");
      setPhase("上传失败");
    } finally {
      setUploading(false);
    }
  };

  const consumeStream = async (response: Response) => {
    if (!response.body) throw new Error("浏览器不支持流式响应");
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
        const agentEvent = JSON.parse(data) as BackendEvent;
        if (agentEvent.messageType === "heartbeat") continue;
        setEvents((current) => [...current, agentEvent]);
        if (agentEvent.messageType === "run_completed") {
          setRunning(false);
          setPhase("任务完成");
        } else if (agentEvent.messageType === "error") {
          setRunning(false);
          setError(textOf(agentEvent.payload.message, "智能体执行失败"));
          setPhase("任务失败");
        } else {
          setPhase(`${agentEvent.agent}: ${eventTitle(agentEvent)}`);
        }
      }
      if (done) break;
    }
  };

  const run = async () => {
    const task = prompt.trim();
    if (!task || running || uploading) return;
    setError("");
    setEvents([]);
    setRunning(true);
    setPhase("正在连接 SSE");
    try {
      const response = await fetch(`${apiBaseUrl}/api/v1/agent/sessions/${sessionId}/runs`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
        body: JSON.stringify({ prompt: task, mode, imageUrls: asset ? [asset.imageUrl] : [] }),
      });
      if (!response.ok) throw new Error(`智能体请求失败: ${response.status}`);
      await consumeStream(response);
    } catch (runError) {
      setRunning(false);
      setError(runError instanceof Error ? runError.message : "无法连接智能体 SSE 服务");
      setPhase("连接失败");
    }
  };

  return <main className={styles.page}>
    <header className={styles.header}>
      <div>
        <p>LIVE MULTIMODAL AGENT</p>
        <h1>Bring the <em>reference</em> into the run.</h1>
        <span>图片通过短期 STS 凭证直传 COS，签名 URL 会作为 image_url 进入 PlanningAgent、ExecutorAgent 与 SummaryAgent。</span>
      </div>
      <Segmented value={mode} onChange={(value) => setMode(value as Mode)} options={[
        { label: "Plan-Solve", value: "plan-solve" },
        { label: "ReAct", value: "react" },
      ]} />
    </header>

    <section className={styles.grid}>
      <section className={styles.thread}>
        <div className={styles.status}><i className={running || uploading ? styles.pulse : ""} /><b>{phase}</b><span>{apiBaseUrl}</span></div>
        {events.length === 0 ? <div className={styles.empty}><SafetyCertificateOutlined /><h2>等待一次图文任务</h2><p>选择图片后直接上传到 COS，再提交任务。没有图片时，也可以直接发起纯文本智能体任务。</p></div> : events.filter((event) => event.messageType !== "run_completed").map((event) => <article key={event.eventId} className={`${styles.event} ${styles[`event_${event.messageType}`] ?? ""}`}>
          <div><Tag>{event.messageType}</Tag><small>{event.agent} / {event.status}</small></div>
          <h2>{textOf(event.payload.title, eventTitle(event))}</h2>
          <p>{eventContent(event)}</p>
        </article>)}
        {error && <div className={styles.error}>{error}</div>}
        <div className={styles.composer}>
          <label className={styles.uploadButton}>
            <input type="file" accept="image/jpeg,image/png,image/webp,image/gif" onChange={upload} disabled={uploading || running} />
            {uploading ? <LoadingOutlined /> : <CloudUploadOutlined />} {uploading ? "上传中" : asset ? "替换参考图" : "添加参考图"}
          </label>
          <Sender value={prompt} onChange={setPrompt} onSubmit={run} loading={running} placeholder="描述要交付的图文任务..." suffix={<Button type="primary" shape="circle" icon={<PlayCircleFilled />} onClick={run} />} />
        </div>
      </section>

      <aside className={styles.workspace}>
        <p>WORKSPACE / CONTEXT</p>
        <h2>本次参考资产</h2>
        {asset ? <article className={styles.asset}>
          <img src={asset.imageUrl} alt={fileName || "reference"} />
          <strong>{fileName}</strong>
          <small>{asset.assetId}</small>
          <Tag color="green">COS STS</Tag>
          <a href={asset.imageUrl} target="_blank">预览签名 URL</a>
        </article> : <div className={styles.noAsset}>尚未附加图片。上传完成后，签名 image_url 会出现在这里并随本次 SSE 请求发送。</div>}
        <div className={styles.note}><b>安全边界</b><span>浏览器不会获得长期 SecretKey；临时凭证只允许写入一个对象，后端再用 HeadObject 复核。</span></div>
      </aside>
    </section>
  </main>;
}
