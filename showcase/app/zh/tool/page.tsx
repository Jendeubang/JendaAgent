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