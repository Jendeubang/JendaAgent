# 第九步补充：STS Region

腾讯云 GetFederationToken API 要求 Region 公共参数。StsRegionHeaderInjector 仅向 sts.tencentcloudapi.com 请求增加：

~~~text
X-TC-Region: ap-shanghai
~~~

区域值读取 agent.storage.cos.region，因此无需新增环境变量。

该请求头不包含永久密钥，临时凭证仍由单对象 COS policy 约束。
