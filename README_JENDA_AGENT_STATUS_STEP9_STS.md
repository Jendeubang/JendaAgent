# Jenda Agent 开发状态：第九步，COS STS 前端直传

> 阅读顺序：主架构文档 -> SSE/历史状态 -> 多模态状态 -> 模型工具链状态 -> COS 状态 -> 预签名 URL 状态 -> 本文件。

## 已实现

### 后端 API

~~~text
POST /api/v1/agent/media/direct/tickets
POST /api/v1/agent/media/direct/complete
~~~

直传流程：

~~~text
Browser chooses image
  -> POST tickets (file name, media type, size)
  -> Spring calls Tencent STS GetFederationToken
  -> ticket contains one objectKey and temporary credentials
  -> Browser signs PUT using TmpSecretKey and x-cos-security-token
  -> Browser PUT directly to COS
  -> POST complete
  -> Spring HEAD verifies object size
  -> Spring returns private-object signed GET URL
~~~

临时 policy 仅允许本次随机对象键的 name/cos:PutObject，不授予 bucket 列表、下载、删除或其他对象路径权限。

### 前端入口

新增：

~~~text
http://localhost:3000/direct-upload
~~~

页面不依赖 permanent SecretKey。Web Crypto 仅在浏览器内使用本次 STS 响应中的 TmpSecretKey；临时凭证过期后失效。

## CAM 必要配置

在 jenda-agent-cos-server 用户的权限页，通过图形化策略生成器增加一条：

~~~text
服务：STS
操作：GetFederationToken
资源：全部资源
效力：允许
~~~

只允许该后端账号调用 STS；它最终能签发的 COS 权限仍受其已有 agent/* COS 策略和本次单对象 STS policy 双重限制。

设置 30 分钟临时凭证：

~~~powershell
$env:AGENT_STORAGE_COS_STS_DURATION_SECONDS="1800"
~~~

## 验证

1. 关闭并在同一个环境变量 PowerShell 重启后端。
2. 前端设置 NEXT_PUBLIC_AGENT_API_BASE_URL 后启动。
3. 打开 /direct-upload。
4. 上传图片。
5. 浏览器 Network 应出现：
   - POST direct/tickets -> 200，响应含临时凭证。
   - PUT 到 *.cos.ap-shanghai.myqcloud.com -> 200。
   - POST direct/complete -> 200，响应 storageProvider=cos-sts。
6. COS 文件列表出现 agent/direct/showcase-direct-upload/<uuid>.png。
7. 页面图片可预览，证明返回的是签名 GET URL。

## 已验证

- 后端 Maven 编译通过，227 个源文件。
- 前端 TypeScript 与 Next.js 生产编译通过，路由包括 /direct-upload。
- 实际 STS 颁发和浏览器 PUT 需要在真实 CAM 权限与真实后端环境变量下验证。

## 下一步

1. 将 DirectUploadTicket 从内存 Map 改为 Redis，支持多实例 ECS 部署和过期清理。
2. 完成后将 COS objectKey、上传者、runId 写入 Workspace 资产表。
3. 回放时用 objectKey 重新签发 GET URL，而不是保存已过期的签名 URL。
4. 接入 JWT 后以真实 userId 替代演示 sessionId，限制用户只可写入自己的前缀。
