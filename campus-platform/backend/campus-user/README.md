# campus-user

用户模块，负责用户认证、个人资料管理、地址管理及管理员对用户的管理。

## 功能概述

- 用户登录、注册、登出
- 密码重置（验证码）
- 个人信息查看与修改
- 收货地址管理（增删改查、默认地址）
- 管理员：用户列表、启停账号、角色变更、用户统计

## 接口列表

### AuthController `/api/auth`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 用户登录，返回 Token |
| POST | `/api/auth/register` | 用户注册 |
| GET | `/api/auth/me` | 获取当前登录用户信息 |
| POST | `/api/auth/logout` | 用户登出 |
| POST | `/api/auth/send-code` | 发送密码重置验证码 |
| POST | `/api/auth/reset-password` | 通过验证码重置密码 |
| POST | `/api/auth/bootstrap-admin` | 初始化管理员（仅无管理员时可用） |

### UserController `/api/user`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/user/{id}` | 获取用户公开资料（如卖家主页） |
| PUT | `/api/user/profile` | 修改当前用户资料 |
| PUT | `/api/user/password` | 修改当前用户密码 |
| DELETE | `/api/user/account` | 注销（停用）当前用户账号 |

### AddressController `/api/address`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/address` | 获取当前用户地址列表 |
| GET | `/api/address/{id}` | 获取地址详情 |
| GET | `/api/address/default` | 获取当前用户默认地址 |
| POST | `/api/address` | 新增地址 |
| PUT | `/api/address` | 修改地址 |
| DELETE | `/api/address/{id}` | 删除地址 |

### AdminUserController `/api/admin/users`（需管理员权限）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/users` | 用户列表（支持关键词/角色/状态筛选，分页） |
| PUT | `/api/admin/users/{id}/status` | 启用/停用用户账号 |
| PUT | `/api/admin/users/{id}/role` | 变更用户角色（0=普通用户, 1=管理员, 2=跑腿员） |
| GET | `/api/admin/users/stats` | 用户统计（总数、活跃数、跑腿员数） |

## 依赖

- `campus-common`（公共模块：实体、Mapper、通用服务、异常处理）
