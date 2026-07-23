"use client";

import { AppstoreOutlined, CheckCircleFilled, DeleteOutlined, DownloadOutlined, FileImageOutlined, IdcardOutlined, InboxOutlined, PictureOutlined, ProfileOutlined, ShoppingOutlined, SmileOutlined, ThunderboltOutlined } from "@ant-design/icons";
import { Button, Input } from "antd";
import { useParams } from "next/navigation";
import { ChangeEvent, CSSProperties, useEffect, useState } from "react";
import { CrispixHeader } from "../../../../components/CrispixHeader";
import styles from "./page.module.css";

type Field = { key: string; label: string; help: string; type: "text" | "textarea" | "number" | "select"; defaultValue: string; options?: string[]; required?: boolean };
type ToolConfig = { slug: string; title: string; subtitle: string; accent: string; soft: string; icon: "upscale" | "enhance" | "layered" | "product" | "character" | "poster" | "emoji" | "detail" | "generic"; fields: Field[]; features: string[] };

const copy = {
  category: "\u56fe\u50cf\u5904\u7406",
  settings: "\u53c2\u6570\u8bbe\u7f6e",
  result: "\u751f\u6210\u7ed3\u679c",
  upload: "\u4e0a\u4f20\u56fe\u7247",
  uploadHint: "\u652f\u6301 JPG\u3001PNG\u3001WEBP \u683c\u5f0f\uff0c\u6700\u5927 10MB",
  process: "\u5f00\u59cb\u5904\u7406",
  processing: "\u6b63\u5728\u5904\u7406...",
  preview: "\u8868\u5355\u503c\uff08\u5b9e\u65f6\u9884\u89c8\uff09",
  noResult: "\u6682\u65e0\u5904\u7406\u7ed3\u679c",
  download: "\u4e0b\u8f7d",
  imageInfo: "\u56fe\u7247\u4fe1\u606f",
  outputReady: "\u5904\u7406\u5df2\u5b8c\u6210",
  required: "\u5fc5\u586b",
  delete: "\u79fb\u9664",
};

