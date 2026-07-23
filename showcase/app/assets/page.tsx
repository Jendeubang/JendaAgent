"use client";

import { DeleteOutlined, DownloadOutlined, FilterOutlined, FolderOpenOutlined, PictureOutlined, ReloadOutlined } from "@ant-design/icons";
import { Button, Empty, Modal, Pagination, Select, Spin, Tag, message } from "antd";
import { useEffect, useState } from "react";
import { AgentAccountMenu } from "../../components/AgentAccountMenu";
import { agentFetch } from "../../lib/agentAuth";
import styles from "./page.module.css";

type Asset = {
  assetId: string;
  ownerUserId: string;
  sessionId: string;
  runId?: string;
  fileName?: string;
  mediaType?: string;
  size: number;
  objectKey?: string;
  imageUrl: string;
  source: "upload" | "generated";
  createdAt: string;
};

type AssetPage = { items: Asset[]; total: number; page: number; size: number };
type Session = { sessionId: string; latestPrompt: string };

const apiBaseUrl = process.env.NEXT_PUBLIC_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const pageSize = 12;

function time(value: string) { return new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" }).format(new Date(value)); }
function bytes(value: number) { return value > 0 ? `${(value / 1024 / 1024).toFixed(value > 1024 * 1024 ? 1 : 2)} MB` : "模型产物"; }

export default function AssetsPage() {
  const [assets, setAssets] = useState<AssetPage>({ items: [], total: 0, page: 1, size: pageSize });
  const [sessions, setSessions] = useState<Session[]>([]);
  const [sessionId, setSessionId] = useState<string>();
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [removing, setRemoving] = useState("");

  const load = async () => {
    setLoading(true);
    try {
      const query = new URLSearchParams({ page: String(page), size: String(pageSize) });
      if (sessionId) query.set("sessionId", sessionId);
      const [assetResponse, sessionResponse] = await Promise.all([
        agentFetch(`${apiBaseUrl}/api/v1/agent/assets?${query}`),
        agentFetch(`${apiBaseUrl}/api/v1/agent/sessions?limit=100`),
      ]);
      if (!assetResponse.ok) throw new Error(`资产列表请求失败: ${assetResponse.status}`);
      setAssets(await assetResponse.json() as AssetPage);
      if (sessionResponse.ok) setSessions(await sessionResponse.json() as Session[]);
    } catch (error) {
      message.error(error instanceof Error ? error.message : "无法读取资产中心");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, [page, sessionId]);

  const remove = (asset: Asset) => {
    Modal.confirm({
      title: "删除这张资产？",
      content: "将同时删除 COS 中的图片文件和当前用户的资产记录，此操作不可恢复。",
      okText: "确认删除",
      okButtonProps: { danger: true },
      cancelText: "取消",
      onOk: async () => {
        setRemoving(asset.assetId);
        try {
          const response = await agentFetch(`${apiBaseUrl}/api/v1/agent/assets/${encodeURIComponent(asset.assetId)}`, { method: "DELETE" });
          if (!response.ok) throw new Error(`删除失败: ${response.status}`);
          message.success("资产已从 COS 和工作区删除");
          if (assets.items.length === 1 && page > 1) setPage((current) => current - 1);
          else await load();
        } finally {
          setRemoving("");
        }
      },
    });
  };

  return <main className={styles.page}>
    <header className={styles.header}>
      <div><p>JENDA AGENT / ASSET LIBRARY</p><h1>你的每一次创作，都有<em>归属。</em></h1><span>按账号隔离的 COS 图像资产，支持会话筛选、预览、下载与彻底删除。</span></div>
      <div className={styles.actions}><Button icon={<ReloadOutlined />} onClick={() => void load()} loading={loading}>刷新</Button><AgentAccountMenu /></div>
    </header>
    <section className={styles.toolbar}>
      <div><FilterOutlined /><b>会话筛选</b><Select allowClear value={sessionId} onChange={(value) => { setSessionId(value); setPage(1); }} placeholder="全部会话" options={sessions.map((session) => ({ value: session.sessionId, label: session.latestPrompt || session.sessionId }))} /></div>
      <span>{assets.total} 个当前账号资产</span>
    </section>
    {loading ? <div className={styles.loading}><Spin size="large" />正在读取资产...</div> : assets.items.length === 0 ? <div className={styles.empty}><PictureOutlined /><h2>这里还没有图片资产</h2><p>上传参考图或完成一次图像生成后，资产会自动归档到这里。</p></div> : <section className={styles.grid}>{assets.items.map((asset) => <article key={asset.assetId} className={styles.card}>
      <a href={asset.imageUrl} target="_blank"><img src={asset.imageUrl} alt={asset.fileName || asset.assetId} /></a>
      <div className={styles.cardBody}><div className={styles.cardTop}><Tag color={asset.source === "generated" ? "magenta" : "cyan"}>{asset.source === "generated" ? "工具产物" : "参考图"}</Tag><small>{time(asset.createdAt)}</small></div><h2>{asset.fileName || "未命名图像"}</h2><dl><div><dt>运行 ID</dt><dd>{asset.runId || "上传资产"}</dd></div><div><dt>COS 存储</dt><dd>{asset.objectKey || "已归档生成图"}</dd></div><div><dt>文件大小</dt><dd>{bytes(asset.size)}</dd></div></dl><div className={styles.cardActions}><a href={asset.imageUrl} target="_blank" download><DownloadOutlined /> 下载</a><Button danger type="text" icon={<DeleteOutlined />} loading={removing === asset.assetId} onClick={() => remove(asset)}>删除</Button></div></div>
    </article>)}</section>}
    {assets.total > pageSize && <footer className={styles.pagination}><Pagination current={page} pageSize={pageSize} total={assets.total} showSizeChanger={false} onChange={setPage} /></footer>}
  </main>;
}
