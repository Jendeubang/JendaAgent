$root = Split-Path -Parent $PSScriptRoot
$indexPath = Join-Path $root "showcase\app\zh\tool\page.tsx"
$workbenchPath = Join-Path $root "showcase\app\zh\tool\[slug]\page.tsx"
$cssPath = Join-Path $root "showcase\app\zh\tool\[slug]\page.module.css"

$indexSource = @'
"use client";

import Link from "next/link";
import { ArrowRightOutlined, SearchOutlined } from "@ant-design/icons";
import { Input, Tag } from "antd";
import { useState } from "react";
import { CrispixHeader } from "../../../components/CrispixHeader";
import styles from "./page.module.css";

type Category = "all" | "processing" | "creative" | "commerce";
type ToolCard = { route: string; image: string; title: string; category: Exclude<Category, "all">; badge: string; description: string };

const copy = {
  title: "Jenda Tool", description: "\u4e00\u7ad9\u5f0f AI \u521b\u4f5c\u5de5\u5177\u96c6\u5408\uff0c\u6ee1\u8db3\u4f60\u7684\u5404\u79cd\u521b\u4f5c\u9700\u6c42\u3002", models: "\u63a5\u5165 NanoBanana 2\u3001NanoBanana Pro\u3001SeedDream 4.5 \u7b49\u9876\u7ea7\u56fe\u50cf\u6a21\u578b", all: "\u5168\u90e8", processing: "\u56fe\u50cf\u5904\u7406", creative: "\u521b\u610f\u751f\u6210", commerce: "\u7535\u5546\u5de5\u5177", search: "\u641c\u7d22\u5de5\u5177", detail: "\u70b9\u51fb\u67e5\u770b\u8be6\u60c5", empty: "\u6ca1\u6709\u5339\u914d\u7684\u5de5\u5177\uff0c\u8bf7\u5c1d\u8bd5\u5176\u4ed6\u5206\u7c7b\u6216\u641c\u7d22\u8bcd\u3002", hot: "\u70ed\u95e8", fresh: "\u65b0\u54c1"
};

const categoryLabels: Record<Category, string> = { all: copy.all, processing: copy.processing, creative: copy.creative, commerce: copy.commerce };
const cards: ToolCard[] = [
  { route: "image-upscale", image: "tool-01.webp", title: "\u56fe\u50cf\u8d85\u5206", category: "processing", badge: copy.hot, description: "AI \u8d85\u5206\u8fa8\u7387\u6280\u672f\uff0c\u4e00\u952e\u63d0\u5347\u56fe\u50cf\u6e05\u6670\u5ea6" },
  { route: "seedvr2", image: "tool-02.jpeg", title: "\u8d85\u6e05\u589e\u5f3a", category: "processing", badge: "\u4e13\u4e1a", description: "\u6e05\u6670\u5ea6\u589e\u5f3a\u3001\u964d\u566a\u3001\u53bb\u4f2a\u5f71\uff0c\u652f\u63012K/4K/8K" },
  { route: "image-layered", image: "tool-03.png", title: "\u56fe\u50cf\u5206\u5c42", category: "processing", badge: copy.fresh, description: "\u667a\u80fd\u8bed\u4e49\u5206\u5272\uff0c\u81ea\u52a8\u5206\u79bb\u4e3b\u4f53\u4e0e\u80cc\u666f" },
  { route: "product-refinement", image: "tool-04.png", title: "\u7535\u5546\u4ea7\u54c1\u7cbe\u4fee", category: "processing", badge: "\u4e13\u4e1a", description: "\u751f\u6210\u4ea7\u54c1\u767d\u5e95\u6b63\u89c6\u56fe\uff0c\u8ba9\u4ea7\u54c1\u66f4\u6709\u8d28\u611f" },
  { route: "character-setting-sheet", image: "tool-06.jpg", title: "\u89d2\u8272\u8bbe\u5b9a\u56fe", category: "creative", badge: copy.fresh, description: "\u5c06\u4eba\u7269\u751f\u6210\u89c4\u8303\u7684\u89d2\u8272\u8bbe\u5b9a\u56fe\uff0c\u4fbf\u4e8e\u7edf\u4e00\u4e0e\u4e8c\u6b21\u521b\u4f5c" },
  { route: "emoji-sticker", image: "tool-01.webp", title: "\u4e00\u952e\u751f\u6210\u8868\u60c5\u5305", category: "creative", badge: copy.hot, description: "\u4e0a\u4f20\u4eba\u7269\u6216\u89d2\u8272\u56fe\u7247\uff0c\u751f\u6210\u9002\u5408\u793e\u4ea4\u5206\u4eab\u7684\u8868\u60c5\u7ec4\u56fe" },
  { route: "ecommerce-promotion-poster", image: "tool-09.png", title: "\u7535\u5546\u5ba3\u4f20\u6d77\u62a5", category: "commerce", badge: "\u7535\u5546", description: "\u4e0a\u4f20\u4ea7\u54c1\u56fe\uff0c\u4e00\u952e\u751f\u6210\u9ad8\u7aef\u5927\u6c14\u7684\u7535\u5546\u6d77\u62a5" },
  { route: "product-detail-image", image: "tool-10.png", title: "\u5546\u54c1\u8be6\u60c5\u56fe", category: "commerce", badge: "\u7535\u5546", description: "\u751f\u6210\u9002\u7528\u4e8e\u7535\u5546\u8be6\u60c5\u9875\u7684\u7cbe\u7f8e\u5c55\u793a\u56fe" }
];