const tools: Record<string, ToolConfig> = {
  "image-upscale": {
    slug: "image-upscale", title: "\u56fe\u50cf\u8d85\u5206", subtitle: "AI\u8d85\u5206\u8fa8\u7387\u6280\u672f\uff0c\u4e00\u952e\u63d0\u5347\u56fe\u50cf\u6e05\u6670\u5ea6\uff0c\u8fd8\u539f\u7ec6\u8282\uff0c\u8ba9\u6a21\u7cca\u7167\u7247\u7115\u7136\u4e00\u65b0", accent: "#2563eb", soft: "#dbeafe", icon: "upscale", fields: [],
    features: ["2x\u8d85\u5206\u8fa8\u7387\u653e\u5927", "\u667a\u80fd\u7ec6\u8282\u8fd8\u539f", "\u4fdd\u6301\u539f\u59cb\u8272\u5f69\u548c\u98ce\u683c"]
  },
  seedvr2: {
    slug: "seedvr2", title: "\u8d85\u6e05\u589e\u5f3a", subtitle: "\u6bd4\u56fe\u50cf\u8d85\u5206\u66f4\u8fdb\u4e00\u6b65\uff08SeedVR2\uff09\uff1a\u6e05\u6670\u5ea6\u589e\u5f3a\u3001\u964d\u566a\u3001\u53bb\u4f2a\u5f71\uff0c\u652f\u63012K/4K/8K", accent: "#2563eb", soft: "#dbeafe", icon: "enhance",
    fields: [{ key: "resolution", label: "\u76ee\u6807\u5206\u8fa8\u7387", help: "\u53ef\u9009 2K\u30014K\u30018K", type: "select", defaultValue: "4K", options: ["2K", "4K", "8K"] }, { key: "outputFormat", label: "\u8f93\u51fa\u683c\u5f0f", help: "\u53ef\u9009 JPEG\u3001WEBP\u3001PNG", type: "select", defaultValue: "PNG", options: ["PNG", "JPEG", "WEBP"] }],
    features: ["2K/4K/8K \u591a\u6863\u5206\u8fa8\u7387", "\u667a\u80fd\u964d\u566a\u4e0e\u53bb\u4f2a\u5f71", "\u7eb9\u7406\u7ec6\u8282\u589e\u5f3a"]
  },
  "image-layered": {
    slug: "image-layered", title: "\u56fe\u50cf\u5206\u5c42", subtitle: "\u667a\u80fd\u8bed\u4e49\u5206\u5272\uff0c\u81ea\u52a8\u5206\u79bb\u4e3b\u4f53\u4e0e\u80cc\u666f\uff0c\u652f\u6301\u7cbe\u7ec6\u7f16\u8f91\u548c\u56fe\u5c42\u7ba1\u7406", accent: "#16a34a", soft: "#dcfce7", icon: "layered",
    fields: [{ key: "description", label: "\u63cf\u8ff0", help: "\u5bf9\u56fe\u50cf\u7684\u63cf\u8ff0\uff0c\u5982\u65e0\u9700\u63cf\u8ff0\u9ed8\u8ba4auto\u5373\u53ef", type: "text", defaultValue: "auto" }, { key: "numLayers", label: "\u56fe\u5c42\u6570\u91cf", help: "\u56fe\u5c42\u6570\u91cf\uff0c\u8303\u56f41-10\uff0c\u9ed8\u8ba44", type: "number", defaultValue: "4" }, { key: "outputFormat", label: "\u8f93\u51fa\u683c\u5f0f", help: "\u56fe\u50cf\u8f93\u51fa\u683c\u5f0f\u652f\u6301PNG\u3001JPG\u3001WEBP\u683c\u5f0f", type: "select", defaultValue: "JPG", options: ["JPG", "PNG", "WEBP"] }],
    features: ["\u667a\u80fd\u4e3b\u4f53\u8bc6\u522b\uff0c\u7cbe\u51c6\u5206\u79bb", "\u7cbe\u786e\u8fb9\u7f18\u5904\u7406\uff0c\u65e0\u952f\u9f7f", "\u652f\u6301\u591a\u56fe\u5c42\u5bfc\u51fa\uff08PNG\u900f\u660e\u80cc\u666f\uff09", "\u80cc\u666f\u66ff\u6362\u529f\u80fd"]
  },
  "product-refinement": {
    slug: "product-refinement", title: "\u7535\u5546\u4ea7\u54c1\u7cbe\u4fee", subtitle: "\u751f\u6210\u7528\u6237\u4e0a\u4f20\u56fe\u7247\u4ea7\u54c1\u7684\u767d\u5e95\u6b63\u89c6\u56fe\uff0c\u8ba9\u4ea7\u54c1\u53d8\u5f97\u66f4\u6709\u8d28\u611f\u548c\u65b0\u9c9c", accent: "#f97316", soft: "#ffedd5", icon: "product",
    fields: [{ key: "size", label: "\u56fe\u50cf\u5206\u8fa8\u7387", help: "\u9009\u62e9\u56fe\u50cf\u5206\u8fa8\u7387\uff0c\u53ef\u90092K\u62164K", type: "select", defaultValue: "2K", options: ["2K", "4K"] }, { key: "aspectRatio", label: "\u5bbd\u9ad8\u6bd4", help: "\u9009\u62e9\u8f93\u51fa\u56fe\u50cf\u7684\u957f\u5bbd\u6bd4\uff0c\u9ed8\u8ba4\u5339\u914d\u539f\u56fe", type: "select", defaultValue: "9:16 (\u7ad6\u5c4f/\u4eba\u50cf)", options: ["\u539f\u56fe\u6bd4\u4f8b", "1:1 (\u65b9\u5f62)", "4:3 (\u6807\u51c6)", "9:16 (\u7ad6\u5c4f/\u4eba\u50cf)", "16:9 (\u6a2a\u5c4f)"] }, { key: "description", label: "\u989d\u5916\u63cf\u8ff0", help: "\u53ef\u9009\uff0c\u5bf9\u4ea7\u54c1\u8fdb\u884c\u63cf\u8ff0\u4ee5\u5e2e\u52a9\u6a21\u578b\u66f4\u597d\u5730\u8bc6\u522b\u548c\u5904\u7406", type: "text", defaultValue: "" }],
    features: ["\u667a\u80fd\u4ea7\u54c1\u8bc6\u522b\u4e0e\u63d0\u53d6", "\u81ea\u52a8\u751f\u6210\u6b63\u89c6\u56fe", "\u589e\u5f3a\u4ea7\u54c1\u8d28\u611f\u548c\u7ec6\u8282", "\u652f\u6301\u591a\u79cd\u5c3a\u5bf8\u9009\u62e9"]
  },
  "character-setting-sheet": {
    slug: "character-setting-sheet", title: "\u89d2\u8272\u8bbe\u5b9a\u56fe", subtitle: "\u5c06\u4e0a\u4f20\u7684\u52a8\u6f2b\u4eba\u7269\u7b49\u89d2\u8272\u751f\u6210\u89c4\u8303\u7684\u4eba\u7269\u8bbe\u5b9a\u56fe\uff0c\u4fbf\u4e8e\u8bbe\u5b9a\u7edf\u4e00\u4e0e\u4e8c\u6b21\u521b\u4f5c", accent: "#a855f7", soft: "#f3e8ff", icon: "character",
    fields: [{ key: "size", label: "\u5206\u8fa8\u7387", help: "\u9ed8\u8ba4 2K", type: "select", defaultValue: "2K", options: ["2K", "4K"] }, { key: "aspectRatio", label: "\u957f\u5bbd\u6bd4", help: "\u4e0d\u586b\u5219\u9ed8\u8ba4\u5339\u914d\u8f93\u5165\u56fe\u7247\u7684\u957f\u5bbd\u6bd4", type: "select", defaultValue: "9:16 (\u7ad6\u5c4f/\u4eba\u50cf)", options: ["\u539f\u56fe\u6bd4\u4f8b", "1:1 (\u65b9\u5f62)", "9:16 (\u7ad6\u5c4f/\u4eba\u50cf)", "16:9 (\u6a2a\u5c4f)"] }, { key: "outputFormat", label: "\u8f93\u51fa\u683c\u5f0f", help: "\u56fe\u50cf\u8f93\u51fa\u683c\u5f0f", type: "select", defaultValue: "PNG", options: ["PNG", "JPEG"] }, { key: "description", label: "\u989d\u5916\u63cf\u8ff0", help: "\u53ef\u9009\uff0c\u8865\u5145\u89d2\u8272\u7279\u5f81\u6709\u52a9\u4e8e\u751f\u6210\u66f4\u8d34\u5408\u7684\u4eba\u7269\u8bbe\u5b9a\u56fe", type: "textarea", defaultValue: "", required: false }],
    features: ["\u4e00\u952e\u751f\u6210\u89d2\u8272\u8bbe\u5b9a\u56fe", "\u652f\u6301\u591a\u79cd\u957f\u5bbd\u6bd4", "\u53ef\u8865\u5145\u63cf\u8ff0\u4f18\u5316\u6548\u679c"]
  },
  "ecommerce-promotion-poster": {
    slug: "ecommerce-promotion-poster", title: "\u7535\u5546\u5ba3\u4f20\u6d77\u62a5", subtitle: "\u4e0a\u4f20\u4ea7\u54c1\u56fe\uff0c\u4e00\u952e\u751f\u6210\u9ad8\u7aef\u5927\u6c14\u3001\u89c6\u89c9\u51b2\u51fb\u529b\u5f3a\u7684\u7535\u5546\u5ba3\u4f20\u6d77\u62a5", accent: "#f97316", soft: "#ffedd5", icon: "poster",
    fields: [{ key: "aspectRatio", label: "\u957f\u5bbd\u6bd4", help: "\u9009\u62e9\u6d77\u62a5\u5c3a\u5bf8\u6bd4\u4f8b", type: "select", defaultValue: "9:16 (\u7ad6\u5c4f/\u624b\u673a)", options: ["1:1 (\u65b9\u5f62)", "4:3 (\u6807\u51c6)", "9:16 (\u7ad6\u5c4f/\u624b\u673a)", "16:9 (\u6a2a\u5c4f)"] }, { key: "outputFormat", label: "\u8f93\u51fa\u683c\u5f0f", help: "\u56fe\u50cf\u8f93\u51fa\u683c\u5f0f\uff0c\u9ed8\u8ba4PNG", type: "select", defaultValue: "PNG", options: ["PNG", "JPEG"] }, { key: "description", label: "\u989d\u5916\u63cf\u8ff0", help: "\u8ffd\u52a0\u5230\u56fa\u5b9a\u63d0\u793a\u8bcd\u540e\uff0c\u5e2e\u52a9\u751f\u6210\u66f4\u7b26\u5408\u9700\u6c42\u7684\u6d77\u62a5", type: "textarea", defaultValue: "", required: false }],
    features: ["\u4e00\u952e\u751f\u6210\u9ad8\u7aef\u7535\u5546\u5ba3\u4f20\u6d77\u62a5", "\u652f\u6301\u591a\u79cd\u957f\u5bbd\u6bd4\uff0c\u9002\u914d\u4e0d\u540c\u5e73\u53f0", "\u53ef\u8865\u5145\u989d\u5916\u63cf\u8ff0\u4f18\u5316\u751f\u6210\u6548\u679c"]
  },
  "emoji-sticker": {
    slug: "emoji-sticker", title: "\u4e00\u952e\u751f\u6210\u8868\u60c5\u5305", subtitle: "\u4e0a\u4f20\u4eba\u7269/\u89d2\u8272\u56fe\u7247\uff0c\u4e00\u952e\u751f\u6210\u4e00\u7ec4\u6709\u8da3\u7684\u8868\u60c5\u5305\uff0c\u9002\u5408\u793e\u4ea4\u5206\u4eab\u3001\u7fa4\u804a\u6597\u56fe", accent: "#ca8a04", soft: "#fef9c3", icon: "emoji",
    fields: [{ key: "aspectRatio", label: "\u957f\u5bbd\u6bd4", help: "\u8868\u60c5\u5305\u901a\u5e38\u4f7f\u752816:9 \u5bbd\u5c4f\u62169:16 \u7ad6\u5c4f", type: "select", defaultValue: "16:9 (\u5bbd\u5c4f)", options: ["1:1 (\u65b9\u5f62)", "16:9 (\u5bbd\u5c4f)", "9:16 (\u7ad6\u5c4f)"] }, { key: "outputFormat", label: "\u8f93\u51fa\u683c\u5f0f", help: "\u56fe\u50cf\u8f93\u51fa\u683c\u5f0f\uff0c\u9ed8\u8ba4PNG", type: "select", defaultValue: "PNG", options: ["PNG", "JPEG"] }],
    features: ["\u4e00\u952e\u751f\u6210\u591a\u4e2a\u6709\u8da3\u8868\u60c5", "\u9002\u5408\u5fae\u4fe1\u7fa4\u804a\u3001\u793e\u4ea4\u5206\u4eab", "\u652f\u6301\u771f\u4eba\u3001\u52a8\u6f2b\u3001\u5361\u901a\u7b49\u591a\u79cd\u98ce\u683c"]
  },
  "product-detail-image": {
    slug: "product-detail-image", title: "\u5546\u54c1\u8be6\u60c5\u56fe", subtitle: "\u4e0a\u4f20\u4ea7\u54c1\u56fe\uff0c\u751f\u6210\u9002\u7528\u4e8e\u7535\u5546\u8be6\u60c5\u9875\u7684\u7cbe\u7f8e\u5c55\u793a\u56fe\uff0c\u652f\u6301\u81ea\u5b9a\u4e49\u5185\u5bb9\u63cf\u8ff0", accent: "#f97316", soft: "#ffedd5", icon: "detail",
    fields: [{ key: "aspectRatio", label: "\u957f\u5bbd\u6bd4", help: "\u9009\u62e9\u8be6\u60c5\u56fe\u5c3a\u5bf8\u6bd4\u4f8b\uff0c\u9ed8\u8ba49:16", type: "select", defaultValue: "9:16 (\u7ad6\u5c4f\u957f\u56fe)", options: ["1:1 (\u65b9\u5f62)", "4:3 (\u6807\u51c6)", "9:16 (\u7ad6\u5c4f\u957f\u56fe)", "16:9 (\u6a2a\u5c4f)"] }, { key: "outputFormat", label: "\u8f93\u51fa\u683c\u5f0f", help: "\u56fe\u50cf\u8f93\u51fa\u683c\u5f0f\uff0c\u9ed8\u8ba4PNG", type: "select", defaultValue: "PNG", options: ["PNG", "JPEG"] }, { key: "description", label: "\u5185\u5bb9\u63cf\u8ff0", help: "\u53ef\u9009\u586b\uff0c\u7559\u7a7a\u65f6\u7531\u6a21\u578b\u81ea\u884c\u641c\u7d22\u5185\u5bb9\u8865\u5145", type: "textarea", defaultValue: "", required: false }],
    features: ["\u4e00\u952e\u751f\u6210\u7535\u5546\u8be6\u60c5\u9875\u5c55\u793a\u56fe", "\u652f\u6301\u81ea\u5b9a\u4e49\u5185\u5bb9\u63cf\u8ff0\uff0c\u7cbe\u51c6\u63a7\u5236\u751f\u6210\u6548\u679c", "\u591a\u79cd\u957f\u5bbd\u6bd4\u9002\u914d\u4e0d\u540c\u8be6\u60c5\u9875\u5e03\u5c40"]
  }
};

