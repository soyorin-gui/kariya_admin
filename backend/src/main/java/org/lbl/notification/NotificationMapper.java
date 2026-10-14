package org.lbl.notification;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface NotificationMapper extends BaseMapper<NotificationEntity> {
    @Select("SELECT * FROM sys_notification WHERE recipient_id=#{userId} ORDER BY created_time DESC, id DESC LIMIT #{limit}")
    List<NotificationEntity> latest(@Param("userId") Long userId, @Param("limit") int limit);
    @Select("SELECT COUNT(1) FROM sys_notification WHERE recipient_id=#{userId} AND read_time IS NULL")
    long unread(Long userId);
    @Update("UPDATE sys_notification SET read_time=NOW() WHERE id=#{id} AND recipient_id=#{userId} AND read_time IS NULL")
    int markRead(@Param("id") Long id, @Param("userId") Long userId);
    @Update("UPDATE sys_notification SET read_time=NOW() WHERE recipient_id=#{userId} AND read_time IS NULL")
    int markAllRead(Long userId);
}
