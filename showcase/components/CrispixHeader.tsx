"use client";

import Link from "next/link";
import { Button } from "antd";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { clearAgentAuthSession, logoutAgentSession, readAgentAuthSession, type AgentAuthSession } from "../lib/agentAuth";
import styles from "./CrispixHeader.module.css";

const labels = { home: "首页", agent: "AI 智能体", tools: "工具箱", pricing: "价格方案", login: "登录", start: "开始使用" };

export function CrispixHeader() {
  const pathname = usePathname();
  const router = useRouter();
  const [session, setSession] = useState<AgentAuthSession>();
  const links = [["/zh", labels.home], ["/zh/agent", labels.agent], ["/zh/tool", labels.tools], ["/zh/pricing", labels.pricing]];
  useEffect(() => {
    const sync = () => setSession(readAgentAuthSession());
    sync(); window.addEventListener("storage", sync);
    return () => window.removeEventListener("storage", sync);
  }, []);
  const logout = async () => { await logoutAgentSession(); clearAgentAuthSession(); setSession(undefined); router.replace("/zh"); };

  return <header className={styles.header}>
    <Link href="/zh" className={styles.logo}><span>J</span><b>Jenda</b></Link>
    <nav>{links.map(([href, label]) => <Link key={href} href={href} className={pathname === href || (href === "/zh/tool" && pathname.startsWith("/zh/tool/")) ? styles.active : ""}>{label}</Link>)}</nav>
    <div className={styles.actions}>
      {session ? <div className={styles.account}><span className={styles.avatar}>{session.username.slice(0, 1).toUpperCase()}</span><span className={styles.username}>{session.username}</span><button type="button" onClick={() => void logout()}>退出</button></div> : <><Link href="/login" className={styles.login}>{labels.login}</Link><Button type="primary" href="/zh/agent">{labels.start}</Button></>}
    </div>
  </header>;
}