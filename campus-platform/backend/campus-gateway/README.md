# campus-gateway

API 网关，基于 Spring Cloud Gateway，统一接收外部请求并路由到各微服务。

**端口**: 9000 | **服务名**: `campus-gateway`

## 功能

- **路由转发** — 根据路径前缀分发到对应微服务
- **CORS 处理** — 统一跨域配置
- **WebSocket 代理** — 聊天 WebSocket 连接转发
- **服务发现** — 基于 Nacos 的动态路由（`lb://`）

## 路由规则

| 路由 ID | 目标服务 | 路径匹配 |
|---|---|---|
| campus-user | campus-user-service | `/api/auth/**`, `/api/user/**`, `/api/address/**`, `/api/admin/users/**` |
| campus-trade | campus-trade-service | `/api/goods/**`, `/api/orders/**`, `/api/chat/**`, `/api/categories/**`, `/api/reviews/**`, `/api/favorites/**`, `/api/follow/**`, `/api/disputes/**`, `/api/delivery/**`, `/api/reports/**` |
| campus-forum | campus-forum-service | `/api/forum/**`, `/api/admin/forum/**` |
| campus-ai-consult | campus-ai-consult-service | `/api/ai/**`, `/api/admin/knowledge/**` |
| campus-admin | campus-admin-service | `/api/admin/dashboard/**`, `/api/admin/config/**`, `/api/admin/bans/**`, `/api/announcements/**` |
| campus-trade-ws | campus-trade-service | `/ws/chat/**` (WebSocket) |
| campus-uploads | campus-trade-service | `/uploads/**` (静态资源) |

## 依赖

Spring Cloud Gateway、Nacos Discovery、LoadBalancer

> 本模块不依赖 campus-common 和 campus-feign，是独立的基础设施服务。
