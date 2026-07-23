"use client";

import { CheckCircleFilled, CloudDownloadOutlined, HistoryOutlined, PlayCircleFilled, ReloadOutlined, RobotOutlined } from "@ant-design/icons";
import { Sender } from "@ant-design/x";
import { Badge, Button, Segmented, Tag } from "antd";
import { useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { addAsset, addMessage, reset, setMode, setStatus, type AppDispatch, type Asset, type Message, type RootState } from "../../store";
import styles from "./page.module.css";

type BackendEvent = {
  eventId: string;
  sessionId: string;
  messageType: string;
  status: string;
  agent: string;
  payload: Record<string, unknown>;
};

const apiBaseUrl = process.env.NEXT_PUBLIC_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const sessionId = "showcase-live-session";

function asText(value: unknown, fallback = "") {
  return typeof value === "string" ? value : fallback;
}

function toMessage(event: BackendEvent): Message | undefined {
  const kindMap: Record<string, Message["kind"]> = {
    plan: "plan",
    task: "task",
    tool_result: "tool",
    image: "image",
    summary: "summary",
  };
  const kind = kindMap[event.messageType];
  if (!kind) return undefined;
  const rawSteps = event.payload.steps;
  const steps = Array.isArray(rawSteps) ? rawSteps.filter((item): item is string => typeof item === "string") : undefined;
  return {
    id: event.eventId,
    kind,
    title: asText(event.payload.title, event.messageType),
    content: asText(event.payload.content),
    meta: `${event.agent} · ${event.status}`,
    steps,
  };
}

function phaseFor(type: string) {
  if (type === "plan") return "PlanningAgent 正在规划";
  if (type === "task" || type === "tool_result") return "ExecutorAgent 并发执行";
  if (type === "summary") return "SummaryAgent 汇总交付";
  return "事件流处理中";
}

function EventCard({ message }: { message: Message }) {
  if (message.kind === "user") return <article className={styles.user}><small>你</small><p>{message.content}</p></article>;
  return <article className={`${styles.event} ${styles[`event_${message.kind}`]}`}>
    <div className={styles.meta}><b>{message.kind.toUpperCase()}</b><span>{message.meta}</span></div>
    <h3>{message.title} <CheckCircleFilled /></h3>
    <p>{message.content}</p>
    {message.steps && <ol>{message.steps.map((step, index) => <li key={step}><span>{String(index + 1).padStart(2, "0")}</span>{step}</li>)}</ol>}
  </article>;
}

export default function LiveAgentPage() {
  const dispatch = useDispatch<AppDispatch>();
  const { mode, running, phase, assets } = useSelector((state: RootState) => state.studio);
  const [draft, setDraft] = useState("");
  const [imageUrl, setImageUrl] = useState("");
  const [messages, setMessages] = useState<Message[]>([]);
  const [error, setError] = useState("");

  const appendEvent = (event: BackendEvent) => {
    if (event.messageType === "heartbeat") return;
    if (event.messageType === "run_completed") {
      dispatch(setStatus({ running: false, phase: "任务完成" }));
      return;
    }
    if (event.messageType === "error") {
      setError(asText(event.payload.message, "智能体执行失败"));
      dispatch(setStatus({ running: false, phase: "执行失败" }));
      return;
    }
    const message = toMessage(event);
    if (!message) return;
    setMessages((current) => [...current, message]);
    dispatch(addMessage(message));
    dispatch(setStatus({ running: true, phase: phaseFor(event.messageType) }));
    if (event.messageType === "image") {
      const asset: Asset = {
        id: asText(event.payload.assetId, event.eventId),
        name: asText(event.payload.title, "生成图片"),
        detail: asText(event.payload.imageUrl, "等待 COS 图片地址"),
        tone: "coral",
      };
      dispatch(addAsset(asset));
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
        if (eventName === "agent-event" && data) appendEvent(JSON.parse(data) as BackendEvent);
      }
      if (done) break;
    }
  };

  const run = async (raw?: string) => {
    const prompt = (raw ?? draft).trim();
    if (!prompt || running) return;
    setDraft("");
    setError("");
    setMessages([{ id: `user-${Date.now()}`, kind: "user", title: "任务", content: prompt }]);
    dispatch(reset());
    dispatch(addMessage({ id: `user-${Date.now()}`, kind: "user", title: "任务", content: prompt }));
    dispatch(setStatus({ running: true, phase: "正在连接 SSE" }));
    try {
      const response = await fetch(`${apiBaseUrl}/api/v1/agent/sessions/${sessionId}/runs`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
        body: JSON.stringify({ prompt, mode, imageUrls: imageUrl ? [imageUrl] : [] }),
      });
      if (!response.ok) throw new Error(`SSE 请求失败：${response.status}`);
      await consumeStream(response);
    } catch (streamError) {
      setError(streamError instanceof Error ? streamError.message : "无法连接后端 SSE 服务");
      dispatch(setStatus({ running: false, phase: "连接失败" }));
    }
  };

  const replay = async () => {
    setError("");
    try {
      const response = await fetch(`${apiBaseUrl}/api/v1/agent/sessions/${sessionId}/events`);
      if (!response.ok) throw new Error(`历史回放失败：${response.status}`);
      const events = await response.json() as BackendEvent[];
      const replayMessages = events.map(toMessage).filter((message): message is Message => Boolean(message));
      setMessages(replayMessages);
      dispatch(setStatus({ running: false, phase: `已回放 ${replayMessages.length} 条事件` }));
    } catch (replayError) {
      setError(replayError instanceof Error ? replayError.message : "无法加载会话历史");
    }
  };

  return <main className={styles.page}>
    <header className={styles.header}><div><p>REAL SSE + HISTORY REPLAY</p><h1>Jenda Agent <em>Live</em></h1><span>统一事件协议已连接后端，所有业务事件先持久化后推送。</span></div><div className={styles.actions}><Segmented value={mode} onChange={(value) => dispatch(setMode(value as "plan-solve" | "react"))} options={[{ label: "Plan-Solve", value: "plan-solve" }, { label: "ReAct", value: "react" }]} /><Button icon={<HistoryOutlined />} onClick={replay}>回放历史</Button></div></header>
    <section className={styles.grid}>
      <section className={styles.thread}><div className={styles.status}><Badge status={running ? "processing" : "success"} text={phase} /><span>{apiBaseUrl}</span></div>{messages.length === 0 ? <div className={styles.empty}><RobotOutlined /><h2>连接真实智能体事件流</h2><p>提交任务后，前端将解析 `agent-event` SSE 帧；刷新页面后可通过回放接口重新加载已保存过程。</p></div> : messages.map((message) => <EventCard key={message.id} message={message} />)}{error && <div className={styles.error}>{error}</div>}<div className={styles.composer}><input value={imageUrl} onChange={(event) => setImageUrl(event.target.value)} placeholder="可选：参考图片 URL（image_url）" /><Sender value={draft} onChange={setDraft} onSubmit={run} loading={running} placeholder="描述要交付的智能体任务..." suffix={<Button type="primary" shape="circle" icon={<PlayCircleFilled />} onClick={() => run()} />} /></div></section>
      <aside className={styles.workspace}><div><p>WORKSPACE</p><h2>已持久化产物</h2></div><Tag color="cyan">{assets.length} assets</Tag>{assets.map((asset) => <article key={asset.id} className={styles.asset}><span className={`${styles.art} ${styles[`art_${asset.tone}`]}`}><i /><b /></span><div><strong>{asset.name}</strong><small>{asset.detail}</small></div><CloudDownloadOutlined /></article>)}<div className={styles.note}><RobotOutlined /><span><strong>回放规则</strong>通用事件表保存顺序；专用子表保存 plan、task、tool_result、image、summary 载荷。</span></div></aside>
    </section>
  </main>;
}
