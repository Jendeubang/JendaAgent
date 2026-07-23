"use client";

import { CheckCircleFilled, SafetyCertificateOutlined, ThunderboltOutlined } from "@ant-design/icons";
import { Button, Spin, Tag } from "antd";
import { ChangeEvent, useState } from "react";
import styles from "./page.module.css";

type Ticket = {
  uploadId: string;
  uploadUrl: string;
  objectKey: string;
  expiresAt: number;
  credentials: { tmpSecretId: string; tmpSecretKey: string; token: string };
};

type Asset = {
  assetId: string;
  imageUrl: string;
  storageProvider: string;
  modelAccessible: boolean;
  size: number;
};

const apiBaseUrl = process.env.NEXT_PUBLIC_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";

function encode(value: string) {
  return encodeURIComponent(value).replace(/\*/g, "%2A").replace(/%7E/g, "~");
}

function hex(buffer: ArrayBuffer) {
  return Array.from(new Uint8Array(buffer)).map((item) => item.toString(16).padStart(2, "0")).join("");
}

async function hmacSha1(key: string, value: string) {
  const encoder = new TextEncoder();
  const cryptoKey = await crypto.subtle.importKey("raw", encoder.encode(key), { name: "HMAC", hash: "SHA-1" }, false, ["sign"]);
  return hex(await crypto.subtle.sign("HMAC", cryptoKey, encoder.encode(value)));
}

async function signCosPut(ticket: Ticket, contentType: string) {
  const url = new URL(ticket.uploadUrl);
  const now = Math.floor(Date.now() / 1000);
  const keyTime = String(now) + ";" + String(Math.min(ticket.expiresAt, now + 900));
  const headerList = "content-type;host;x-cos-security-token";
  const canonicalHeaders = "content-type=" + encode(contentType.toLowerCase())
    + "&host=" + encode(url.host.toLowerCase())
    + "&x-cos-security-token=" + encode(ticket.credentials.token);
  const httpString = "put\n/" + ticket.objectKey + "\n\n" + canonicalHeaders + "\n";
  const digest = await crypto.subtle.digest("SHA-1", new TextEncoder().encode(httpString));
  const stringToSign = "sha1\n" + keyTime + "\n" + hex(digest) + "\n";
  const signKey = await hmacSha1(ticket.credentials.tmpSecretKey, keyTime);
  const signature = await hmacSha1(signKey, stringToSign);
  return "q-sign-algorithm=sha1&q-ak=" + encode(ticket.credentials.tmpSecretId)
    + "&q-sign-time=" + keyTime
    + "&q-key-time=" + keyTime
    + "&q-header-list=" + headerList
    + "&q-url-param-list="
    + "&q-signature=" + signature;
}

export default function DirectUploadPage() {
  const [asset, setAsset] = useState<Asset>();
  const [status, setStatus] = useState("等待选择图片");
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState("");

  const upload = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    setUploading(true);
    setError("");
    setAsset(undefined);
    try {
      setStatus("申请单对象 STS 临时凭证");
      const ticketResponse = await fetch(apiBaseUrl + "/api/v1/agent/media/direct/tickets", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          sessionId: "showcase-direct-upload",
          fileName: file.name,
          mediaType: file.type,
          size: file.size,
        }),
      });
      if (!ticketResponse.ok) throw new Error("STS 凭证签发失败: " + ticketResponse.status);
      const ticket = await ticketResponse.json() as Ticket;

      setStatus("浏览器直传 COS，不经过 Spring Boot");
      const authorization = await signCosPut(ticket, file.type);
      const cosResponse = await fetch(ticket.uploadUrl, {
        method: "PUT",
        headers: {
          "Content-Type": file.type,
          "x-cos-security-token": ticket.credentials.token,
          Authorization: authorization,
        },
        body: file,
      });
      if (!cosResponse.ok) throw new Error("COS 直传失败: " + cosResponse.status);

      setStatus("后端校验对象并签发预览 URL");
      const completeResponse = await fetch(apiBaseUrl + "/api/v1/agent/media/direct/complete", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ uploadId: ticket.uploadId }),
      });
      if (!completeResponse.ok) throw new Error("上传完成校验失败: " + completeResponse.status);
      setAsset(await completeResponse.json() as Asset);
      setStatus("直传完成，私有对象可短时预览");
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : "直传失败");
      setStatus("上传未完成");
    } finally {
      setUploading(false);
    }
  };

  return <main className={styles.page}>
    <header>
      <p>STS DIRECT UPLOAD</p>
      <h1>Files take the <em>fast lane.</em></h1>
      <span>浏览器只拿到单对象、短时有效的临时凭证。文件直达 COS，后端不承载文件流量。</span>
    </header>
    <section className={styles.panel}>
      <div className={styles.flow}>
        <article><SafetyCertificateOutlined /><b>01</b><span>后端签发受限 STS</span></article>
        <i />
        <article><ThunderboltOutlined /><b>02</b><span>浏览器直传 COS</span></article>
        <i />
        <article><CheckCircleFilled /><b>03</b><span>签名 URL 预览</span></article>
      </div>
      <label className={styles.dropzone}>
        <input type="file" accept="image/jpeg,image/png,image/webp,image/gif" disabled={uploading} onChange={upload} />
        {uploading ? <Spin size="large" /> : <ThunderboltOutlined />}
        <strong>{uploading ? status : "选择图片并直传 COS"}</strong>
        <small>临时凭证只允许上传一个对象，默认 15 分钟过期</small>
      </label>
      <div className={styles.status}><i className={uploading ? styles.pulse : ""} />{status}</div>
      {asset && <article className={styles.asset}>
        <img src={asset.imageUrl} alt="COS direct upload preview" />
        <div><Tag color="green">COS STS</Tag><b>私有对象预览已签发</b><span>{Math.ceil(asset.size / 1024)} KB · {asset.modelAccessible ? "modelAccessible" : "not accessible"}</span></div>
        <Button href={asset.imageUrl} target="_blank">打开图片</Button>
      </article>}
      {error && <div className={styles.error}>{error}</div>}
    </section>
  </main>;
}
