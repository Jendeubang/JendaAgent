"use client";

import { CloudUploadOutlined, LoadingOutlined, PlayCircleFilled, RetweetOutlined, SafetyCertificateOutlined } from "@ant-design/icons";
import { Button, Tag } from "antd";
import { Sender } from "@ant-design/x";
import { ChangeEvent, useEffect, useState } from "react";
import { uploadImageDirect, type DirectUploadedAsset } from "../../lib/cosDirectUpload";
import styles from "./page.module.css";

type AgentEvent = { eventId: string; messageType: string; status: string; agent: string; payload: Record<string, unknown> };
const apiBaseUrl = process.env.NEXT_PUBLIC_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const storageKey = "jenda-dynamic-plan-solve-session";

function text(value: unknown, fallback = "") { return typeof value === "string" ? value : fallback; }
function roundOf(event: AgentEvent) { return typeof event.payload.round === "number" ? event.payload.round : 0; }
function title(event: AgentEvent) { return text(event.payload.title, event.messageType); }
function content(event: AgentEvent) { return text(event.payload.content, text(event.payload.message, "等待智能体输出。")); }

export default function PlanSolvePage() {
  const [sessionId, setSessionId] = useState("");
  const [prompt, setPrompt] = useState("");
  const [asset, setAsset] = useState<DirectUploadedAsset>();
  const [fileName, setFileName] = useState("");
  const [events, setEvents] = useState<AgentEvent[]>([]);
  const [uploading, setUploading] = useState(false);
  const [running, setRunning] = useState(false);
  const [phase, setPhase] = useState("正在准备动态闭环");
  const [error, setError] = useState("");

  useEffect(() => {
    const stored = window.localStorage.getItem(storageKey);
    const id = stored || `plan-solve-${crypto.randomUUID()}`;
    if (!stored) window.localStorage.setItem(storageKey, id);
    setSessionId(id);
    setPhase("动态 Plan-Solve 已就绪");
  }, []);

  const upload = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file || uploading || running || !sessionId) return;
    setUploading(true); setError(""); setPhase("正在申请 STS 临时凭证");
    try { const uploaded = await uploadImageDirect(apiBaseUrl, sessionId, file); setAsset(uploaded); setFileName(file.name); setPhase("参考图已进入动态任务上下文"); }
    catch (uploadError) { setError(uploadError instanceof Error ? uploadError.message : "图片上传失败"); setPhase("上传失败"); }
    finally { setUploading(false); }
  };

  const consume = async (response: Response) => {
    if (!response.body) throw new Error("浏览器不支持流式响应");
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
        if (agentEvent.messageType === "run_completed") { setRunning(false); setPhase("动态闭环完成"); }
        else if (agentEvent.messageType === "error") { setRunning(false); setError(text(agentEvent.payload.message, "动态任务失败")); setPhase("任务失败"); }
        else setPhase(`${agentEvent.agent}: ${title(agentEvent)}`);
      }
      if (done) break;
    }
  };

  const run = async () => {
    if (!prompt.trim() || running || uploading || !sessionId) return;
    setEvents([]); setError(""); setRunning(true); setPhase("正在启动第 1 轮规划");
    try {
      const response = await fetch(`${apiBaseUrl}/api/v2/agent/sessions/${sessionId}/runs`, {
        method: "POST", headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
        body: JSON.stringify({ prompt: prompt.trim(), mode: "plan-solve", imageUrls: asset ? [asset.imageUrl] : [] }),
      });
      if (!response.ok) throw new Error(`动态 Plan-Solve 请求失败: ${response.status}`);
      await consume(response);
    } catch (runError) { setRunning(false); setError(runError instanceof Error ? runError.message : "无法连接动态智能体服务"); setPhase("连接失败"); }
  };

  const roundEvents = events.filter((event) => event.messageType !== "run_completed");
  const rounds = [...new Set(roundEvents.map(roundOf).filter(Boolean))];
  return <main className={styles.page}>
    <header className={styles.header}><div><p>PLAN-SOLVE / DYNAMIC LOOP</p><h1>Plan. Solve. <em>Replan.</em></h1><span>每一轮共享执行记忆。只有真实调用后失败的工具才会进入下一轮，避免盲目重试。</span></div><div className={styles.loop}><b>MAX 3</b><span>planning rounds</span></div></header>
    <section className={styles.grid}><section className={styles.thread}><div className={styles.status}><i className={running || uploading ? styles.pulse : ""} /><b>{phase}</b><span>{sessionId}</span></div>{events.length === 0 ? <div className={styles.empty}><RetweetOutlined /><h2>等待一次动态任务</h2><p>提交任务后，先生成第 1 轮计划；若工具真实失败，PlanningAgent 将带着观察结果生成下一轮修订计划。</p></div> : <div className={styles.timeline}>{rounds.map((round) => <section key={round} className={styles.round}><div className={styles.roundLabel}>ROUND {round}<span>共享记忆驱动</span></div>{roundEvents.filter((event) => roundOf(event) === round).map((event) => <article key={event.eventId} className={`${styles.event} ${styles[`event_${event.messageType}`] ?? ""}`}><div><Tag>{event.messageType}</Tag><small>{event.agent} / {event.status}</small>{event.payload.replan === true && <Tag color="orange">重规划</Tag>}</div><h2>{title(event)}</h2><p>{content(event)}</p></article>)}</section>)}{roundEvents.filter((event) => roundOf(event) === 0).map((event) => <article key={event.eventId} className={`${styles.event} ${styles[`event_${event.messageType}`] ?? ""}`}><div><Tag>{event.messageType}</Tag><small>{event.agent} / {event.status}</small></div><h2>{title(event)}</h2><p>{content(event)}</p></article>)}</div>}{error && <div className={styles.error}>{error}</div>}<div className={styles.composer}><label className={styles.uploadButton}><input type="file" accept="image/jpeg,image/png,image/webp,image/gif" onChange={upload} disabled={uploading || running} />{uploading ? <LoadingOutlined /> : <CloudUploadOutlined />}{uploading ? "上传中" : asset ? `参考图: ${fileName}` : "添加参考图"}</label><Sender value={prompt} onChange={setPrompt} onSubmit={run} loading={running} placeholder="输入需要动态规划的复杂任务..." suffix={<Button type="primary" shape="circle" icon={<PlayCircleFilled />} onClick={run} />} /></div></section><aside className={styles.panel}><p>LOOP CONTRACT</p><h2>执行规则</h2><ol><li>PlanningAgent 生成本轮结构化步骤</li><li>ExecutorAgent 并发执行工具</li><li>ToolRouter 记录观察与产物</li><li>失败且已调用的工具进入重规划</li><li>达到结果或 3 轮上限后交付</li></ol><div className={styles.asset}>{asset ? <><img src={asset.imageUrl} alt={fileName} /><strong>{fileName}</strong><Tag color="cyan">共享 image_url</Tag></> : <><SafetyCertificateOutlined /><span>可选图片会被传入每一轮规划、执行和汇总。</span></>}</div></aside></section>
  </main>;
}