const genericTool: ToolConfig = { slug: "creative-studio", title: "Jenda \u56fe\u50cf\u5904\u7406", subtitle: "\u4e0a\u4f20\u56fe\u7247\uff0c\u8bbe\u7f6e\u5904\u7406\u53c2\u6570\uff0c\u8ba9 Jenda \u4ea4\u4ed8\u53ef\u4e0b\u8f7d\u7684\u56fe\u50cf\u7ed3\u679c", accent: "#2563eb", soft: "#dbeafe", icon: "generic", fields: [], features: ["\u4e00\u952e\u56fe\u50cf\u5904\u7406", "\u53ef\u89c6\u5316\u53c2\u6570\u9884\u89c8", "\u7ed3\u679c\u9884\u89c8\u4e0e\u4e0b\u8f7d"] };

function ToolGlyph({ icon }: { icon: ToolConfig["icon"] }) {
  if (icon === "upscale") return <PictureOutlined />;
  if (icon === "enhance") return <ThunderboltOutlined />;
  if (icon === "layered") return <AppstoreOutlined />;
  if (icon === "product") return <ShoppingOutlined />;
  if (icon === "character") return <IdcardOutlined />;
  if (icon === "poster") return <PictureOutlined />;
  if (icon === "emoji") return <SmileOutlined />;
  if (icon === "detail") return <ProfileOutlined />;
  return <FileImageOutlined />;
}

