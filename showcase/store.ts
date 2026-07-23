import { configureStore, createSlice, type PayloadAction } from "@reduxjs/toolkit";

export type Mode = "plan-solve" | "react";
export type MessageKind = "user" | "plan" | "task" | "tool" | "image" | "summary";
export type Message = { id: string; kind: MessageKind; title: string; content: string; meta?: string; steps?: string[] };
export type Asset = { id: string; name: string; detail: string; tone: "gold" | "teal" | "coral" };

type StudioState = { mode: Mode; running: boolean; phase: string; messages: Message[]; assets: Asset[] };
const initialState: StudioState = {
  mode: "plan-solve",
  running: false,
  phase: "等待任务",
  messages: [{ id: "intro", kind: "summary", title: "Jenda Agent Studio 已就绪", content: "输入任务后，系统将用可回放的 SSE 事件呈现规划、执行、工具调用和交付物汇总。", meta: "SYSTEM READY" }],
  assets: [{ id: "ref", name: "品牌视觉参考.png", detail: "image_url 多模态上下文", tone: "gold" }],
};
const studio = createSlice({
  name: "studio",
  initialState,
  reducers: {
    setMode: (state, action: PayloadAction<Mode>) => { state.mode = action.payload; },
    setStatus: (state, action: PayloadAction<{ running: boolean; phase: string }>) => { state.running = action.payload.running; state.phase = action.payload.phase; },
    addMessage: (state, action: PayloadAction<Message>) => { state.messages.push(action.payload); },
    addAsset: (state, action: PayloadAction<Asset>) => { state.assets.unshift(action.payload); },
    reset: (state) => { Object.assign(state, initialState); },
  },
});
export const { setMode, setStatus, addMessage, addAsset, reset } = studio.actions;
export const store = configureStore({ reducer: { studio: studio.reducer } });
export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
