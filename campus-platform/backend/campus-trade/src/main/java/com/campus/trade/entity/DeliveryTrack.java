package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 配送轨迹/节点记录，对应 delivery_track 表，记录跑腿员位置与操作节点 */
@Data
@TableName("delivery_track")
public class DeliveryTrack {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属配送单 ID，关联 delivery_order.id */
    private Long deliveryId;
    /** 上报位置的跑腿员用户 ID */
    private Long runnerId;
    /** 纬度 */
    private BigDecimal latitude;
    /** 经度 */
    private BigDecimal longitude;
    /** 位置的文字地址描述 */
    private String address;
    /** 操作动作：如取货、送达等节点标识 */
    private String action;
    /** 节点凭证照片 URL */
    private String photoUrl;
    /** 上报时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
