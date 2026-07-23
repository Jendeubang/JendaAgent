# Jenda Agent 开发状态：第六步，模型与工具链

> 继续开发前依次阅读：
> [README_JENDA_AGENT.md](README_JENDA_AGENT.md)、
> [README_JENDA_AGENT_STATUS.md](README_JENDA_AGENT_STATUS.md)、
> [README_JENDA_AGENT_STATUS_STEP5.md](README_JENDA_AGENT_STATUS_STEP5.md)、
> 本文件。

## 已实现的运行时

本步骤把原先 SSE 中的占位 plan、tool result 和 image 事件替换为可配置的真实执行链：

~~~text
AgentRunRequest(prompt, mode, imageUrls)
  -> PlanningAgent -> OpenAiCompatibleChatClient
  -> ExecutorAgent -> OCR / image-generate / image-edit HTTP adapters (parallel)
  -> SummaryAgent -> OpenAiCompatibleChatClient
  -> AgentHistoryStore -> SSE agent-event
~~~

### 模型接入

OpenAiCompatibleChatClient 调用 OpenAI 兼容的 chat/completions 接口。Qwen 可直接使用 DashScope 兼容模式。

- 文本请求使用 system + user messages。
- user content 是数组：text part 后追加多个 image_url parts。
- imageUrls 因而同时进入 PlanningAgent 与 SummaryAgent；Executor 的工具请求也收到 image_urls。
- 不在代码、application.yml 或 README 中保存真实 API Key。

模型环境变量：

~~~powershell
$env:AGENT_RUNTIME_MODEL_ENABLED="true"
$env:AGENT_RUNTIME_MODEL_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
$env:AGENT_RUNTIME_MODEL_API_KEY="你的 DashScope API Key"
$env:AGENT_RUNTIME_MODEL_MODEL="qwen-plus"
~~~

完整变量样例见 [genie-backend/.env.agent.example](genie-backend/.env.agent.example)。

### 工具链接入

当前实现的是供应商中立 HTTP 适配器，统一向工具端点 POST：

~~~json
{
  "prompt": "用户任务",
  "image_urls": ["https://.../reference.png"],
  "task_type": "ocr"
}
~~~

工具路由：

- 有参考图：选择 OCR。
- 包含 编辑、背景、风格、清晰、修复、替换、局部 等词：选择 image-edit。
- 包含 生成、绘制、海报、图片、图像 等词：选择 image-generate。
- 多个已匹配工具用 CompletableFuture 并发执行。

工具响应适配器会尝试读取 image_url、imageUrl、url、output.url 或 data[0].url；只有工具真正返回 URL 时，才生成 SSE 的 image 事件。不会再发送 example.invalid 伪造 URL。

分别设置以下变量可启用工具：

~~~powershell
$env:AGENT_RUNTIME_TOOLS_OCR_ENABLED="true"
$env:AGENT_RUNTIME_TOOLS_OCR_URL="https://你的 OCR 服务端点"
$env:AGENT_RUNTIME_TOOLS_OCR_API_KEY="你的 OCR Key"

$env:AGENT_RUNTIME_TOOLS_IMAGE_GENERATE_ENABLED="true"
$env:AGENT_RUNTIME_TOOLS_IMAGE_GENERATE_URL="https://你的文生图端点"
$env:AGENT_RUNTIME_TOOLS_IMAGE_GENERATE_API_KEY="你的图像 Key"

$env:AGENT_RUNTIME_TOOLS_IMAGE_EDIT_ENABLED="true"
$env:AGENT_RUNTIME_TOOLS_IMAGE_EDIT_URL="https://你的图像编辑端点"
$env:AGENT_RUNTIME_TOOLS_IMAGE_EDIT_API_KEY="你的图像 Key"
~~~

不同图像供应商的字段并不统一。接入 NanoBanana、SeedDream 或其他服务时，应只修改 HttpAgentToolClient 的供应商映射，或增加各自的 ToolClient 实现，不能把供应商请求格式泄漏到 PlanningAgent、ExecutorAgent 或前端。

## 与上游兼容方式

当前沙箱拒绝修改既有文件。新增 PersistentAgentRunServiceReplacer 通过 Spring BeanPostProcessor 无侵入替换上游 PersistentAgentRunService 的实例，而保留原 Bean 的 Primary 资格与 AgentRunController API。

这不是最终理想结构。待文件系统恢复后，应删除替换器，直接让 PersistentAgentRunService 委托 ModelToolAgentRunService。

## 安全与降级

- 模型未启用或缺少 Key 时，不发起网络调用；SSE 中显示“模型未启用”，并提供确定性的工具路由说明。
- 工具端点未配置时，发出明确的 tool_result 跳过事件。
- 工具异常不会伪装为成功图片；只记录失败结果。
- 本地上传 URL 仍不能被公网模型读取。应先完成 COS HTTPS URL，再启用真实视觉模型。

## 验证

~~~powershell
cd F:\JendaAgent\JendaAgent\genie-backend
mvn "-Dmaven.repo.local=F:\JendaAgent\.m2" "-Dtest=AgentHistoryStoreTest,LocalAgentImageStorageTest,AgentRuntimeClientsTest" test
~~~

本轮新增 AgentRuntimeClientsTest，覆盖“模型禁用不请求外网”和“工具未配置不请求外网”。

## 下一步

1. 配置 DashScope Qwen Key，并以真实模型调用验证 plan/summary 的 SSE 内容。
2. 选择并配置 OCR、文生图、图像编辑供应商的真实端点。
3. 完成 COS 上传，使 image_url 为模型可访问的 HTTPS 地址。
4. 将工具的图像产物写入 Workspace 资产表，并在会话回放时恢复资产卡片。
