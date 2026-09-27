package org.lbl.auth.identity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_external_identity")
public class ExternalIdentityEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String providerKey;
    private String issuer;
    private String subject;
    private String displayNameSnapshot;
    private String emailSnapshot;
    private LocalDateTime createdTime;
    private LocalDateTime lastLoginTime;
}
