import type { AppDispatch } from "../store";
import { addAsset, addMessage, setActivePhase, setRunning, type AgentMode } from "../store/session-slice";

const wait = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms));
const now = () => new Intl.DateTimeFormat("zh-CN", { hour: "2-digit", minute: "2-digit" }).format(new Date());

export async function streamDemoRun(dispatch: AppDispatch, prompt: string, mode: AgentMode, uploadedName?: string) {
  dispatch(setRunning(true));
  dispatch(addMessage({
    id: `user-${Date.now()}`,
    type: "user",
    title: "你的任务",
    content: prompt,
    status: "complete",
    timestamp: now(),
  }));

  if (uploadedName) {
    dispatch(addAsset({
      id: `upload-${Date.now()}`,
      name: uploadedName,
      kind: "image",
      detail: "多模态上下文已注入至智能体执行链路",
      accent: "teal",
    }));
  }

  dispatch(setActivePhase("planning"));
  await wait(560);
  dispatch(addMessage({
    id: `plan-${Date.now()}`,
    type: "plan",
    title: mode === "plan-solve" ? "PlanningAgent 正在拆解交付路径" : "ReAct · Think：理解目标与视觉约束",
    content: uploadedName
      ? "检测到参考图，已将 image_url 作为共享上下文传入规划与执行阶段。"
      : "未提供参考图，将根据文本目标建立可执行的视觉任务。",
    steps: ["识别任务目标与交付物", "检索提示词规范与视觉约束", "并发编排图像工具与结果校验"],
    status: "complete",
    timestamp: now(),
  }));

  dispatch(setActivePhase("executing"));
  await wait(780);
  dispatch(addMessage({
    id: `task-${Date.now()}`,
    type: "task",
    title: "ExecutorAgent · 子任务 01 / 02",
    content: "PromptOpAgent 已召回图像提示词规范，并生成结构化提示词。",
    tool: "PromptOpAgent + Qwen",
    status: "complete",
    timestamp: now(),
  }));

  await wait(620);
  dispatch(addMessage({
    id: `tool-${Date.now()}`,
    type: "tool_result",
    title: mode === "plan-solve" ? "工具并发执行：视觉理解 + 图像生成" : "ReAct · Act：调用 Image Studio",
    content: "视觉理解完成，主体、构图与色彩约束已提取；生成任务已进入结果校验。",
    tool: uploadedName ? "OCR / Vision / SeedDream" : "PromptOpAgent / SeedDream",
    status: "complete",
    timestamp: now(),
  }));

  await wait(720);
  const artifactId = `artifact-${Date.now()}`;
  dispatch(addAsset({
    id: artifactId,
    name: "jenda-creative-delivery.png",
    kind: "image",
    detail: "1024 x 1024 · Image Studio · 已完成",
    accent: "coral",
  }));
  dispatch(addMessage({
    id: `image-${Date.now()}`,
    type: "image",
    title: "中间产物：视觉方案 A",
    content: "已生成符合任务目标的视觉交付物，可在 Workspace 中预览、查看详情或下载。",
    artifactId,
    status: "complete",
    timestamp: now(),
  }));

  dispatch(setActivePhase("summarizing"));
  await wait(560);
  dispatch(addMessage({
    id: `summary-${Date.now()}`,
    type: "summary",
    title: "SummaryAgent · 交付已完成",
    content: "已汇总规划、工具调用与产物。该会话事件可按 messageType 持久化，并在后续访问时完整回放。",
    status: "complete",
    timestamp: now(),
  }));
  dispatch(setRunning(false));
}
