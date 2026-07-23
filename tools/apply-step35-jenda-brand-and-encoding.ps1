$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$showcaseRoot = Join-Path $repoRoot "showcase"

function Write-Source([string]$relativePath, [string]$content) {
    $path = Join-Path $showcaseRoot $relativePath
    $directory = Split-Path -Parent $path
    [System.IO.Directory]::CreateDirectory($directory) | Out-Null
    [System.IO.File]::WriteAllText($path, $content, [System.Text.UTF8Encoding]::new($false))
}

$header = @'
"use client";

import Link from "next/link";
import { Button } from "antd";
import { usePathname } from "next/navigation";
import styles from "./CrispixHeader.module.css";

const labels = {
  home: "\u9996\u9875",
  agent: "AI \u667a\u80fd\u4f53",
  tools: "\u5de5\u5177\u7bb1",
  pricing: "\u4ef7\u683c\u65b9\u6848",
  login: "\u767b\u5f55",
  start: "\u5f00\u59cb\u4f7f\u7528",
};

export function CrispixHeader() {
  const pathname = usePathname();
  const links = [["/zh", labels.home], ["/zh/agent", labels.agent], ["/zh/tool", labels.tools], ["/zh/pricing", labels.pricing]];

  return (
    <header className={styles.header}>
      <Link href="/zh" className={styles.logo}><span>J</span><b>Jenda</b></Link>
      <nav>{links.map(([href, label]) => <Link key={href} href={href} className={pathname === href ? styles.active : ""}>{label}</Link>)}</nav>
      <div className={styles.actions}>
        <Link href="/login" className={styles.login}>{labels.login}</Link>
        <Button type="primary" href="/zh/agent">{labels.start}</Button>
      </div>
    </header>
  );
}
'@

$homePage = @'
"use client";

import Link from "next/link";
import { Button } from "antd";
import { ArrowRightOutlined, CheckCircleFilled, PlayCircleOutlined } from "@ant-design/icons";
import { CrispixHeader } from "../../components/CrispixHeader";
import styles from "./page.module.css";

const copy = {
  model: "\u91cd\u78c5\u63a5\u5165",
  title: "\u81ea\u4e3b\u89c4\u5212\uff0c\u667a\u80fd\u521b\u4f5c",
  description: "\u4ee5 AI \u81ea\u4e3b\u89c4\u5212\u7684\u56fe\u50cf\u667a\u80fd\u4f53\u4e3a\u6838\u5fc3\uff0c\u7406\u89e3\u60a8\u7684\u9700\u6c42\uff0c\u81ea\u52a8\u62c6\u89e3\u5e76\u5b8c\u6210\u56fe\u50cf\u4efb\u52a1\u3002",
  experience: "\u7acb\u5373\u4f53\u9a8c",
  demo: "\u89c2\u770b\u6f14\u793a",
  demoPrompt: "\u8bf7\u5e2e\u6211\u8bbe\u8ba1\u4e00\u5f20\u79d1\u6280\u98ce\u683c\u7684\u53d1\u5e03\u4f1a\u6d77\u62a5",
  stages: ["\u9700\u6c42\u7406\u89e3", "\u7d20\u6750\u641c\u7d22", "\u56fe\u50cf\u751f\u6210", "\u540e\u671f\u4f18\u5316"],
  engines: "\u53cc\u6838\u5f15\u64ce",
  engineLead: "\u4ee5\u667a\u80fd\u4f53\u4e3a\u6838\u5fc3\uff0cAI \u529f\u80fd\u4e3a\u8f85\u52a9\uff0c\u8ba9\u590d\u6742\u7684\u56fe\u50cf\u9700\u6c42\u53d8\u5f97\u7b80\u5355\u3002",
  react: "ReAct \u6a21\u5f0f",
  plan: "Plan-Solve \u6a21\u5f0f",
  reactDesc: "\u9002\u5408\u5feb\u901f\u95ee\u7b54\u3001\u56fe\u50cf OCR \u4e0e\u5355\u6b65\u5de5\u5177\u8c03\u7528\u3002",
  planDesc: "\u81ea\u52a8\u62c6\u89e3\u590d\u6742\u76ee\u6807\uff0c\u591a\u667a\u80fd\u4f53\u5e76\u53d1\u6267\u884c\u5e76\u81ea\u6211\u53cd\u601d\u3002",
  fromIdea: "\u4ece\u60f3\u6cd5\u5230\u6210\u679c",
  oneSentence: "\u53ea\u9700\u4e00\u53e5\u8bdd",
  showcase: "\u667a\u80fd\u4f53\u4f1a\u5206\u6790\u4f60\u7684\u76ee\u6807\uff0c\u9009\u62e9\u5408\u9002\u7684\u5de5\u5177\uff0c\u4ea4\u4ed8\u53ef\u76f4\u63a5\u4f7f\u7528\u7684\u6210\u54c1\u3002",
  algorithm: "\u4e86\u89e3\u89c4\u5212\u7b97\u6cd5",
  toolbox: "AI \u5de5\u5177\u7bb1",
  use: "\u70b9\u51fb\u8bd5\u7528",
  enter: "\u8fdb\u5165\u5de5\u5177\u7bb1",
};

