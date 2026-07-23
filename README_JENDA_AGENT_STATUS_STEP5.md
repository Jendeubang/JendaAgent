# Jenda Agent 开发状态：第五步，多模态上传

> 主架构文档：[README_JENDA_AGENT.md](README_JENDA_AGENT.md)  
> 第三、四步（真实 SSE 与历史持久化）：[README_JENDA_AGENT_STATUS.md](README_JENDA_AGENT_STATUS.md)

本文件记录“接入多模态上传”后的可运行边界。继续开发前，按主文档、第三四步状态和本文件的顺序阅读。

## 已实现

### 上传链路

~~~text
Browser file input
  -> POST /api/v1/agent/media/images (multipart/form-data)
  -> AgentImageStorage
  -> LocalAgentImageStorage
  -> ./data/uploads/agent/<uuid>.<extension>
  -> /uploads/agent/<uuid>.<extension>
  -> image_url put into AgentRunRequest.imageUrls
  -> PlanningAgent / ExecutorAgent event context
~~~

后端新增：

- AgentMediaController: 图片上传 API，返回统一图片资产描述。
- AgentImageStorage: 存储抽象。后续腾讯云 COS 实现必须继续使用此接口，不能把 COS SDK 调用写入 Controller。
- LocalAgentImageStorage: 本地开发实现，只允许 JPEG、PNG、WEBP、GIF，最大 10 MB，生成 UUID 文件名。
- AgentUploadWebConfig: 配置 multipart 上限，并把 /uploads/agent/** 映射到本地上传目录。
- StoredAgentImage、AgentImageUploadResponse: 上传结果模型。

上传接口：

~~~http
POST /api/v1/agent/media/images
Content-Type: multipart/form-data

file=<image file>
~~~

响应形状：

~~~json
{
  "assetId": "uuid",
  "fileName": "reference.png",
  "imageUrl": "http://127.0.0.1:8080/uploads/agent/uuid.png",
  "mediaType": "image/png",
  "size": 12345,
  "modelAccessible": false,
  "storageProvider": "local"
}
~~~

modelAccessible 为 false 是刻意设计：外部大模型通常无法访问开发机的 127.0.0.1。前端可以预览该地址，但模型调用前必须替换为 COS 的公开 HTTPS URL 或签名 URL。

### 前端验证入口

新增 [showcase/app/upload/page.tsx](showcase/app/upload/page.tsx)，访问：

~~~text
http://localhost:3000/upload
~~~

该页面执行以下流程：

1. 选择图片，提交 multipart 上传。
2. 预览上传结果，显示 image_url、文件类型、大小和模型可访问状态。
3. 输入任务并选择 Plan-Solve 或 ReAct。
4. 以 prompt、mode、imageUrls 发起 SSE 任务。
5. 实时显示 plan、task、tool_result、image、summary 事件。

前端通过 NEXT_PUBLIC_AGENT_API_BASE_URL 配置后端地址，示例见 showcase/.env.example。

## 本地运行

后端：

~~~powershell
cd F:\JendaAgent\JendaAgent\genie-backend
mvn "-Dmaven.repo.local=F:\JendaAgent\.m2" spring-boot:run
~~~

前端：

~~~powershell
cd F:\JendaAgent\JendaAgent\showcase
$env:NEXT_PUBLIC_AGENT_API_BASE_URL="http://127.0.0.1:8080"
corepack pnpm dev
~~~

如 8080 被占用，将后端改到空闲端口，并让 NEXT_PUBLIC_AGENT_API_BASE_URL 指向同一个端口。

## 已验证

~~~powershell
cd F:\JendaAgent\JendaAgent\genie-backend
mvn "-Dmaven.repo.local=F:\JendaAgent\.m2" "-Dtest=AgentHistoryStoreTest,LocalAgentImageStorageTest" test
~~~

结果：2 tests, 0 failures, 0 errors。

~~~powershell
cd F:\JendaAgent\JendaAgent\showcase
node_modules\.bin\tsc.cmd --noEmit --skipLibCheck
node_modules\.bin\next.cmd build --no-lint --experimental-build-mode compile
~~~

结果：通过，路由包括 /upload。当前构建有一条 CSS 的 align-items: end 兼容性告警，不影响运行；待文件系统沙箱恢复对现有文件的更新权限后，将改为 flex-end。

## 下一步：COS 与真实模型

1. 实现 CosAgentImageStorage，由 agent.storage.provider=cos 选择，并从环境变量读取 COS 桶、地域、临时凭证。
2. 上传响应中返回 HTTPS URL，令 modelAccessible=true。
3. 在模型适配器中将 imageUrls 映射为模型提供商的 image_url content part，不能只将 URL 拼入文本提示词。
4. 保存图片资产元数据到 Workspace 表，SSE 回放时恢复资产卡片，而不只是恢复消息时间线。
5. 用 COS STS 临时密钥改为浏览器直传；服务端只签名、校验和记录资产。