export default function ToolPage() {
  const [category, setCategory] = useState<Category>("all");
  const [query, setQuery] = useState("");
  const filtered = cards.filter((card) => (category === "all" || card.category === category) && `${card.title}${card.description}`.toLowerCase().includes(query.trim().toLowerCase()));
  return <main className={styles.page}><CrispixHeader /><section className={styles.intro}><p>JENDA TOOL</p><h1>{copy.title}</h1><span>{copy.description}</span><small>{copy.models}</small></section><div className={styles.filters}><div>{(Object.keys(categoryLabels) as Category[]).map((key) => <button key={key} type="button" onClick={() => setCategory(key)} className={category === key ? styles.chosen : ""}>{categoryLabels[key]} <b>{key === "all" ? cards.length : cards.filter((card) => card.category === key).length}</b></button>)}</div><Input value={query} onChange={(event) => setQuery(event.target.value)} prefix={<SearchOutlined />} placeholder={copy.search} allowClear /></div><section className={styles.grid}>{filtered.map((card) => <Link href={`/zh/tool/${card.route}`} className={styles.card} key={card.route}><div className={styles.imageWrap}><img src={`/crispix/${card.image}`} alt={card.title} /><Tag color={card.badge === copy.hot ? "blue" : card.badge === copy.fresh ? "green" : "default"}>{card.badge}</Tag></div><div className={styles.cardBody}><small>{categoryLabels[card.category]}</small><h2>{card.title}</h2><p>{card.description}</p><span>{copy.detail} <ArrowRightOutlined /></span></div></Link>)}</section>{filtered.length === 0 && <p className={styles.more}>{copy.empty}</p>}</main>;
}
'@

$source = [System.IO.File]::ReadAllText($workbenchPath)
$source = $source.Replace('import { AppstoreOutlined, CheckCircleFilled, DeleteOutlined, DownloadOutlined, FileImageOutlined, InboxOutlined, PictureOutlined, ShoppingOutlined, ThunderboltOutlined } from "@ant-design/icons";', 'import { AppstoreOutlined, CheckCircleFilled, DeleteOutlined, DownloadOutlined, FileImageOutlined, IdcardOutlined, InboxOutlined, PictureOutlined, ProfileOutlined, ShoppingOutlined, SmileOutlined, ThunderboltOutlined } from "@ant-design/icons";')
$source = $source.Replace('type Field = { key: string; label: string; help: string; type: "text" | "number" | "select"; defaultValue: string; options?: string[] };', 'type Field = { key: string; label: string; help: string; type: "text" | "textarea" | "number" | "select"; defaultValue: string; options?: string[]; required?: boolean };')
$source = $source.Replace('icon: "upscale" | "enhance" | "layered" | "product" | "generic";', 'icon: "upscale" | "enhance" | "layered" | "product" | "character" | "poster" | "emoji" | "detail" | "generic";')

$marker = "  }`r`n};`r`n`r`nconst genericTool"
if ($source.IndexOf($marker, [System.StringComparison]::Ordinal) -lt 0) { $marker = "  }`n};`n`nconst genericTool" }
if ($source.IndexOf($marker, [System.StringComparison]::Ordinal) -lt 0) { throw "Tool config insertion marker was not found." }
$extraConfigs = @'
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