const tools = [
  ["tool-01.webp", "\u56fe\u50cf\u53d8\u6e05\u6670", "AI \u8d85\u5206\u8fa8\u7387\u6280\u672f\uff0c\u4e00\u952e\u63d0\u5347\u753b\u9762\u7ec6\u8282"],
  ["tool-02.jpeg", "\u56fe\u7247\u53d8\u624b\u529e", "\u98ce\u683c\u8fc1\u79fb\u6280\u672f\uff0c\u4fdd\u7559\u4eba\u7269\u7279\u5f81"],
  ["tool-03.png", "\u63d0\u53d6\u4ea7\u54c1\u767d\u5e95\u56fe", "\u667a\u80fd\u5206\u79bb\u4e3b\u4f53\u4e0e\u80cc\u666f"],
  ["tool-04.png", "\u98ce\u683c\u8f6c\u6362", "\u6cb9\u753b\u3001\u6c34\u5f69\u3001\u7d20\u63cf\u4e0e\u5361\u901a\u98ce\u683c"],
];

export default function ZhHomePage() {
  return <main className={styles.page}>
    <CrispixHeader />
    <section className={styles.hero}>
      <div className={styles.heroCopy}>
        <span className={styles.kicker}>NEW &nbsp; GPT-IMAGE-2 {copy.model}</span>
        <p className={styles.eyebrow}>JENDA AUTOPLAN AGENT CORE</p>
        <h1>Jenda Agent</h1><h2>{copy.title}</h2><p>{copy.description}</p>
        <div className={styles.heroActions}><Button type="primary" size="large" href="/zh/agent">{copy.experience} <ArrowRightOutlined /></Button><Button size="large" icon={<PlayCircleOutlined />}>{copy.demo}</Button></div>
      </div>
      <div className={styles.agentDemo}>
        <div className={styles.demoTop}><span>AGENT RUNNING</span><small>01 / 04</small></div><div className={styles.quote}>{copy.demoPrompt}</div>
        {copy.stages.map((item, index) => <div className={styles.demoStep} key={item}><span>{index < 2 ? <CheckCircleFilled /> : <i />}</span><b>{item}</b><small>{index < 2 ? "Complete" : "Waiting"}</small></div>)}
        <div className={styles.demoOutput}><img src="/crispix/tool-01.webp" alt="Jenda generated result" /><span><b>jenda_poster_final.png</b><small>Generated by Jenda Agent</small></span></div>
      </div>
    </section>
    <section className={styles.section}><p className={styles.eyebrow}>DUAL ENGINE</p><h2>{copy.engines}</h2><p className={styles.lead}>{copy.engineLead}</p><div className={styles.engineGrid}><article><span>01</span><h3>{copy.react}</h3><p>{copy.reactDesc}</p><b>Fast &middot; Low Latency</b></article><article className={styles.featured}><span>02</span><h3>{copy.plan}</h3><p>{copy.planDesc}</p><b>Planning &middot; Dynamic Loop</b></article></div></section>
    <section className={styles.showcase}><div><p className={styles.eyebrow}>FROM IDEA TO DELIVERY</p><h2>{copy.fromIdea}<br /><em>{copy.oneSentence}</em></h2><p>{copy.showcase}</p><Link href="/zh/agent">{copy.algorithm} <ArrowRightOutlined /></Link></div><div className={styles.poster}><img src="/crispix/tool-02.jpeg" alt="Jenda creative showcase" /><span>Jenda Visual Collection</span></div></section>
    <section className={styles.tools}><p className={styles.eyebrow}>JENDA TOOL</p><h2>{copy.toolbox}</h2><div className={styles.toolGrid}>{tools.map(([image, title, description]) => <Link href="/zh/tool" key={title}><img src={`/crispix/${image}`} alt={title} /><div><h3>{title}</h3><p>{description}</p><span>{copy.use} <ArrowRightOutlined /></span></div></Link>)}</div><Link href="/zh/tool" className={styles.more}>{copy.enter} <ArrowRightOutlined /></Link></section>
    <footer><b>Jenda AI</b><span>2026 Jenda Agent Platform</span></footer>
  </main>;
}
'@

