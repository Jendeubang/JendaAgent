# Step 34: Crispix 风格产品页面

## 已实现

- `/zh`：公开产品首页，包含品牌导航、Hero、Plan-Solve/ReAct 双引擎、工具入口和作品展示。
- `/zh/agent`：统一导航下的真实 Agent Studio，继续使用 Ant Design X Sender、JWT、SSE、COS 上传和会话历史能力。
- `/zh/tool`：工具箱卡片页，使用本地 `public/crispix` 素材，不依赖外部图片热链。
- `/zh/pricing`：月付/年付切换、套餐卡片、权益对比和企业版联系入口。
- `/`：默认进入 `/zh`。

## 架构

```text
Next.js App Router
  ├─ CrispixHeader 共享导航
  ├─ /zh 首页展示层
  ├─ /zh/tool 工具展示层
  ├─ /zh/pricing 价格展示层
  └─ /zh/agent -> Agent Studio 真实业务层
       ├─ Ant Design X Sender
       ├─ JWT 用户隔离
       ├─ SSE Plan-Solve / ReAct
       ├─ COS 直传与资产中心
       └─ MySQL 会话、事件、资产持久化
```

## 使用

```powershell
cd F:\JendaAgent\JendaAgent\showcase
corepack pnpm dev
```

生产容器：

```powershell
cd F:\JendaAgent\JendaAgent
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml up --build -d
```

访问 `http://localhost/zh`、`http://localhost/zh/agent`、`http://localhost/zh/tool` 和 `http://localhost/zh/pricing`。

## 后续优化

- 对 `/zh/agent` 的真实工作台继续做 Crispix 源站级视觉收敛，同时保持 SSE 和资产操作不变。
- 将工具箱卡片绑定到真实工具参数预填和 Agent Studio 工作流。
- 增加移动端导航抽屉、深色主题和国际化文案资源。
- 补充 Playwright 页面截图回归、无障碍检查和 Lighthouse 性能检查。
