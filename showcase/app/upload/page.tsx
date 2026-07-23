"use client";

import { CheckCircleFilled, CloudUploadOutlined, FileImageOutlined, PlayCircleFilled, WarningFilled } from "@ant-design/icons";
import { Sender } from "@ant-design/x";
import { Button, Segmented, Spin, Tag } from "antd";
import { ChangeEvent, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { setMode, setStatus, type AppDispatch, type Mode, type RootState } from "../../store";
import styles from "./page.module.css";

type UploadResponse = {
  assetId: string;
  fileName: string;
  imageUrl: string;
  mediaType: string;
  size: number;
  modelAccessible: boolean;
  storageProvider: string;
};

type BackendEvent = {
  eventId: string;
  messageType: string;
  status: string;
  agent: string;
  payload: Record<string, unknown>;
};

const apiBaseUrl = process.env.NEXT_PUBLIC_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const sessionId = "showcase-upload-session";

function textOf(value: unknown, fallback = "") {
  return typeof value === "string" ? value : fallback;
}

function eventLabel(event: BackendEvent) {
  const labels: Record<string, string> = {
    plan: "规划",
    task: "任务",
    tool_result: "工具",
    image: "图像",
    summary: "交付",
  };
  return labels[event.messageType] ?? event.messageType;
}

export default function UploadAgentPage() {
  const dispatch = useDispatch<AppDispatch>();
  const { mode, running, phase } = useSelector((state: RootState) => state.studio);
  const [prompt, setPrompt] = useState("");
  const [upload, setUpload] = useState<UploadResponse>();
  const [uploading, setUploading] = useState(false);
  const [events, setEvents] = useState<BackendEvent[]>([]);
  const [error, setError] = useState("");

  const onFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    setUploading(true);
    setError("");
    setUpload(undefined);
    try {
      const formData = new FormData();
      formData.append("file", file);
      const response = await fetch(`${apiBaseUrl}/api/v1/agent/media/images`, {
        method: "POST",
        body: formData,
      });
      if (!response.ok) throw new Error(`图片上传失败: ${response.status}`);
      setUpload(await response.json() as UploadResponse);
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : "无法连接图片上传服务");
    } finally {
      setUploading(false);
      event.target.value = "";
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
        const eventName = frame.split("\n").find((line) => line.startsWith("event:"))?.slice(6).trim();
        const data = frame.split("\n").filter((line) => line.startsWith("data:")).map((line) => line.slice(5).trim()).join("\n");
        if (eventName !== "agent-event" || !data) continue;
        const agentEvent = JSON.parse(data) as BackendEvent;
        if (agentEvent.messageType === "heartbeat") continue;
        setEvents((current) => [...current, agentEvent]);
        if (agentEvent.messageType === "run_completed") {
          dispatch(setStatus({ running: false, phase: "任务完成" }));
        } else if (agentEvent.messageType === "error") {
          dispatch(setStatus({ running: false, phase: "执行失败" }));
          setError(textOf(agentEvent.payload.message, "智能体执行失败"));
        } else {
          dispatch(setStatus({ running: true, phase: `${agentEvent.agent}: ${eventLabel(agentEvent)}` }));
        }
      }
      if (done) break;
    }
  };

  const run = async () => {
    const task = prompt.trim();
    if (!task || running) return;
    setError("");
    setEvents([]);
    dispatch(setStatus({ running: true, phase: "连接 SSE" }));
    try {
      const response = await fetch(`${apiBaseUrl}/api/v1/agent/sessions/${sessionId}/runs`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
        body: JSON.stringify({
          prompt: task,
          mode,
          imageUrls: upload ? [upload.imageUrl] : [],
        }),
      });
      if (!response.ok) throw new Error(`任务请求失败: ${response.status}`);
      await consumeStream(response);
    } catch (runError) {
      setError(runError instanceof Error ? runError.message : "无法连接智能体 SSE 服务");
      dispatch(setStatus({ running: false, phase: "连接失败" }));
    }
  };

  return <main className={styles.page}>
    <header className={styles.header}>
      <div>
        <p>MULTIMODAL INPUT PIPELINE</p>
        <h1>Bring an <em>image</em> into the plan.</h1>
        <span>上传后返回统一的 image_url，PlanningAgent 与 ExecutorAgent 都从同一任务上下文读取它。</span>
      </div>
      <Segmented value={mode} onChange={(value) => dispatch(setMode(value as Mode))} options={[
        { label: "Plan-Solve", value: "plan-solve" },
        { label: "ReAct", value: "react" },
      ]} />
    </header>

    <section className={styles.layout}>
      <section className={styles.uploadPanel}>
        <div className={styles.panelHeading}>
          <span>01 / REFERENCE ASSET</span>
          <h2>上传参考图片</h2>
        </div>
        <label className={styles.dropzone}>
          <input type="file" accept="image/jpeg,image/png,image/webp,image/gif" onChange={onFileChange} disabled={uploading || running} />
          {uploading ? <Spin size="large" /> : upload ? <img src={upload.imageUrl} alt={upload.fileName} /> : <CloudUploadOutlined />}
          <strong>{uploading ? "正在上传..." : upload ? upload.fileName : "选择一张图片"}</strong>
          <small>{upload ? `${upload.mediaType} · ${Math.ceil(upload.size / 1024)} KB` : "JPEG / PNG / WEBP / GIF，最大 10 MB"}</small>
        </label>

        {upload && <div className={styles.assetMeta}>
          <FileImageOutlined />
          <div><b>image_url 已写入任务上下文</b><span>{upload.imageUrl}</span></div>
          <Tag color={upload.modelAccessible ? "green" : "gold"}>{upload.modelAccessible ? "模型可访问" : "本地开发 URL"}</Tag>
        </div>}
        {upload && !upload.modelAccessible && <div className={styles.warning}><WarningFilled /> 本地 URL 仅用于前端预览。接入 COS 后，上传接口将返回可被外部模型读取的 HTTPS 地址。</div>}
      </section>

      <section className={styles.runPanel}>
        <div className={styles.panelHeading}>
          <span>02 / AGENT RUN</span>
          <h2>描述图文任务</h2>
        </div>
        <Sender value={prompt} onChange={setPrompt} onSubmit={run} loading={running} placeholder="例如：分析参考图的构图，并生成一张更适合电商首图的现代海报..." suffix={<Button type="primary" shape="circle" icon={<PlayCircleFilled />} onClick={run} />} />
        <div className={styles.statusLine}><i className={running ? styles.pulse : ""} />{phase}<span>{upload ? "image_url attached" : "text-only task"}</span></div>

        <div className={styles.events}>
          {events.length === 0 ? <div className={styles.empty}>等待任务事件。上传图片不是必需项，但有图片时会随请求体的 <code>imageUrls</code> 一起进入规划与执行阶段。</div> : events.map((item) => <article key={item.eventId} className={styles.event}>
            <span>{eventLabel(item)}</span>
            <div><b>{textOf(item.payload.title, item.agent)}</b><p>{textOf(item.payload.content, textOf(item.payload.message))}</p></div>
            <CheckCircleFilled />
          </article>)}
        </div>
        {error && <div className={styles.error}>{error}</div>}
      </section>
    </section>
  </main>;
}