$tool = @'
"use client";

import Link from "next/link";
import { ArrowRightOutlined, SearchOutlined } from "@ant-design/icons";
import { Input, Tag } from "antd";
import { CrispixHeader } from "../../../components/CrispixHeader";
import styles from "./page.module.css";

const text = {
  title: "Jenda Tool", description: "\u4e00\u7ad9\u5f0f AI \u521b\u4f5c\u5de5\u5177\u96c6\u5408\uff0c\u6ee1\u8db3\u4f60\u7684\u5404\u79cd\u521b\u4f5c\u9700\u6c42\u3002", models: "\u63a5\u5165 NanoBanana 2\u3001NanoBanana Pro\u3001SeedDream 4.5 \u7b49\u9876\u7ea7\u56fe\u50cf\u6a21\u578b", all: "\u5168\u90e8", processing: "\u56fe\u50cf\u5904\u7406", creative: "\u521b\u610f\u751f\u6210", commerce: "\u7535\u5546\u5de5\u5177", search: "\u641c\u7d22\u5de5\u5177", detail: "\u70b9\u51fb\u67e5\u770b\u8be6\u60c5", coming: "\u66f4\u591a\u529f\u80fd\u6b63\u5728\u5f00\u53d1\u4e2d\uff0c\u656c\u8bf7\u671f\u5f85", hot: "\u70ed\u95e8", fresh: "\u65b0\u54c1",
};

const cards = [
  ["tool-01.webp", "\u56fe\u50cf\u8d85\u5206", "\u56fe\u50cf\u590d\u539f", text.hot, "AI \u8d85\u5206\u8fa8\u7387\u6280\u672f\uff0c\u4e00\u952e\u63d0\u5347\u56fe\u50cf\u6e05\u6670\u5ea6"],
  ["tool-02.jpeg", "\u5b9a\u5236\u5de5\u4f5c\u6d41", "\u56fe\u50cf\u589e\u5f3a", "\u4e13\u4e1a", "\u6e05\u6670\u5ea6\u589e\u5f3a\u3001\u964d\u566a\u3001\u53bb\u4f2a\u5f71\uff0c\u652f\u63012K/4K/8K"],
  ["tool-03.png", "\u56fe\u50cf\u5206\u5c42", "\u56fe\u50cf\u5904\u7406", text.fresh, "\u667a\u80fd\u8bed\u4e49\u5206\u5272\uff0c\u81ea\u52a8\u5206\u79bb\u4e3b\u4f53\u4e0e\u80cc\u666f"],
  ["tool-04.png", "\u7535\u5546\u4ea7\u54c1\u7cbe\u4fee", "\u7535\u5546\u5de5\u5177", "\u7535\u5546", "\u751f\u6210\u4ea7\u54c1\u767d\u5e95\u6b63\u89c6\u56fe\uff0c\u8ba9\u4ea7\u54c1\u66f4\u6709\u8d28\u611f"],
  ["tool-05.png", "\u80cc\u666f\u79fb\u9664", "\u56fe\u50cf\u5904\u7406", "\u4e13\u4e1a", "\u4e00\u952e\u667a\u80fd\u62a0\u56fe\uff0c\u7cbe\u51c6\u5206\u79bb\u4e3b\u4f53\u4e0e\u80cc\u666f"],
  ["tool-06.jpg", "NanoBanana Pro \u89d2\u8272\u8bbe\u5b9a\u56fe", "\u521b\u610f\u751f\u6210", "\u521b\u610f", "\u5c06\u4eba\u7269\u751f\u6210\u89c4\u8303\u7684\u89d2\u8272\u8bbe\u5b9a\u56fe"],
  ["tool-07.jpg", "\u4eba\u7269\u8f6c\u624b\u529e", "\u521b\u610f\u751f\u6210", "\u521b\u610f", "\u5c06\u4eba\u7269\u56fe\u7247\u53d8\u6210\u53ef\u7231\u7684 3D \u624b\u529e\u98ce\u683c"],
  ["tool-08.jpg", "\u6f02\u6d6e\u5c9b\u7acb\u4f53\u6a21\u578b", "\u521b\u610f\u751f\u6210", "\u521b\u610f", "\u8f93\u5165\u5730\u6807\uff0c\u751f\u6210\u6587\u5316\u5143\u7d20\u4e0e\u5efa\u7b51\u7684 3D \u5c0f\u5c9b\u5c7f"],
  ["tool-09.png", "NanoBanana 2 \u7535\u5546\u5ba3\u4f20\u6d77\u62a5", "\u7535\u5546\u5de5\u5177", "\u7535\u5546", "\u4e3a\u4ea7\u54c1\u751f\u6210\u9ad8\u7aef\u5927\u6c14\u7684\u5ba3\u4f20\u6d77\u62a5"],
  ["tool-10.png", "NanoBanana 2 \u5546\u54c1\u8be6\u60c5\u56fe", "\u7535\u5546\u5de5\u5177", "\u7535\u5546", "\u751f\u6210\u7cbe\u7f8e\u7684\u8be6\u60c5\u9875\u5c55\u793a\u56fe"],
];