export default function ToolWorkbenchPage() {
  const params = useParams<{ slug: string }>();
  const slug = Array.isArray(params.slug) ? params.slug[0] : params.slug;
  const config = tools[slug] ?? genericTool;
  const [values, setValues] = useState<Record<string, string>>({});
  const [file, setFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState("");
  const [processing, setProcessing] = useState(false);
  const [completed, setCompleted] = useState(false);

  useEffect(() => {
    setValues(Object.fromEntries(config.fields.map((field) => [field.key, field.defaultValue])));
    setFile(null); setPreviewUrl(""); setProcessing(false); setCompleted(false);
  }, [config]);

  useEffect(() => () => { if (previewUrl) URL.revokeObjectURL(previewUrl); }, [previewUrl]);

  const formJson = JSON.stringify({ ...values, fileId: file?.name ?? undefined }, null, 2);
  const pageStyle = { "--tool-accent": config.accent, "--tool-soft": config.soft } as CSSProperties;
  const categoryLabel = config.slug === "character-setting-sheet" ? "\u98ce\u683c\u8f6c\u6362" : config.slug === "emoji-sticker" ? "\u521b\u610f\u751f\u6210" : config.slug.includes("ecommerce") || config.slug === "product-detail-image" ? "\u7535\u5546\u5de5\u5177" : copy.category;
  const uploadLabel = config.slug === "character-setting-sheet" ? "\u4e0a\u4f20\u89d2\u8272\u53c2\u8003\u56fe" : config.slug === "emoji-sticker" ? "\u4e0a\u4f20\u4eba\u7269/\u89d2\u8272\u56fe\u7247" : config.slug.includes("ecommerce") || config.slug === "product-detail-image" ? "\u4e0a\u4f20\u4ea7\u54c1\u56fe" : copy.upload;

  function chooseFile(event: ChangeEvent<HTMLInputElement>) {
    const selected = event.target.files?.[0];
    if (!selected) return;
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setFile(selected); setPreviewUrl(URL.createObjectURL(selected)); setCompleted(false);
  }

  function clearFile() {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setFile(null); setPreviewUrl(""); setCompleted(false);
  }

  function processImage() {
    if (!file) return;
    setProcessing(true); setCompleted(false);
    window.setTimeout(() => { setProcessing(false); setCompleted(true); }, 900);
  }

  function downloadImage() {
    if (!previewUrl || !file) return;
    const link = document.createElement("a");
    link.href = previewUrl; link.download = `jenda-${config.slug}-${file.name}`; link.click();
  }

  return <main className={styles.page} style={pageStyle}>
    <CrispixHeader />
    <section className={styles.hero}>
      <div className={styles.heroIcon}><ToolGlyph icon={config.icon} /></div>
      <div><div className={styles.titleRow}><h1>{config.title}</h1><span>{categoryLabel}</span></div><p>{config.subtitle}</p></div>
    </section>
    <section className={styles.workspace}>
      <article className={styles.card}>
        <h2>{copy.settings}</h2>
        <div className={styles.form}>
          <label className={styles.label}><b>*</b>{uploadLabel}</label>
          {file ? <div className={styles.fileRow}><img src={previewUrl} alt={file.name} /><span>{file.name}</span><button type="button" aria-label={copy.delete} onClick={clearFile}><DeleteOutlined /></button></div> : <label className={styles.uploadButton}><InboxOutlined />{uploadLabel}<input type="file" accept="image/jpeg,image/png,image/webp" onChange={chooseFile} /></label>}
          <p className={styles.help}>{copy.uploadHint}</p>
          {config.fields.map((field) => <div className={styles.field} key={field.key}><label className={styles.label}>{field.required === false ? null : <b>*</b>}{field.label}</label>{field.type === "select" ? <select value={values[field.key] ?? ""} onChange={(event) => setValues({ ...values, [field.key]: event.target.value })}>{field.options?.map((option) => <option key={option}>{option}</option>)}</select> : field.type === "textarea" ? <Input.TextArea value={values[field.key] ?? ""} autoSize={{ minRows: 3, maxRows: 5 }} onChange={(event) => setValues({ ...values, [field.key]: event.target.value })} /> : <Input type={field.type} value={values[field.key] ?? ""} onChange={(event) => setValues({ ...values, [field.key]: event.target.value })} />}<p className={styles.help}>{field.help}</p></div>)}
          <Button type="primary" block loading={processing} disabled={!file} onClick={processImage}>{processing ? copy.processing : copy.process}</Button>
          <section className={styles.jsonCard}><h3>{copy.preview}</h3><pre>{formJson || "{}"}</pre></section>
        </div>
      </article>
      <article className={styles.card}>
        <h2>{copy.result}</h2>
        <div className={styles.resultBody}>{completed && previewUrl ? <><img className={styles.resultImage} src={previewUrl} alt={copy.outputReady} /><div className={styles.resultFooter}><span><CheckCircleFilled /> jenda_{config.slug}_result</span><Button type="primary" size="small" icon={<DownloadOutlined />} onClick={downloadImage}>{copy.download}</Button><Button size="small">{copy.imageInfo}</Button></div></> : <div className={styles.empty}><FileImageOutlined /><span>{processing ? copy.processing : copy.noResult}</span></div>}</div>
      </article>
    </section>
    <section className={styles.featureCard}><h2>\u529f\u80fd\u8bf4\u660e</h2><ul>{config.features.map((feature) => <li key={feature}>{feature}</li>)}</ul></section>
  </main>;
}