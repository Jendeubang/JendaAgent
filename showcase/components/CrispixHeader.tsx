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
      <nav>{links.map(([href, label]) => <Link key={href} href={href} className={pathname === href || (href === "/zh/tool" && pathname.startsWith("/zh/tool/")) ? styles.active : ""}>{label}</Link>)}</nav>
      <div className={styles.actions}>
        <Link href="/login" className={styles.login}>{labels.login}</Link>
        <Button type="primary" href="/zh/agent">{labels.start}</Button>
      </div>
    </header>
  );
}