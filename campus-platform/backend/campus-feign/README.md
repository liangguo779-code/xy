# campus-feign

Feign 远程调用接口模块，定义各微服务间的内部通信契约和数据传输对象（DTO）。以 JAR 库形式被消费方依赖，不是独立微服务。

## FeignClient 接口

| 接口 | 目标服务 | 路径前缀 |
|---|---|---|
| `UserFeignClient` | campus-user-service | `/internal/user` |
| `TradeFeignClient` | campus-trade-service | `/internal/trade` |
| `ForumFeignClient` | campus-forum-service | `/internal/forum` |
| `AiFeignClient` | campus-ai-consult-service | `/internal/ai` |

## DTO 类

```
com.campus.feign
├── user/dto/
│   ├── UserVO          — 完整用户信息
│   ├── UserSimpleVO    — 简要用户信息（id/昵称/头像/角色）
│   ├── UserStatsVO     — 用户统计（总数/活跃数）
│   └── AddressVO       — 收货地址
├── ai/dto/
│   └── AiStatsVO       — AI 统计（会话总数）
├── forum/dto/
│   └── PostStatsVO     — 帖子统计（总数/活跃数）
└── trade/dto/
    ├── GoodsStatsVO    — 商品统计（总数/在售/待审核）
    ├── OrderStatsVO    — 订单统计（总数/待处理/已完成）
    ├── DisputeStatsVO  — 纠纷统计（总数/待处理）
    └── ReportStatsVO   — 举报统计（总数/待处理）
```

## 设计原则

- 所有 DTO 使用 `@Data` + `implements Serializable`，字段使用包装类型
- 不暴露领域实体，仅返回必要的视图数据
- `/internal/**` 路径免登录校验，仅供服务间调用
