# campus-user

用户微服务，负责用户账户、认证登录、个人资料、收货地址和后台用户管理。

**端口**: 8081 | **服务名**: `campus-user-service`

## 功能

- **认证** — 注册、登录（Sa-Token）、登出、密码重置（验证码）、初始管理员引导
- **用户管理** — 查看资料、修改资料、修改密码、注销账号
- **收货地址** — 地址 CRUD、设置默认地址
- **后台管理** — 用户列表（搜索/筛选）、启用/禁用用户、修改角色、用户统计

## API 概览

| 路径前缀 | 说明 | 权限 |
|---|---|---|
| `/api/auth` | 认证相关 | 公开 |
| `/api/user` | 用户资料 | 登录 |
| `/api/address` | 收货地址 | 登录 |
| `/api/admin/users` | 后台用户管理 | 管理员 |
| `/internal/user` | Feign 内部接口 | 服务间 |

## 实体

| 实体 | 说明 |
|---|---|
| `User` | 用户（username/password/nickname/avatar/phone/dormitory/role/status） |
| `Address` | 收货地址（contactName/phone/building/detail/isDefault） |

## 角色

- `0` — 普通用户
- `1` — 管理员
- `2` — 跑腿员

## 依赖

campus-common、campus-feign
