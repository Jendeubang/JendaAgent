"use client";

import {
  AppstoreOutlined,
  ArrowUpOutlined,
  CheckCircleFilled,
  ClearOutlined,
  CloudUploadOutlined,
  DownloadOutlined,
  FileImageOutlined,
  FileTextOutlined,
  MoreOutlined,
  PaperClipOutlined,
  PlayCircleFilled,
  RobotOutlined,
  ThunderboltOutlined,
} from "@ant-design/icons";
import { Conversations, Sender } from "@ant-design/x";
import { Badge, Button, Divider, Segmented, Tag, Tooltip } from "antd";
import { useRef, useState } from "react";
import { useDispatch, useSelector } from "react-redux";
import { streamDemoRun } from "../lib/demo-agent";
import type { RootState } from "../store";
import { clearRun, setMode, type AgentMessage, type WorkspaceAsset } from "../store/session-slice";

const conversations = [
  { key: "active", label: "新品视觉方案", group: "今天", icon: <ThunderboltOutlined /> },
  { key: "history-1", label: "夏日活动海报", group: "昨天", icon: <FileImageOutlined /> },
  { key: "history-2", label: "产品 OCR 整理", group: "本周", icon: <FileTextOutlined /> },
];

const phaseLabel = {
  idle: "等待任务",
  planning: "规划中",
  executing: "并发执行中",
  summarizing: "汇总交付中",
};

function MessageCard({ message, onOpenAsset }: { message: AgentMessage; onOpenAsset: (id?: string) => void }) {
  if (message.type === "user") {
    return (
      <article className="user-message">
        <span>你</span>
        <p>{message.content}</p>
        <time>{message.timestamp}</time>
      </article>
    );
  }

  const typeLabel = {
    plan: "PLAN",
    task: "TASK",
    tool_result: "TOOL RESULT",
    image: "IMAGE",
    summary: "SUMMARY",
  }[message.type];

  return (
    <article className={`agent-card agent-card--${message.type}`}>
      <div className="agent-card__meta">
        <span className="agent-card__type">{typeLabel}</span>
        <span>{message.timestamp}</span>
      </div>
      <div className="agent-card__title-row">
        <h3>{message.title}</h3>
        {message.status === "complete" && <CheckCircleFilled className="complete-icon" />}
      </div>
      <p>{message.content}</p>
      {message.steps && (
        <ol className="plan-steps">
          {message.steps.map((step, index) => (
            <li key={step}>
              <span>{String(index + 1).padStart(2, "0")}</span>
              {step}
            </li>
          ))}
        </ol>
      )}
      {message.tool && <Tag className="tool-tag" icon={<RobotOutlined />}>{message.tool}</Tag>}
      {message.type === "image" && (
        <button className="artifact-card" onClick={() => onOpenAsset(message.artifactId)}>
          <span className="artifact-card__image"><i /><b /><em /></span>
          <span>
            <strong>jenda-creative-delivery.png</strong>
            <small>Image Studio · 1024 x 1024 · 已同步至 Workspace</small>
          </span>
          <ArrowUpOutlined className="artifact-card__arrow" />
        </button>
      )}
    </article>
  );
}

function AssetPreview({ asset, active, onClick }: { asset: WorkspaceAsset; active: boolean; onClick: () => void }) {
  return (
    <button className={`asset-item asset-item--${asset.accent} ${active ? "is-active" : ""}`} onClick={onClick}>
      <span className="asset-thumb"><i /><b /><em /></span>
      <span className="asset-item__copy">
        <strong>{asset.name}</strong>
        <small>{asset.detail}</small>
      </span>
      <MoreOutlined />
    </button>
  );
}