const genericTool
'@
$source = $source.Replace($marker, $extraConfigs)
$source = $source.Replace('if (icon === "product") return <ShoppingOutlined />;', 'if (icon === "product") return <ShoppingOutlined />;`n  if (icon === "character") return <IdcardOutlined />;`n  if (icon === "poster") return <PictureOutlined />;`n  if (icon === "emoji") return <SmileOutlined />;`n  if (icon === "detail") return <ProfileOutlined />;')
$source = $source.Replace('const pageStyle = { "--tool-accent": config.accent, "--tool-soft": config.soft } as CSSProperties;', 'const pageStyle = { "--tool-accent": config.accent, "--tool-soft": config.soft } as CSSProperties;`n  const categoryLabel = config.slug === "character-setting-sheet" ? "\u98ce\u683c\u8f6c\u6362" : config.slug === "emoji-sticker" ? "\u521b\u610f\u751f\u6210" : config.slug.includes("ecommerce") || config.slug === "product-detail-image" ? "\u7535\u5546\u5de5\u5177" : copy.category;`n  const uploadLabel = config.slug === "character-setting-sheet" ? "\u4e0a\u4f20\u89d2\u8272\u53c2\u8003\u56fe" : config.slug === "emoji-sticker" ? "\u4e0a\u4f20\u4eba\u7269/\u89d2\u8272\u56fe\u7247" : config.slug.includes("ecommerce") || config.slug === "product-detail-image" ? "\u4e0a\u4f20\u4ea7\u54c1\u56fe" : copy.upload;')
$source = $source.Replace('<div><div className={styles.titleRow}><h1>{config.title}</h1><span>{copy.category}</span></div>', '<div><div className={styles.titleRow}><h1>{config.title}</h1><span>{categoryLabel}</span></div>')
$source = $source.Replace('<label className={styles.label}><b>*</b>{copy.upload}</label>', '<label className={styles.label}><b>*</b>{uploadLabel}</label>')
$source = $source.Replace('<InboxOutlined />{copy.upload}<input', '<InboxOutlined />{uploadLabel}<input')
$oldFields = '{config.fields.map((field) => <div className={styles.field} key={field.key}><label className={styles.label}><b>*</b>{field.label}</label>{field.type === "select" ? <select value={values[field.key] ?? ""} onChange={(event) => setValues({ ...values, [field.key]: event.target.value })}>{field.options?.map((option) => <option key={option}>{option}</option>)}</select> : <Input type={field.type} value={values[field.key] ?? ""} onChange={(event) => setValues({ ...values, [field.key]: event.target.value })} />}<p className={styles.help}>{field.help}</p></div>)}'
$newFields = '{config.fields.map((field) => <div className={styles.field} key={field.key}><label className={styles.label}>{field.required === false ? null : <b>*</b>}{field.label}</label>{field.type === "select" ? <select value={values[field.key] ?? ""} onChange={(event) => setValues({ ...values, [field.key]: event.target.value })}>{field.options?.map((option) => <option key={option}>{option}</option>)}</select> : field.type === "textarea" ? <Input.TextArea value={values[field.key] ?? ""} autoSize={{ minRows: 3, maxRows: 5 }} onChange={(event) => setValues({ ...values, [field.key]: event.target.value })} /> : <Input type={field.type} value={values[field.key] ?? ""} onChange={(event) => setValues({ ...values, [field.key]: event.target.value })} />}<p className={styles.help}>{field.help}</p></div>)}'
if ($source.IndexOf($oldFields, [System.StringComparison]::Ordinal) -lt 0) { throw "Workbench field renderer was not found." }
$source = $source.Replace($oldFields, $newFields)

$css = [System.IO.File]::ReadAllText($cssPath)
$css += '.form :global(textarea.ant-input){border-radius:7px;resize:vertical}.field :global(textarea.ant-input){padding-top:9px}'

$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($indexPath, $indexSource, $utf8)
[System.IO.File]::WriteAllText($workbenchPath, $source, $utf8)
[System.IO.File]::WriteAllText($cssPath, $css, $utf8)
Write-Output "Applied Step 40 tool catalog filtering and remaining workbench configurations."
