package org.lbl.auth.identity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_local_credential")
public class LocalCredentialEntity {
    @TableId
    private Long userId;
    private String passwordHash;
    private Integer passwordChangeRequired;
    private Integer enabled;
    private LocalDateTime passwordChangedTime;
    private LocalDateTime updatedTime;
}