export function AgentWorkbench() {
  const dispatch = useDispatch();
  const { mode, messages, assets, activePhase, isRunning } = useSelector((state: RootState) => state.session);
  const [draft, setDraft] = useState("");
  const [activeConversation, setActiveConversation] = useState("active");
  const [uploadedName, setUploadedName] = useState<string>();
  const [activeAssetId, setActiveAssetId] = useState("starter-asset");
  const uploadRef = useRef<HTMLInputElement>(null);

  const submitTask = (value?: string) => {
    const task = (value ?? draft).trim();
    if (!task || isRunning) return;
    setDraft("");
    void streamDemoRun(dispatch, task, mode, uploadedName);
  };

  const activeAsset = assets.find((asset) => asset.id === activeAssetId) ?? assets[0];

  return (
    <main className="studio-shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark"><i /><b /></span>
          <span>JENDA <strong>AGENT</strong></span>
        </div>
        <Button className="new-task" icon={<AppstoreOutlined />} block>
          新建智能任务
        </Button>
        <div className="conversation-wrap">
          <Conversations
            items={conversations}
            activeKey={activeConversation}
            onActiveChange={(key) => setActiveConversation(key)}
            groupable={{ label: (group) => <span className="conversation-group">{group}</span> }}
          />
        </div>
        <div className="sidebar-footer">
          <div className="agent-health">
            <span className="pulse-dot" />
            <span><strong>3</strong> agents online</span>
          </div>
          <p>JoyAgent-JDGenie<br />secondary development</p>
        </div>
      </aside>

      <section className="conversation-panel">
        <header className="topbar">
          <div>
            <p className="eyebrow">MULTI-AGENT WORKSPACE</p>
            <h1>新品视觉方案 <Badge status={isRunning ? "processing" : "success"} text={phaseLabel[activePhase]} /></h1>
          </div>
          <div className="topbar-actions">
            <Segmented
              value={mode}
              onChange={(value) => dispatch(setMode(value as "plan-solve" | "react"))}
              options={[
                { label: "Plan-Solve", value: "plan-solve" },
                { label: "ReAct", value: "react" },
              ]}
            />
            <Tooltip title="清空本次演示事件">
              <Button type="text" icon={<ClearOutlined />} onClick={() => dispatch(clearRun())} />
            </Tooltip>
          </div>
        </header>

        <div className="mode-strip">
          <span className="mode-strip__mode">{mode === "plan-solve" ? "PLAN-SOLVE" : "REACT ENGINE"}</span>
          <span>{mode === "plan-solve" ? "PlanningAgent → ExecutorAgent → SummaryAgent" : "Think → Act → Observe → Iterate"}</span>
          <span className="mode-strip__stream"><i /> SSE stream connected</span>
        </div>

        <div className="message-stream">
          <div className="welcome-grid">
            <div>
              <p className="eyebrow">ORCHESTRATE, CREATE, DELIVER</p>
              <h2>把复杂需求交给<br /><em>多智能体协作。</em></h2>
              <p className="welcome-copy">规划、执行、重规划与交付物汇总都以可解释的事件流展现，支持图文混合输入和工作空间沉淀。</p>
            </div>
            <div className="capability-grid">
              <span>OCR</span><span>IMAGE EDIT</span><span>PROMPT OP</span><span>VISION</span>
            </div>
          </div>
          {messages.map((message) => <MessageCard key={message.id} message={message} onOpenAsset={setActiveAssetId} />)}
        </div>

        <div className="composer-area">
          <input
            ref={uploadRef}
            className="hidden-input"
            type="file"
            accept="image/*"
            onChange={(event) => setUploadedName(event.target.files?.[0]?.name)}
          />
          {uploadedName && (
            <div className="upload-chip">
              <FileImageOutlined /> <span>{uploadedName}</span>
              <button onClick={() => setUploadedName(undefined)}>×</button>
            </div>
          )}
          <Sender
            value={draft}
            onChange={setDraft}
            onSubmit={submitTask}
            loading={isRunning}
            placeholder="描述任务，例如：根据参考图生成一张小红书产品视觉海报..."
            autoSize={{ minRows: 2, maxRows: 5 }}
            prefix={
              <Tooltip title="上传图片作为多模态上下文">
                <Button type="text" icon={<PaperClipOutlined />} onClick={() => uploadRef.current?.click()} />
              </Tooltip>
            }
            footer={
              <div className="sender-footer">
                <span><CloudUploadOutlined /> 图片会以 image_url 注入 Agent 上下文</span>
                <span>Enter 发送 · Shift + Enter 换行</span>
              </div>
            }
            suffix={<Button type="primary" shape="circle" icon={<PlayCircleFilled />} onClick={() => submitTask()} />}
          />
        </div>
      </section>

      <aside className="workspace">
        <header className="workspace-header">
          <div>
            <p className="eyebrow">DELIVERABLES</p>
            <h2>Workspace</h2>
          </div>
          <Badge count={assets.length} color="#0f766e" />
        </header>
        <Divider />
        <section className="workspace-feature">
          <div className="workspace-canvas"><i /><b /><em /><span>J</span></div>
          <div className="workspace-feature__copy">
            <Tag color="cyan">{activeAsset?.kind === "image" ? "IMAGE ASSET" : "REPORT"}</Tag>
            <h3>{activeAsset?.name}</h3>
            <p>{activeAsset?.detail}</p>
            <div>
              <Button icon={<DownloadOutlined />}>下载</Button>
              <Button type="text">详情</Button>
            </div>
          </div>
        </section>
        <section className="asset-list">
          <div className="asset-list__title"><span>本次会话产物</span><span>{assets.length}</span></div>
          {assets.map((asset) => (
            <AssetPreview key={asset.id} asset={asset} active={asset.id === activeAssetId} onClick={() => setActiveAssetId(asset.id)} />
          ))}
        </section>
        <div className="workspace-note">
          <RobotOutlined />
          <p><strong>回放准备就绪</strong>按 messageType 分表存储后，可重建完整任务过程。</p>
        </div>
      </aside>
    </main>
  );
}
