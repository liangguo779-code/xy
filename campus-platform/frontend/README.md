# frontend

校园二手交易平台前端，基于 Vue 3 + Element Plus 构建的单页应用。

## 技术栈

| 层面 | 技术 |
|---|---|
| 框架 | Vue 3（Composition API + `<script setup>`） |
| 构建工具 | Vite 5 |
| UI 组件库 | Element Plus 2.6（中文国际化） |
| 状态管理 | Pinia（user / chat 两个 Store） |
| 路由 | Vue Router 4（HTML5 History 模式） |
| HTTP 客户端 | Axios（自动携带 Bearer Token，统一错误处理） |
| 样式 | SCSS，768px 断点移动端适配 |
| Markdown 渲染 | marked（AI 对话气泡） |

## 启动

```bash
npm install
npm run dev        # 开发服务器 → http://localhost:5173
npm run build      # 生产构建
```

Vite 开发代理：`/api`、`/uploads` → `http://localhost:9000`，`/ws` → `ws://localhost:9000`

## 页面结构

### 用户端

| 路由 | 页面 | 功能 |
|---|---|---|
| `/goods` | 商品列表 | 二手市场首页，分类筛选、搜索、推荐 |
| `/goods/:id` | 商品详情 | 查看详情、发起聊天、下单 |
| `/seller/:userId` | 卖家主页 | 卖家信息、在售商品 |
| `/my-goods` | 我的商品 | 发布管理 |
| `/orders` | 订单列表 | 我的买入/卖出 |
| `/orders/:id` | 订单详情 | 订单状态、确认/取消/评价 |
| `/chat` | 聊天列表 | 会话列表、未读消息 |
| `/chat/:sessionId` | 聊天室 | WebSocket 实时消息、撤回 |
| `/forum` | 论坛 | 帖子列表、搜索 |
| `/forum/:id` | 帖子详情 | 评论（嵌套回复）、点赞、收藏 |
| `/ai` | AI 问答 | SSE 流式对话、来源引用、会话管理 |
| `/address` | 收货地址 | 地址 CRUD、默认地址 |
| `/profile` | 个人资料 | 编辑资料、修改密码 |
| `/favorites` | 我的收藏 | 收藏的商品 |
| `/browse-history` | 浏览记录 | 浏览历史 |
| `/dispatch` | 派单大厅 | 跑腿员抢单、配送轨迹 |
| `/notifications` | 通知中心 | 消息通知、已读管理 |
| `/crazy-thursday` | 疯狂星期四 | 活动报名 |

### 管理后台

| 路由 | 页面 | 功能 |
|---|---|---|
| `/admin/dashboard` | 数据看板 | 统计概览 |
| `/admin/users` | 用户管理 | 启用/禁用、角色修改 |
| `/admin/goods` | 商品审核 | 审核通过/驳回、强制下架 |
| `/admin/orders` | 订单监控 | 订单列表、统计 |
| `/admin/disputes` | 纠纷处理 | 纠纷列表、处理 |
| `/admin/reports` | 举报管理 | 举报列表、处理 |
| `/admin/bans` | 封禁管理 | 封禁/解封用户和 IP |
| `/admin/forum` | 论坛管理 | 帖子置顶/隐藏/删除 |
| `/admin/config` | 系统配置 | 键值对配置 |
| `/admin/knowledge` | 知识库 | AI 知识文档管理 |

## 状态管理

- **useUserStore** — token、用户信息持久化（localStorage），登录/登出/获取用户信息
- **useChatStore** — 未读消息计数、WebSocket 连接管理、自动重连

## 目录结构

```
src/
├── api/            # 14 个 API 模块（auth/goods/order/chat/forum/ai/...）
├── components/     # 公共组件（Layout/AdminLayout/MobileTabBar/CommentItem/SkeletonCard）
├── router/         # 路由定义 + 登录守卫
├── stores/         # Pinia 状态（user/chat）
├── views/
│   ├── user/       # 用户相关页面
│   ├── trade/      # 交易相关页面
│   ├── chat/       # 聊天页面
│   ├── forum/      # 论坛页面
│   ├── ai/         # AI 问答页面
│   ├── delivery/   # 配送页面
│   └── admin/      # 管理后台页面
└── style/          # 全局样式
```
