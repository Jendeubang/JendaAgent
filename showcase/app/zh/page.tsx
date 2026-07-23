"use client";

import Link from "next/link";
import { Button } from "antd";
import { ArrowRightOutlined, CheckCircleFilled, PlayCircleOutlined } from "@ant-design/icons";
import { CrispixHeader } from "../../components/CrispixHeader";
import { useEffect, useState } from "react";
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

const stageDetails = [
  "\u5206\u6790\u7528\u6237\u6307\u4ee4\uff0c\u63d0\u53d6\u4e3b\u9898\u3001\u98ce\u683c\u548c\u8272\u8c03\u8981\u6c42",
  "\u4ece\u5de5\u5177\u94fe\u4e0e\u53c2\u8003\u8d44\u4ea7\u4e2d\u9009\u62e9\u53ef\u7528\u7d20\u6750",
  "\u8c03\u7528\u56fe\u50cf\u751f\u6210\u6a21\u578b\u521b\u4f5c\u4e3b\u89c6\u89c9",
  "\u8c03\u6574\u914d\u8272\u3001\u5e03\u5c40\u4e0e\u7ec6\u8282\uff0c\u4ea4\u4ed8\u6210\u54c1"
];

export default function ZhHomePage() {
  const [tick, setTick] = useState(0);
  const demoLength = copy.stages.length + 1;
  const phase = tick % demoLength;
  const completed = phase === copy.stages.length;
  const activeStage = completed ? copy.stages.length - 1 : phase;
  const cycle = Math.floor(tick / demoLength) + 1;

  useEffect(() => {
    const timer = window.setInterval(() => setTick((value) => value + 1), 2200);
    return () => window.clearInterval(timer);
  }, []);
  return <main className={styles.page}>
    <CrispixHeader />
    <section className={styles.hero}>
      <div className={styles.heroCopy}>
        <span className={styles.kicker}>NEW &nbsp; GPT-IMAGE-2 {copy.model}</span>
        <p className={styles.eyebrow}>JENDA AUTOPLAN AGENT CORE</p>
        <h1>Jenda Agent</h1><h2>{copy.title}</h2><p>{copy.description}</p>
        <div className={styles.heroActions}><Button type="primary" size="large" href="/zh/agent">{copy.experience} <ArrowRightOutlined /></Button><Button size="large" icon={<PlayCircleOutlined />}>{copy.demo}</Button></div>
      </div>
      <div className={styles.agentDemo} aria-live="polite">
        <div className={styles.demoTop}><span><i className={styles.pulse} /> {completed ? "TASK COMPLETED" : "AUTOPLAN-AGENT-CORE"}</span><small>{String(cycle).padStart(2, "0")} / AUTO</small></div>
        <div className={styles.quote}><span>U</span>{copy.demoPrompt}</div>
        <div className={styles.liveDetail}>{completed ? "\u5168\u90e8\u5b50\u4efb\u52a1\u5df2\u5b8c\u6210\uff0c\u5df2\u4ea4\u4ed8\u6700\u7ec8\u4ea7\u7269\u3002" : stageDetails[activeStage]}</div>
        <div className={styles.demoSteps}>{copy.stages.map((item, index) => {
          const complete = completed || index < activeStage;
          const active = !completed && index === activeStage;
          const status = complete ? "Complete" : active ? "Running" : "Waiting";
          return <div className={`${styles.demoStep} ${complete ? styles.completeStep : ""} ${active ? styles.activeStep : ""}`} key={item}>
            <span>{complete ? <CheckCircleFilled /> : <i />}</span><b>{item}</b><small>{status}</small>
          </div>;
        })}</div>
        <div className={`${styles.demoOutput} ${(activeStage === copy.stages.length - 1 || completed) ? styles.outputActive : ""}`}>
          <img src="/crispix/tool-01.webp" alt="Jenda generated result" /><span><strong>{completed ? "\u4efb\u52a1\u5b8c\u6210" : "OUTPUT PREVIEW"}</strong><b>{completed ? "output_poster_final.png" : "jenda_poster_final.png"}</b><small>{completed ? "Generated by Jenda Agent" : activeStage === copy.stages.length - 1 ? "Generating preview..." : "Waiting for final output"}</small><i /></span>
        </div>
      </div>
    </section>
    <section className={styles.section}><p className={styles.eyebrow}>DUAL ENGINE</p><h2>{copy.engines}</h2><p className={styles.lead}>{copy.engineLead}</p><div className={styles.engineGrid}><article><span>01</span><h3>{copy.react}</h3><p>{copy.reactDesc}</p><b>Fast &middot; Low Latency</b></article><article className={styles.featured}><span>02</span><h3>{copy.plan}</h3><p>{copy.planDesc}</p><b>Planning &middot; Dynamic Loop</b></article></div></section>
    <section className={styles.showcase}><div><p className={styles.eyebrow}>FROM IDEA TO DELIVERY</p><h2>{copy.fromIdea}<br /><em>{copy.oneSentence}</em></h2><p>{copy.showcase}</p><Link href="/zh/agent">{copy.algorithm} <ArrowRightOutlined /></Link></div><div className={styles.poster}><img src="/crispix/tool-02.jpeg" alt="Jenda creative showcase" /><span>Jenda Visual Collection</span></div></section>
    <section className={styles.tools}><p className={styles.eyebrow}>JENDA TOOL</p><h2>{copy.toolbox}</h2><div className={styles.toolGrid}>{tools.map(([image, title, description]) => <Link href="/zh/tool" key={title}><img src={`/crispix/${image}`} alt={title} /><div><h3>{title}</h3><p>{description}</p><span>{copy.use} <ArrowRightOutlined /></span></div></Link>)}</div><Link href="/zh/tool" className={styles.more}>{copy.enter} <ArrowRightOutlined /></Link></section>
    <footer><b>Jenda AI</b><span>2026 Jenda Agent Platform</span></footer>
  </main>;
}