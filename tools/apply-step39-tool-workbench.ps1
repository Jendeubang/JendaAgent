$root = Split-Path -Parent $PSScriptRoot
$routeDir = Join-Path $root "showcase\app\zh\tool\[slug]"
$pagePath = Join-Path $routeDir "page.tsx"
$cssPath = Join-Path $routeDir "page.module.css"
$toolIndexPath = Join-Path $root "showcase\app\zh\tool\page.tsx"
$headerPath = Join-Path $root "showcase\components\CrispixHeader.tsx"

New-Item -ItemType Directory -Path $routeDir -Force | Out-Null

$pageSource = @'
"use client";

import { AppstoreOutlined, CheckCircleFilled, DeleteOutlined, DownloadOutlined, FileImageOutlined, InboxOutlined, PictureOutlined, ShoppingOutlined, ThunderboltOutlined } from "@ant-design/icons";
import { Button, Input } from "antd";
import { useParams } from "next/navigation";
import { ChangeEvent, CSSProperties, useEffect, useState } from "react";
import { CrispixHeader } from "../../../../components/CrispixHeader";
import styles from "./page.module.css";

type Field = { key: string; label: string; help: string; type: "text" | "number" | "select"; defaultValue: string; options?: string[] };
type ToolConfig = { slug: string; title: string; subtitle: string; accent: string; soft: string; icon: "upscale" | "enhance" | "layered" | "product" | "generic"; fields: Field[]; features: string[] };

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
  }
};

const genericTool: ToolConfig = { slug: "creative-studio", title: "Jenda \u56fe\u50cf\u5904\u7406", subtitle: "\u4e0a\u4f20\u56fe\u7247\uff0c\u8bbe\u7f6e\u5904\u7406\u53c2\u6570\uff0c\u8ba9 Jenda \u4ea4\u4ed8\u53ef\u4e0b\u8f7d\u7684\u56fe\u50cf\u7ed3\u679c", accent: "#2563eb", soft: "#dbeafe", icon: "generic", fields: [], features: ["\u4e00\u952e\u56fe\u50cf\u5904\u7406", "\u53ef\u89c6\u5316\u53c2\u6570\u9884\u89c8", "\u7ed3\u679c\u9884\u89c8\u4e0e\u4e0b\u8f7d"] };

