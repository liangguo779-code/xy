# campus-admin

管理后台微服务，通过 Feign 聚合各服务统计数据，提供封禁管理和系统配置功能。

**端口**: 8085 | **服务名**: `campus-admin-service`

## 功能

- **数据看板** — 聚合用户、商品、订单、帖子等统计数据（Feign 调用）
- **封禁管理** — 封禁/解封用户和 IP，支持按类型（全站/交易/私信/论坛）封禁
- **系统配置** — 键值对配置的查看和修改

## API 概览

| 路径前缀 | 说明 | 权限 |
|---|---|---|
| `/api/admin/dashboard` | 数据看板 | 管理员 |
| `/api/admin/bans` | 封禁管理 | 管理员 |
| `/api/admin/config` | 系统配置 | 管理员 |
| `/api/announcements` | 公告管理 | 公开/管理员 |

## Feign 调用关系

```
campus-admin
  ├── UserFeignClient   → campus-user-service   (用户统计)
  ├── TradeFeignClient  → campus-trade-service   (商品/订单统计)
  ├── ForumFeignClient  → campus-forum-service   (帖子统计)
  └── AiFeignClient     → campus-ai-consult-service (AI 统计)
```

看板接口对各服务调用做了异常兜底，单个服务不可用时不影响整体返回。

## 依赖

campus-common、campus-feign
