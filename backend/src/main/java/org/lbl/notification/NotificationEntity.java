package org.lbl.notification;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_notification")
public class NotificationEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long recipientId;
    private String type;
    private String title;
    private String content;
    private String businessType;
    private Long businessId;
    private LocalDateTime readTime;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdTime;
}
