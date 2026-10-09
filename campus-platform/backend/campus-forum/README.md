# campus-forum

论坛模块，负责校园社区帖子的发布、评论、点赞、收藏及管理员对论坛内容的管理。

## 功能概述

- 帖子发布、编辑、删除
- 帖子列表（按分类/关键词/时间范围筛选，最新/热度排序）
- 帖子全文检索（Elasticsearch 倒排索引，ik 中文分词，关键词高亮）
- 热门帖子榜（Redis zset 热度 + 列表缓存）
- 帖子点赞/取消点赞（切换）
- 帖子收藏/取消收藏
- 评论（支持嵌套回复）、删除评论（本人或帖子作者）
- 评论点赞（通知评论作者）
- 管理员：帖子审核（置顶/隐藏/删除/恢复）、评论管理、搜索索引重建

## 接口列表

### ForumController `/api/forum`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/forum/posts` | 帖子列表（分类/关键词/时间范围筛选，分页，`sortBy=latest\|hot`） |
| GET | `/api/forum/posts/hot` | 热门帖子 top-N（默认 10，上限 50） |
| GET | `/api/forum/posts/{id}` | 帖子详情（含点赞状态，浏览量 +1） |
| POST | `/api/forum/posts` | 发布帖子 |
| PUT | `/api/forum/posts/{id}` | 编辑帖子 |
| DELETE | `/api/forum/posts/{id}` | 删除帖子 |
| POST | `/api/forum/posts/{id}/like` | 点赞/取消点赞帖子（切换） |
| POST | `/api/forum/posts/{id}/favorite` | 收藏/取消收藏帖子（切换） |
| GET | `/api/forum/posts/{id}/favorite/status` | 检查帖子收藏状态 |
| GET | `/api/forum/favorites` | 我的收藏帖子（分页） |
| GET | `/api/forum/posts/mine` | 我发布的帖子（分页） |
| GET | `/api/forum/posts/{id}/comments` | 帖子评论列表（平铺，分页，登录时批量回填 liked） |
| GET | `/api/forum/posts/{id}/comments/tree` | 帖子评论树（嵌套结构，登录时回填 liked） |
| POST | `/api/forum/posts/{id}/comments` | 发表评论（支持嵌套回复） |
| POST | `/api/forum/comments/{id}/like` | 点赞/取消点赞评论（切换） |
| DELETE | `/api/forum/comments/{id}` | 删除评论（评论作者或帖子作者，连同楼中楼删除并回滚计数/热度） |

### AdminForumController `/api/admin/forum`（需管理员权限）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/forum/posts` | 帖子列表（按状态/关键词筛选） |
| PUT | `/api/admin/forum/posts/{id}/top` | 切换帖子置顶状态 |
| PUT | `/api/admin/forum/posts/{id}/hide` | 隐藏帖子 |
| DELETE | `/api/admin/forum/posts/{id}` | 永久删除帖子 |
| PUT | `/api/admin/forum/posts/{id}/restore` | 恢复已隐藏帖子 |
| POST | `/api/admin/forum/reindex` | 全量重建搜索索引（数据修复/ES 重建后使用） |
| GET | `/api/admin/forum/posts/{id}/comments` | 帖子评论列表（分页，含已删除） |
| DELETE | `/api/admin/forum/comments/{id}` | 删除评论（逻辑删除，回滚计数/热度） |

## Elasticsearch 搜索索引

- 索引名：`posts`（自动创建，1 分片 0 副本）。
- 字段映射：`title`/`content`（ik_max_word 建索引、ik_smart 检索）、`category`（keyword）、
  `userId`/`isTop`/`viewCount`/`likeCount`/`commentCount`（数值）、`createTime`（date）。
- 匹配规则：`multi_match` 打 `title^2 + content`，可叠加分类 term 过滤与创建时间 range 过滤。
- 高亮：title 整段高亮、content 100 字符片段，命中词用 `<em>` 包裹，回填到返回的 Post。

### 同步时机与计数回写

| 事件 | 同步动作 |
|------|----------|
| 发帖/编辑/置顶/恢复 | `indexPost` 全量写入/覆盖文档 |
| 删帖/隐藏 | `deletePost` 移除文档 |
| 浏览/点赞/评论/删评论 | `updateCounts` partial update 仅回写 view/like/comment 三个计数字段 |

计数字段实时回写保证 `sortBy=hot` 的检索结果不使用建档时的陈旧值。

### 同步失败处理（待修复队列）

ES 同步失败**不静默**：失败操作以 `index:{postId}` / `delete:{postId}` 格式记入 Redis list
`forum:es:pending-ops`（最多保留 1000 条、TTL 7 天），并由定时任务
`forum.es.repair-interval-ms`（默认 5 分钟）自动重放：

- `index` 重放时以库内最新状态为准——帖子正常则重建文档，已删除/隐藏则删除文档；
- `delete` 重放时删除文档；
- 单条失败会重新入队，留给下一轮继续重试。

### 降级与限流

- **ES 不可用降级**：索引初始化失败仅告警不阻断启动（`initIndex` catch `Exception`）；
  检索抛异常时自动降级为数据库 LIKE 查询，排序口径与 ES 路径保持一致：
  - `sortBy=hot`：`like*5 + comment*3 + view` 加权，`is_top/create_time` 兜底；
  - `sortBy=latest`（默认）：置顶优先 + 创建时间倒序；
  - 不传 `sortBy`：ES 按相关度（DB 降级路径按最新）。
- **深分页守卫**：`page*size` 超过 10000（ES `max_result_window`）直接报友好错误，
  提示增加筛选条件缩小结果集。

### reindex 运维

- 触发：`POST /api/admin/forum/reindex`（管理员）。
- 行为：删除并重建 `posts` 索引，按 **id 游标分页**（`WHERE id > lastId ORDER BY id LIMIT 500`）
  读库批量写入，避免全表进内存；成功后清空待修复队列（全量重建已覆盖全部差异）。
- 适用场景：ES 数据损坏重建、mapping 变更后迁移、待修复队列积压后的最终一致性兜底。

## 热门帖子榜

- **热度 zset** `forum:hot:posts`（member=postId，score=热度值）：
  发帖 +1、浏览 +1、点赞 ±5、评论 ±3，实时 ZINCRBY；zset 写入时刷新 30 天 TTL。
- **列表缓存** `forum:hot:posts:list`：cache-aside 固定缓存 top50 JSON，TTL 60 秒，
  命中后按入参 size 截断（size 归一到 [1, 50]），删帖时主动失效。
  固定 top50 缓存避免"首个请求的 size 决定缓存量"的截断问题。
- **时间衰减**（容量治理）：每日 03:00 全量分数乘以衰减系数 0.8，低于 0.5 分的成员移除，
  旧互动权重随时间下降，新帖有机会进榜。
- **点赞状态 key**（`post_like:{postId}:{userId}`、`comment_like:{commentId}:{userId}`）：
  不加 TTL（过期会让用户重复点赞使计数虚高），每日 03:30 扫描清理指向已删帖/已删评论的无主 key。

## 依赖

- `campus-common`（公共模块：异常、通知、封禁、Elasticsearch 客户端等）
