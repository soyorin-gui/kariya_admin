package org.lbl.intranet.transaction.field.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.lbl.intranet.transaction.field.entity.TransactionMessageFieldEntity;

@Mapper
public interface TransactionMessageFieldMapper extends BaseMapper<TransactionMessageFieldEntity> {
    @Delete("DELETE FROM intranet_transaction_message_field WHERE transaction_id = #{transactionId}")
    int deleteByTransactionId(@Param("transactionId") Long transactionId);

    @Select("""
            SELECT COUNT(1)
            FROM intranet_transaction_message_field
            WHERE transaction_id = #{transactionId}
              AND message_side = #{side}
              AND parent_id = #{parentId}
              AND field_name_en = #{fieldNameEn}
              AND id != #{excludeId}
            """)
    long countBySiblingFieldName(@Param("transactionId") Long transactionId,
                                 @Param("side") String side,
                                 @Param("parentId") Long parentId,
                                 @Param("fieldNameEn") String fieldNameEn,
                                 @Param("excludeId") long excludeId);
}
