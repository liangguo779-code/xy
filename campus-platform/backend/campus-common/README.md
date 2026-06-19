# campus-common

公共基础模块，提供各微服务共享的配置、实体、工具类和通用功能。以 JAR 库形式被其他模块依赖，不是独立微服务。

## 功能

- **统一响应包装** — `R<T>` 统一返回格式（code/msg/data）
- **Sa-Token 认证** — 登录校验拦截器、WebSocket 鉴权、角色权限实现
- **全局异常处理** — `BusinessException` + `GlobalExceptionHandler`
- **文件上传** — 图片（10MB）、视频（50MB）、通用文件（20MB），支持本地存储和阿里云 OSS
- **通知系统** — 通知列表、已读标记、未读计数
- **公告管理** — 平台公告 CRUD（管理员）
- **封禁管理** — 用户/IP 封禁，支持按类型（全站/交易/私信/论坛）和时长封禁
- **疯狂星期四** — 周期活动报名
- **系统配置** — 键值对配置管理

## 共享实体

| 实体 | 说明 |
|---|---|
| `Notification` | 用户通知 |
| `Announcement` | 平台公告 |
| `BanRecord` | 封禁记录 |
| `CrazyThursday` | 活动配置 |
| `SysConfig` | 系统配置 |

## 技术栈

Spring Boot 3.2.5、Sa-Token、MyBatis-Plus、Redis/Redisson、Knife4j
