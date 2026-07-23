# Jenda Agent 开发状态：第八步，私有 COS 预签名下载 URL

> 阅读顺序：主架构文档 -> SSE/历史状态 -> 多模态状态 -> 模型工具链状态 -> COS 状态 -> 本文件。

## 已实现

CosSignedUrlService 为私有 COS 对象生成 COS V5 预签名 GET URL。

上传接口在 COS 模式下返回：

~~~json
{
  "storageProvider": "cos",
  "modelAccessible": true,
  "imageUrl": "https://bucket.cos.region.myqcloud.com/agent/...png?q-sign-algorithm=sha1&..."
}
~~~

默认有效期 1800 秒（30 分钟）。可选环境变量：

~~~powershell
$env:AGENT_STORAGE_COS_DOWNLOAD_EXPIRY_SECONDS="1800"
~~~

预签名 URL 可用于：

- 前端 Workspace 图片预览和下载。
- OpenAI 兼容视觉模型的 image_url content part。
- OCR、图像编辑和图像生成工具的 image_urls 请求字段。

COS 桶保持私有读写；不要为解决预览问题而设置公有读。

## 验证步骤

1. 在设置 COS 环境变量的同一个 PowerShell 重启 Spring Boot。
2. 打开 showcase 的 /upload 页面，上传一张图片。
3. Network 响应的 imageUrl 必须包含 q-sign-algorithm=sha1 和 q-signature。
4. 页面缩略图应能显示。
5. 在无痕窗口或新标签打开该 imageUrl，应在有效期内加载图片。
6. 过期后访问应返回 403，这属于预期安全行为。

## 重要边界

- 签名 URL 本身属于短期访问凭证，不提交 Git，不记录到长期配置。
- 当前历史消息可能保存已过期的 imageUrl。完成 Workspace 资产表后，应持久化 COS object key，并在历史回放时重新签发 URL。
- 当前上传仍为浏览器 -> 后端 -> COS。后续改为 STS 临时凭证直传，后端只生成路径、签发临时权限和保存资产元数据。