function ToolGlyph({ icon }: { icon: ToolConfig["icon"] }) {
  if (icon === "upscale") return <PictureOutlined />;
  if (icon === "enhance") return <ThunderboltOutlined />;
  if (icon === "layered") return <AppstoreOutlined />;
  if (icon === "product") return <ShoppingOutlined />;
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
      <div><div className={styles.titleRow}><h1>{config.title}</h1><span>{copy.category}</span></div><p>{config.subtitle}</p></div>
    </section>
    <section className={styles.workspace}>
      <article className={styles.card}>
        <h2>{copy.settings}</h2>
        <div className={styles.form}>
          <label className={styles.label}><b>*</b>{copy.upload}</label>
          {file ? <div className={styles.fileRow}><img src={previewUrl} alt={file.name} /><span>{file.name}</span><button type="button" aria-label={copy.delete} onClick={clearFile}><DeleteOutlined /></button></div> : <label className={styles.uploadButton}><InboxOutlined />{copy.upload}<input type="file" accept="image/jpeg,image/png,image/webp" onChange={chooseFile} /></label>}
          <p className={styles.help}>{copy.uploadHint}</p>
          {config.fields.map((field) => <div className={styles.field} key={field.key}><label className={styles.label}><b>*</b>{field.label}</label>{field.type === "select" ? <select value={values[field.key] ?? ""} onChange={(event) => setValues({ ...values, [field.key]: event.target.value })}>{field.options?.map((option) => <option key={option}>{option}</option>)}</select> : <Input type={field.type} value={values[field.key] ?? ""} onChange={(event) => setValues({ ...values, [field.key]: event.target.value })} />}<p className={styles.help}>{field.help}</p></div>)}
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
'@

$cssSource = @'
.page{min-height:100vh;color:#1f2b42;background:linear-gradient(125deg,var(--tool-soft) 0%,#e6fbff 42%,#fff 82%);font-family:var(--font-sans,"Aptos","Microsoft YaHei",sans-serif)}.hero,.workspace,.featureCard{max-width:1220px;margin:auto}.hero{display:flex;align-items:center;gap:17px;padding:46px 30px 33px}.heroIcon{display:grid;flex:0 0 56px;width:56px;height:56px;place-items:center;color:var(--tool-accent);background:#ffffff63;border-radius:15px;font-size:29px}.titleRow{display:flex;align-items:center;gap:12px}.titleRow h1{margin:0;font-size:32px;letter-spacing:-.045em}.titleRow span{padding:4px 8px;color:var(--tool-accent);background:#ffffffa8;border-radius:5px;font-size:11px}.hero p{max-width:780px;margin:7px 0 0;color:#718097;font-size:14px;line-height:1.65}.workspace{display:grid;grid-template-columns:1fr 1fr;gap:24px;padding:0 30px 30px}.card,.featureCard{overflow:hidden;background:#fff;border:1px solid #edf1f6;border-radius:10px;box-shadow:0 10px 24px #1f3e6816}.card>h2,.featureCard>h2{padding:19px 23px;margin:0;border-bottom:1px solid #edf1f6;font-size:17px}.form{padding:25px 23px}.label{display:block;margin:0 0 9px;color:#34435a;font-size:14px;font-weight:600}.label b{margin-right:5px;color:#ff4d4f}.uploadButton{position:relative;display:flex;width:max-content;gap:8px;align-items:center;padding:10px 16px;color:#34435a;background:#fff;border:1px dashed #cad4e1;border-radius:7px;cursor:pointer;font-size:14px}.uploadButton:hover{color:var(--tool-accent);border-color:var(--tool-accent)}.uploadButton input{position:absolute;width:1px;height:1px;opacity:0}.help{margin:5px 0 17px;color:#9aa4b3;font-size:12px}.fileRow{display:flex;gap:10px;align-items:center;padding:9px;border:1px solid #dbe3ed;border-radius:9px}.fileRow img{width:48px;height:48px;object-fit:cover;border-radius:4px}.fileRow span{overflow:hidden;flex:1;color:var(--tool-accent);font-size:13px;text-overflow:ellipsis;white-space:nowrap}.fileRow button{color:#8792a1;background:transparent;border:0;cursor:pointer}.field{margin-top:15px}.field select{width:100%;height:38px;padding:0 11px;color:#34435a;background:#fff;border:1px solid #d9e0e9;border-radius:7px;outline:0}.field select:focus{border-color:var(--tool-accent)}.form :global(.ant-input){height:38px;border-radius:7px}.form :global(.ant-btn-primary){height:41px;margin-top:4px;background:var(--tool-accent);border-color:var(--tool-accent);box-shadow:0 6px 14px color-mix(in srgb,var(--tool-accent) 30%,transparent);font-size:15px}.jsonCard{margin-top:23px;border:1px solid #e7ebf0;border-radius:9px}.jsonCard h3{padding:12px 14px;margin:0;border-bottom:1px solid #edf1f6;font-size:14px}.jsonCard pre{min-height:68px;padding:13px;margin:0;overflow:auto;color:#526176;background:#f5f6f8;font-family:Consolas,monospace;font-size:11px;line-height:1.6;white-space:pre-wrap}.resultBody{display:flex;align-items:center;justify-content:center;min-height:564px;padding:20px}.empty{display:grid;gap:10px;place-items:center;color:#b0b8c4;font-size:13px}.empty :global(.anticon){color:#d3dae3;font-size:42px}.resultImage{max-width:100%;max-height:490px;border-radius:8px;object-fit:contain}.resultBody:has(.resultImage){display:block}.resultFooter{display:flex;gap:8px;align-items:center;margin-top:14px;padding:10px;background:#f8fafc;border-radius:8px}.resultFooter span{overflow:hidden;flex:1;color:#627087;font-size:12px;text-overflow:ellipsis;white-space:nowrap}.resultFooter span :global(.anticon){margin-right:5px;color:#22c55e}.resultFooter :global(.ant-btn-primary){background:var(--tool-accent);border-color:var(--tool-accent)}.featureCard{margin-bottom:58px}.featureCard ul{display:grid;grid-template-columns:1fr 1fr;gap:15px;margin:0;padding:22px 30px 26px;list-style:none}.featureCard li{position:relative;padding-left:14px;color:#607087;font-size:13px}.featureCard li:before{position:absolute;top:6px;left:0;width:6px;height:6px;background:var(--tool-accent);border-radius:50%;content:""}@media(max-width:760px){.hero{gap:13px;padding:38px 22px 28px}.heroIcon{flex-basis:52px;width:52px;height:52px;font-size:25px}.titleRow{align-items:flex-start;flex-wrap:wrap;gap:7px}.titleRow h1{font-size:30px}.hero p{font-size:14px}.workspace{grid-template-columns:1fr;gap:22px;padding:0 22px 26px}.card>h2{padding:20px 30px}.form{padding:30px}.resultBody{min-height:440px;padding:30px}.featureCard{margin:0 22px 40px}.featureCard h2{padding:20px 30px}.featureCard ul{grid-template-columns:1fr;padding:24px 30px}}@media(max-width:430px){.hero{align-items:flex-start}.heroIcon{flex-basis:48px;width:48px;height:48px}.titleRow h1{font-size:27px}.workspace{padding-inline:20px}.form{padding:28px 30px}.resultFooter{flex-wrap:wrap}.resultFooter span{min-width:100%}}
'@

$toolIndex = [System.IO.File]::ReadAllText($toolIndexPath)
$toolIndex = $toolIndex.Replace('const cards = [', 'const routes = ["image-upscale", "seedvr2", "image-layered", "product-refinement", "background-remove", "character-sheet", "figure-style", "floating-island", "commerce-poster", "commerce-detail"];' + [Environment]::NewLine + [Environment]::NewLine + 'const cards = [')
$toolIndex = $toolIndex.Replace('cards.map(([image, title, category, badge, description]) => <Link href="/image-studio"', 'cards.map(([image, title, category, badge, description], index) => <Link href={`/zh/tool/${routes[index]}`}')
if ($toolIndex -notmatch 'routes\[index\]') { throw "Tool index links were not updated." }

$header = [System.IO.File]::ReadAllText($headerPath)
$oldActive = 'className={pathname === href ? styles.active : ""}'
$newActive = 'className={pathname === href || (href === "/zh/tool" && pathname.startsWith("/zh/tool/")) ? styles.active : ""}'
if ($header.IndexOf($oldActive, [System.StringComparison]::Ordinal) -lt 0) { throw "Header active route logic was not found." }
$header = $header.Replace($oldActive, $newActive)

$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($pagePath, $pageSource, $utf8)
[System.IO.File]::WriteAllText($cssPath, $cssSource, $utf8)
[System.IO.File]::WriteAllText($toolIndexPath, $toolIndex, $utf8)
[System.IO.File]::WriteAllText($headerPath, $header, $utf8)
Write-Output "Applied Step 39 tool workbench pages and tool index routes."
