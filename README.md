# 政务协同办公系统 · 公文流转引擎

面向机关单位的公文全流程引擎：**拟稿 → 审核 → 会签 → 签发 → 归档**。
首版交付主链路：模板套红 — 流程配置 — 电子签章 — 会签/或签/串签 — 待办已办 — 归档查询。

## 功能清单

| 模块 | 能力 |
| --- | --- |
| 红头模板 | 发文机关标志（红头大字）、文号前缀、落款署名，绑定办理流程；GB/T 9704 风格套红 PDF（红头、文号、红线、密级标注、标题、主送、正文、附件说明、落款、成文日期、抄送、页码） |
| 流程配置 | 可视化节点编排（增删、排序）；节点类型 审核/会签/签发；办理方式 会签(全部)/或签(任一)/串签(依次)；办理人 指定人员/按岗位/部门负责人；节点办理时限；保存即部署新版本，在办件不受影响 |
| 电子签章 | 单位章/个人章；程序生成演示章图（环形名称+五角星）或上传 PNG；定位盖章（页码+坐标）、骑缝章（每页右缘切条）；每次盖章生成新 PDF 版本并记录 SHA-256，支持验章 |
| 公文流转 | 拟稿/草稿、提交、同意、退回上一步、退回拟稿（文号作废、补正重报）、催办、超时自动提醒、办结自动归档 |
| 权限 | 按组织、岗位、密级（公开/内部/秘密/机密）控制可见与办理；管理员全量可见 |
| 留痕 | 全链路操作留痕（拟稿、提交、办理、退回、催办、盖章、套红、超时、归档），时间线可追溯 |
| 通知 | 待办/退回/催办/超时/归档/督办六类通知，未读角标，WebSocket/Redis 推送 |
| 效能分析 | 定时从 Camunda 历史表（ACT_HI_*）与业务库抽取至分析表（doc_analysis、reminder_log），只读历史数据：办理时效统计（部门/岗位/公文类型维度的拟稿时长、节点停留、会签周期，柱状图+趋势线）、退回热点（原因归类占比+词云）、督办单（超时未办结/催办≥3次自动生成并推送分管领导）、效能排行（部门/个人月度排名，CSV 导出） |

## 技术栈与分层

- **后端** `server/`：Spring Boot 3 + **Camunda 7**（内嵌流程引擎，节点动态生成 BPMN 多实例用户任务）+ Spring Data JPA；默认 H2 文件库，可切 MySQL
- **存储**：默认本地磁盘，可切 **MinIO**（正文/附件/套红 PDF/章图）
- **推送**：默认 STOMP WebSocket，可切 **Redis** Pub/Sub（多实例广播）；token 默认内存，可切 Redis
- **PDF**：Apache PDFBox（套红、盖章、骑缝章、哈希验章）
- **前端** `web/`：React 18 + Vite + Ant Design 5（办文端 + 配置端）

## 快速启动

要求：JDK 17+、Maven 3.6+、Node 18+。无需其他中间件（默认 H2 + 本地存储 + 内存 token）。

```bash
# 1. 后端（首次启动自动初始化演示数据，端口 8080）
cd server
mvn spring-boot:run
# 或：mvn package -DskipTests && java -jar target/gongwen-server.jar

# 2. 前端（端口 5173，已配置 /api 代理）
cd web
npm install
npm run dev
```

打开 http://localhost:5173 ，演示账号：

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| admin | admin123 | 管理员（模板/流程/签章/用户/部门配置） |
| zhangsan | 123456 | 拟稿（市政府办公室 科员） |
| lisi | 123456 | 审核（办公室主任） |
| wangwu / zhaoliu | 123456 | 会签（发改委/财政局 科长） |
| qianqi | 123456 | 签发（副局长，持单位公章与个人章） |

**主链路演示**：zhangsan 拟稿提交 → lisi 待办审核 → wangwu、zhaoliu 会签 → qianqi 签发（此时自动分配文号、生成套红 PDF，可盖章/验章）→ 办结自动归档 → 归档查询可检索。

## 测试

```bash
cd server && mvn test     # 7 个集成测试：主链路/会签或签串签/退回/权限/冒烟/效能分析
cd web && npm run build   # 前端类型检查 + 构建
```

## 配置（server/src/main/resources/application.yml）

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `app.storage.type` | `local` | `minio` 切换对象存储（配套 `app.storage.minio.*`） |
| `app.token-store` | `memory` | `redis` 切换集群 token（配套 `app.redis.*`） |
| `app.push` | `stomp` | `redis` 切换多实例待办广播 |
| `app.pdf.font-path` | 空 | 指定公文 TTF/TTC 字体；默认用内置 `fonts/gongwen.ttc` |
| 数据源 | H2 文件库 `./data/gwdb` | 切 MySQL：改 `spring.datasource.*`（驱动已含） |

生产部署请务必：修改演示账号口令、切换 MySQL/MinIO/Redis、配置 HTTPS。

## 目录结构

```
server/src/main/java/com/gov/gw/
├── auth/      登录、token、鉴权拦截器
├── doc/       公文核心：流转服务、任务对账、权限、编号、提醒调度、通知
├── flow/      流程配置 → 动态 BPMN → Camunda 部署
├── pdf/       套红 PDF 引擎（GB/T 9704 版式）
├── seal/      签章、章图生成、PDF 盖章（定位/骑缝）、验章
├── storage/   存储抽象（local / minio）
├── notify/    待办推送（stomp / redis）
├── org/       部门、用户
└── init/      演示数据初始化
web/src/
├── pages/           待办/已办/拟稿/我的公文/详情/查询/归档/通知
└── pages/admin/     模板/流程/签章/用户/部门 配置端
```

## 关键设计

- **引擎协作**：业务动作（提交/办理/退回）由 `DocService` 发起，动作后调用 `syncTasks` 将 Camunda 活动任务对账到待办表，行为确定、便于测试；退回用 `ProcessInstanceModification` 取消当前多实例并重启上一节点。
- **流程快照**：提交时把节点配置快照存入公文，流程改版不影响在办件；文号在到达签发节点时分配，退回拟稿作废不回收。
- **验章**：每次盖章产出新 PDF 并记录 SHA-256；验章逐条核对历史版本哈希，并校验当前文件是否等于最后一次盖章版本。