export default function ToolPage() {
  return <main className={styles.page}><CrispixHeader /><section className={styles.intro}><p>JENDA TOOL</p><h1>{text.title}</h1><span>{text.description}</span><small>{text.models}</small></section><div className={styles.filters}><div><button className={styles.chosen}>{text.all} <b>13</b></button><button>{text.processing} <b>4</b></button><button>{text.creative} <b>5</b></button><button>{text.commerce} <b>4</b></button></div><Input prefix={<SearchOutlined />} placeholder={text.search} /></div><section className={styles.grid}>{cards.map(([image, title, category, badge, description]) => <Link href="/image-studio" className={styles.card} key={title}><div className={styles.imageWrap}><img src={`/crispix/${image}`} alt={title} /><Tag color={badge === text.hot ? "blue" : badge === text.fresh ? "green" : "default"}>{badge}</Tag></div><div className={styles.cardBody}><small>{category}</small><h2>{title}</h2><p>{description}</p><span>{text.detail} <ArrowRightOutlined /></span></div></Link>)}</section><p className={styles.more}>{text.coming}</p></main>;
}
'@

$pricing = @'
"use client";

import { useState } from "react";
import { Button, Segmented } from "antd";
import { CheckOutlined } from "@ant-design/icons";
import { CrispixHeader } from "../../../components/CrispixHeader";
import styles from "./page.module.css";

