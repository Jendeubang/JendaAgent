import { createSlice, type PayloadAction } from "@reduxjs/toolkit";

export type AgentMode = "plan-solve" | "react";
export type MessageType = "user" | "plan" | "task" | "tool_result" | "image" | "summary";

export type AgentMessage = {
  id: string;
  type: MessageType;
  title: string;
  content: string;
  status?: "running" | "complete" | "queued";
  steps?: string[];
  tool?: string;
  artifactId?: string;
  timestamp: string;
};

export type WorkspaceAsset = {
  id: string;
  name: string;
  kind: "image" | "report";
  detail: string;
  accent: "amber" | "teal" | "coral";
};

type SessionState = {
  mode: AgentMode;
  isRunning: boolean;
  activePhase: "idle" | "planning" | "executing" | "summarizing";
  messages: AgentMessage[];
  assets: WorkspaceAsset[];
};

const initialState: SessionState = {
  mode: "plan-solve",
  isRunning: false,
  activePhase: "idle",
  messages: [
    {
      id: "welcome",
      type: "summary",
      title: "Jenda Agent Studio 已就绪",
      content: "选择执行模式，上传参考图，或直接描述你希望交付的内容。演示会以 SSE 事件的形式逐步呈现多智能体协作过程。",
      status: "complete",
      timestamp: "现在",
    },
  ],
  assets: [
    {
      id: "starter-asset",
      name: "品牌视觉参考.png",
      kind: "image",
      detail: "上传图片将以 image_url 传入 PlanningAgent 与 ExecutorAgent",
      accent: "amber",
    },
  ],
};

const sessionSlice = createSlice({
  name: "session",
  initialState,
  reducers: {
    setMode(state, action: PayloadAction<AgentMode>) {
      state.mode = action.payload;
    },
    setRunning(state, action: PayloadAction<boolean>) {
      state.isRunning = action.payload;
      if (!action.payload) state.activePhase = "idle";
    },
    setActivePhase(state, action: PayloadAction<SessionState["activePhase"]>) {
      state.activePhase = action.payload;
    },
    addMessage(state, action: PayloadAction<AgentMessage>) {
      state.messages.push(action.payload);
    },
    addAsset(state, action: PayloadAction<WorkspaceAsset>) {
      state.assets.unshift(action.payload);
    },
    clearRun(state) {
      state.messages = state.messages.filter((message) => message.id === "welcome");
      state.assets = state.assets.filter((asset) => asset.id === "starter-asset");
      state.activePhase = "idle";
      state.isRunning = false;
    },
  },
});

export const { addAsset, addMessage, clearRun, setActivePhase, setMode, setRunning } = sessionSlice.actions;
export default sessionSlice.reducer;
