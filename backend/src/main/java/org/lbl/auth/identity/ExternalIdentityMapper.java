package org.lbl.auth.identity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ExternalIdentityMapper extends BaseMapper<ExternalIdentityEntity> {
    @Select("SELECT * FROM sys_external_identity WHERE provider_key=#{provider} AND issuer=#{issuer} AND subject=#{subject} LIMIT 1")
    ExternalIdentityEntity find(@Param("provider") String provider, @Param("issuer") String issuer, @Param("subject") String subject);

    @Select("SELECT * FROM sys_external_identity WHERE user_id=#{userId} ORDER BY created_time")
    List<ExternalIdentityEntity> findByUserId(Long userId);

    @Delete("DELETE FROM sys_external_identity WHERE user_id=#{userId} AND provider_key=#{provider}")
    int deleteBinding(@Param("userId") Long userId, @Param("provider") String provider);

    @Delete("DELETE FROM sys_external_identity WHERE user_id=#{userId}")
    int deleteByUserId(Long userId);
}