const text = { basic: "\u57fa\u7840\u7248", pro: "\u4e13\u4e1a\u7248", enterprise: "\u4f01\u4e1a\u7248", pricing: "\u4ef7\u683c\u65b9\u6848", choose: "\u9009\u62e9\u9002\u5408\u60a8\u7684", description: "\u7075\u6d3b\u7684\u5b9a\u4ef7\u65b9\u6848\uff0c\u6ee1\u8db3\u4e0d\u540c\u89c4\u6a21\u7528\u6237\u7684\u9700\u6c42\u3002", month: "\u6309\u6708\u4ed8\u8d39", year: "\u6309\u5e74\u4ed8\u8d39 \u770120%", recommend: "\u63a8\u8350", perMonth: " / \u6bcf\u6708", contact: "\u8054\u7cfb\u9500\u552e", start: "\u5f00\u59cb\u4f7f\u7528", compare: "\u529f\u80fd\u5bf9\u6bd4", feature: "\u529f\u80fd\u7279\u6027", footer: "\u9700\u8981\u66f4\u591a\u5b9a\u5236\u5316\u529f\u80fd\uff1f \u8054\u7cfb\u6211\u4eec\uff0c\u83b7\u53d6\u79c1\u6709\u5316\u90e8\u7f72\u4e0e\u5b9a\u5236\u5f00\u53d1\u65b9\u6848\u3002", yuan: "\u00a5" };
const plans = [
  { name: text.basic, desc: "\u9002\u5408\u4e2a\u4eba\u7528\u6237\u548c\u5c0f\u578b\u9879\u76ee", month: 129, year: 99, features: ["\u6bcf\u6708 1,000 \u6b21 API \u8c03\u7528", "\u57fa\u7840\u56fe\u50cf\u8bc6\u522b", "\u6807\u51c6\u54cd\u5e94\u901f\u5ea6", "\u90ae\u4ef6\u652f\u6301", "\u57fa\u7840\u6587\u6863"] },
  { name: text.pro, desc: "\u9002\u5408\u4e2d\u5c0f\u4f01\u4e1a\u548c\u56e2\u961f\u4f7f\u7528", month: 399, year: 299, hot: true, features: ["\u6bcf\u6708 10,000 \u6b21 API \u8c03\u7528", "\u9ad8\u7ea7\u56fe\u50cf\u8bc6\u522b", "\u4f18\u5148\u54cd\u5e94\u901f\u5ea6", "24/7 \u5728\u7ebf\u652f\u6301", "\u5b8c\u6574\u6587\u6863\u548c SDK", "\u56e2\u961f\u534f\u4f5c\u529f\u80fd"] },
  { name: text.enterprise, desc: "\u9002\u5408\u5927\u578b\u4f01\u4e1a\u548c\u9ad8\u9891\u4f7f\u7528", month: 1299, year: 999, features: ["\u65e0\u9650 API \u8c03\u7528", "\u4f01\u4e1a\u7ea7\u56fe\u50cf\u8bc6\u522b", "\u6700\u9ad8\u4f18\u5148\u7ea7", "\u4e13\u5c5e\u5ba2\u6237\u7ecf\u7406", "\u79c1\u6709\u5316\u90e8\u7f72", "SLA \u4fdd\u969c"] },
];

export default function PricingPage() {
  const [annual, setAnnual] = useState(false);
  const rows = [["API \u8c03\u7528\u6b21\u6570", "1,000 / \u6708", "10,000 / \u6708", "\u65e0\u9650"], ["\u56fe\u50cf\u8bc6\u522b\u7cbe\u5ea6", "\u6807\u51c6", "\u9ad8\u7ea7", "\u4f01\u4e1a\u7ea7"], ["\u6280\u672f\u652f\u6301", "\u90ae\u4ef6", "24/7 \u5728\u7ebf", "\u4e13\u5c5e\u5ba2\u6237\u7ecf\u7406"], ["\u79c1\u6709\u5316\u90e8\u7f72", "-", "-", "\u652f\u6301"], ["SLA \u4fdd\u969c", "-", "99.9%", "99.99%"]];
  return <main className={styles.page}><CrispixHeader /><section className={styles.hero}><p>PRICING</p><h1>{text.choose}<br /><em>{text.pricing}</em></h1><span>{text.description}</span><Segmented value={annual ? "year" : "month"} onChange={(value) => setAnnual(value === "year")} options={[{ label: text.month, value: "month" }, { label: text.year, value: "year" }]} /></section><section className={styles.plans}>{plans.map((plan) => <article className={plan.hot ? styles.hot : ""} key={plan.name}>{plan.hot && <b className={styles.recommend}>{text.recommend}</b>}<small>JENDA AGENT</small><h2>{plan.name}</h2><p>{plan.desc}</p><div className={styles.price}><span>{text.yuan}</span>{annual ? plan.year : plan.month}<small>{text.perMonth}</small></div><ul>{plan.features.map((feature) => <li key={feature}><CheckOutlined />{feature}</li>)}</ul><Button type={plan.hot ? "primary" : "default"} block>{plan.name === text.enterprise ? text.contact : text.start}</Button></article>)}</section><section className={styles.compare}><h2>{text.compare}</h2><div className={styles.table}><div>{text.feature}</div><div>{text.basic}</div><div>{text.pro}</div><div>{text.enterprise}</div>{rows.flatMap((row) => row.map((cell, index) => <span className={index === 0 ? styles.label : ""} key={`${row[0]}-${index}`}>{cell}</span>))}</div></section><footer>{text.footer}</footer></main>;
}
'@

Write-Source "components\CrispixHeader.tsx" $header
Write-Source "app\zh\page.tsx" $homePage
Write-Source "app\zh\tool\page.tsx" $tool
Write-Source "app\zh\pricing\page.tsx" $pricing
Write-Output "Applied Step 35 Jenda branding and JSX encoding recovery."
