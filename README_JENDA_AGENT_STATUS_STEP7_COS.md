# Jenda Agent 开发状态：第七步，腾讯云 COS 存储

> 阅读顺序：主架构文档 -> SSE/历史状态 -> 多模态状态 -> 模型工具链状态 -> 本文件。

## 已实现

新增 CosAgentImageStorage，在设置 agent.storage.provider=cos 时替换 LocalAgentImageStorage。

COS 上传路径：

~~~text
agent/yyyy/MM/dd/<asset-id>.<extension>
~~~

上传通过 COS XML API 的 V5 签名调用 PUT Object，不需要把腾讯 COS SDK 引入当前上游工程。服务端使用以下环境变量：

~~~powershell
$env:AGENT_STORAGE_PROVIDER="cos"
$env:AGENT_STORAGE_COS_BUCKET="jenda-agent-1455545317"
$env:AGENT_STORAGE_COS_REGION="ap-shanghai"
$env:AGENT_STORAGE_COS_SECRET_ID="CAM 子用户 SecretId"
$env:AGENT_STORAGE_COS_SECRET_KEY="CAM 子用户 SecretKey"
$env:AGENT_STORAGE_COS_PREFIX="agent/"
~~~

上传成功时，COS 专用响应适配器返回：

~~~json
{
  "imageUrl": "https://jenda-agent-1455545317.cos.ap-shanghai.myqcloud.com/agent/2026/07/<uuid>.png",
  "modelAccessible": true,
  "storageProvider": "cos"
}
~~~

不提交 SecretId、SecretKey、带签名 URL 或真实用户上传图片到 Git。

## 权限要求

CAM 策略只应覆盖：

~~~text
qcs::cos:ap-shanghai:uid/1455545317:jenda-agent-1455545317/agent/*
~~~

当前服务端最少需要 PutObject、GetObject、HeadObject。暂不授予 DeleteObject、ListBucket 或全桶资源权限。

## 验证步骤

1. 在同一个 PowerShell 窗口设置上述六个环境变量。
2. 启动后端。
3. 打开 http://localhost:3000/upload，选择一张小于 10 MB 的 PNG 或 JPG。
4. 成功响应的 storageProvider 必须是 cos，modelAccessible 必须是 true，imageUrl 必须以 COS 域名开头。
5. COS 控制台的文件列表应出现 agent/yyyy/MM/dd 路径下的新对象。

## 当前实现边界

- 当前是“浏览器 -> 后端 -> COS”代理上传，适合开发和中小文件。
- 下一阶段改为“浏览器 -> STS 临时凭证 -> COS”直传，后端只签发路径和记录资产。
- 若模型供应商不能拉取私有 COS 地址，需要提供短时预签名 GET URL；不要将桶改为公有读。
- 当前工作区不能更新既有 Controller 源文件，使用 Spring 替换器返回 COS URL。文件系统恢复后，应将该行为直接合并回 AgentMediaController，删除替换器和扫描排除器。
