# campus-forum

校园论坛微服务，提供帖子发布、评论、点赞、收藏等社区交流功能。

**端口**: 8083 | **服务名**: `campus-forum-service`

## 功能

- **帖子** — 发布/编辑/删除、分类浏览、关键词搜索、浏览计数
- **评论** — 支持嵌套回复（树形结构）、点赞
- **互动** — 帖子点赞/取消点赞、收藏/取消收藏
- **后台管理** — 帖子置顶、隐藏/恢复、删除、评论管理

## API 概览

| 路径前缀 | 说明 | 权限 |
|---|---|---|
| `/api/forum/posts` | 帖子列表/详情 | 公开 |
| `/api/forum/posts/{id}/comments` | 评论（平铺/树形） | 公开 |
| `/api/forum/posts` (POST/PUT/DELETE) | 帖子 CRUD | 登录 |
| `/api/forum/posts/{id}/like` | 点赞 | 登录 |
| `/api/forum/posts/{id}/favorite` | 收藏 | 登录 |
| `/api/forum/favorites` | 我的收藏 | 登录 |
| `/api/forum/posts/mine` | 我的帖子 | 登录 |
| `/api/admin/forum` | 后台管理 | 管理员 |
| `/internal/forum` | Feign 内部接口 | 服务间 |

## 实体

| 实体 | 说明 |
|---|---|
| `Post` | 帖子（title/content/category/images/viewCount/likeCount/commentCount/isTop/status） |
| `Comment` | 评论（支持 parentId 嵌套回复、images、likeCount） |
| `PostFavorite` | 帖子收藏记录 |

## 依赖

campus-common、campus-feign
