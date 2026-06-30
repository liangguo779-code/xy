# campus-app

应用启动与聚合模块，是整个后端的入口。负责启动 Spring Boot 应用，聚合所有业务模块，并提供管理后台仪表盘、公告管理、疯狂星期四活动及模块间内部调用接口。

## 功能概述

- Spring Boot 应用入口（`CampusApplication`）
- 聚合所有业务模块（campus-user、campus-trade、campus-forum、campus-ai、campus-admin）
- 管理后台仪表盘（聚合统计数据）
- 公告管理（发布/编辑/删除）
- 疯狂星期四活动（状态查询/报名）
- 模块间内部调用接口（Internal Feign-style，供模块间 RPC 调用）

## 接口列表

### AdminController `/api/admin`（需管理员权限）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/dashboard` | 管理后台仪表盘（聚合用户、商品、订单、帖子、纠纷、举报统计） |

### AnnouncementController `/api/announcements`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/announcements` | 获取有效公告列表（用户端，分页） |
| GET | `/api/announcements/all` | 获取所有公告（管理员端） |
| POST | `/api/announcements` | 创建公告（需管理员权限） |
| PUT | `/api/announcements/{id}` | 编辑公告（需管理员权限） |
| DELETE | `/api/announcements/{id}` | 删除公告（需管理员权限） |

### CrazyThursdayController `/api/crazy-thursday`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/crazy-thursday/status` | 获取疯狂星期四活动状态 |
| POST | `/api/crazy-thursday/register` | 报名参加疯狂星期四活动 |

### InternalUserController `/internal/user`（内部接口）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/internal/user/{id}` | 根据 ID 获取用户 VO |
| POST | `/internal/user/batch` | 批量获取用户信息 |
| GET | `/internal/user/{id}/nickname` | 获取用户昵称 |
| GET | `/internal/user/{id}/simple` | 获取用户简要信息（ID、昵称、头像、角色） |
| GET | `/internal/user/address/{userId}/default` | 获取用户默认地址 |
| GET | `/internal/user/address/{userId}/{addressId}` | 获取指定地址 |
| GET | `/internal/user/stats` | 获取用户统计（总数、活跃数） |

### InternalTradeController `/internal/trade`（内部接口）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/internal/trade/stats/goods` | 获取商品统计（总数、在售、待审核） |
| GET | `/internal/trade/stats/orders` | 获取订单统计（总数、待处理、已完成） |
| GET | `/internal/trade/stats/disputes` | 获取纠纷统计（总数、待处理） |
| GET | `/internal/trade/stats/reports` | 获取举报统计（总数、待处理） |

### InternalForumController `/internal/forum`（内部接口）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/internal/forum/stats` | 获取论坛帖子统计（总数、活跃） |

### InternalAiController `/internal/ai`（内部接口）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/internal/ai/stats` | 获取 AI 会话统计（总会话数） |

## 依赖

- `campus-user`
- `campus-trade`
- `campus-forum`
- `campus-ai`
- `campus-admin`
- Spring Boot Actuator（健康检查、应用信息）
