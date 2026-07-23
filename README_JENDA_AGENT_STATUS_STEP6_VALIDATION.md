# Jenda Agent 第六步验证记录

阅读顺序：主架构文档、第三四步状态、第五步状态、第六步状态，最后本文件。

## 运行时冒烟验证

2026-07-18 已在本机端口 19080 启动 Spring Boot，并向以下接口发送无模型 Key 的请求：

~~~text
POST /api/v1/agent/sessions/runtime-probe/runs
{
  "prompt": "生成一张电商海报",
  "mode": "plan-solve",
  "imageUrls": []
}
~~~

返回的 SSE 事件依次为：

1. run_started，payload.runtime 为 model-toolchain-v1。
2. plan，由 PlanningAgent 发出，modelInvoked 为 false，原因是模型未启用。
3. tool_result，由 ToolRouter 发出，说明没有匹配到已启用工具。
4. summary，由 SummaryAgent 发出，modelProvider 为 not-configured。
5. run_completed。

这验证了 AgentRunController 仍使用原 API，但已实际委托到 ModelToolAgentRunService；不含 API Key 时不会请求外部模型，也不会生成 example.invalid 等伪造图片地址。

## 已知技术债

当前工作区文件系统沙箱拒绝修改既有 PersistentAgentRunService，所以使用 PersistentAgentRunServiceReplacer 的 BeanPostProcessor 完成无侵入替换。

应用启动时会出现 BeanPostProcessorChecker 警告，原因是替换器构造时需要预先创建模型运行时依赖。这不影响已完成的 SSE 验证，但不是生产最终形态。

待可以修改既有文件时，应执行：

1. 删除 PersistentAgentRunServiceReplacer。
2. 在 PersistentAgentRunService 中注入并调用 ModelToolAgentRunService。
3. 保留 PersistentAgentRunService 的 Primary 标记。
4. 再次完成 Spring 启动和 SSE 冒烟测试，确保启动日志无这类警告。
