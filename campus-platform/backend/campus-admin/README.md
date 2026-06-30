# campus-admin

管理后台模块，负责用户封禁管理和系统配置管理。

## 功能概述

- 用户封禁（按用户/按 IP，支持多种封禁类型：全站/交易/私信/论坛）
- 解封
- 封禁记录查询
- 系统配置项管理（键值对形式）

## 接口列表

### AdminBanController `/api/admin/bans`（需管理员权限）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/bans/user` | 封禁用户（支持类型、原因、天数；不可封禁管理员和自己） |
| POST | `/api/admin/bans/ip` | 封禁 IP 地址 |
| PUT | `/api/admin/bans/{id}/unban` | 解除封禁 |
| GET | `/api/admin/bans` | 封禁记录列表（按目标类型/状态筛选，分页） |
| GET | `/api/admin/bans/check/user/{userId}` | 查询用户所有封禁状态（全站/交易/私信/论坛） |

### AdminConfigController `/api/admin/config`（需管理员权限）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/config` | 获取所有系统配置项 |
| PUT | `/api/admin/config` | 设置配置项（key、value、description） |

## 依赖

- `campus-common`（公共模块）
