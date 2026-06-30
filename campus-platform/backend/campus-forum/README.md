# campus-forum

论坛模块，负责校园社区帖子的发布、评论、点赞、收藏及管理员对论坛内容的管理。

## 功能概述

- 帖子发布、编辑、删除
- 帖子列表（按分类/关键词筛选）
- 帖子点赞/取消点赞（切换）
- 帖子收藏/取消收藏
- 评论（支持嵌套回复）
- 评论点赞
- 管理员：帖子审核（置顶/隐藏/删除/恢复）、评论管理

## 接口列表

### ForumController `/api/forum`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/forum/posts` | 帖子列表（支持分类/关键词筛选，分页） |
| GET | `/api/forum/posts/{id}` | 帖子详情（含点赞状态） |
| POST | `/api/forum/posts` | 发布帖子 |
| PUT | `/api/forum/posts/{id}` | 编辑帖子 |
| DELETE | `/api/forum/posts/{id}` | 删除帖子 |
| POST | `/api/forum/posts/{id}/like` | 点赞/取消点赞帖子（切换） |
| POST | `/api/forum/posts/{id}/favorite` | 收藏/取消收藏帖子（切换） |
| GET | `/api/forum/posts/{id}/favorite/status` | 检查帖子收藏状态 |
| GET | `/api/forum/favorites` | 我的收藏帖子（分页） |
| GET | `/api/forum/posts/mine` | 我发布的帖子（分页） |
| GET | `/api/forum/posts/{id}/comments` | 帖子评论列表（平铺，分页） |
| GET | `/api/forum/posts/{id}/comments/tree` | 帖子评论树（嵌套结构） |
| POST | `/api/forum/comments/{id}/like` | 点赞/取消点赞评论（切换） |
| POST | `/api/forum/posts/{id}/comments` | 发表评论（支持嵌套回复） |

### AdminForumController `/api/admin/forum`（需管理员权限）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/forum/posts` | 帖子列表（按状态/关键词筛选） |
| PUT | `/api/admin/forum/posts/{id}/top` | 切换帖子置顶状态 |
| PUT | `/api/admin/forum/posts/{id}/hide` | 隐藏帖子 |
| DELETE | `/api/admin/forum/posts/{id}` | 永久删除帖子 |
| PUT | `/api/admin/forum/posts/{id}/restore` | 恢复已隐藏帖子 |
| GET | `/api/admin/forum/posts/{id}/comments` | 帖子评论列表（分页） |
| DELETE | `/api/admin/forum/comments/{id}` | 软删除（隐藏）评论 |

## 依赖

- `campus-common`（公共模块）
