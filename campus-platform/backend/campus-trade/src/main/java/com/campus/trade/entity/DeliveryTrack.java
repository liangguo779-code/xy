package com.campus.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("delivery_track")
public class DeliveryTrack {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deliveryId;
    private Long runnerId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String address;
    private String action;
    private String photoUrl;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
