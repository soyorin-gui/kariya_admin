package org.lbl.intranet.transaction.field.service;

import org.junit.jupiter.api.Test;
import org.lbl.common.exception.BusinessException;
import org.lbl.intranet.transaction.entity.TransactionAssetEntity;
import org.lbl.intranet.transaction.field.entity.TransactionMessageFieldEntity;
import org.lbl.intranet.transaction.field.mapper.TransactionMessageFieldMapper;
import org.lbl.intranet.transaction.field.request.TransactionMessageFieldRequest;
import org.lbl.intranet.transaction.mapper.TransactionAssetMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 报文节点父级选择的边界校验。
 * <p>
 * 重点在环：把节点挂到自己的后代下面不会报错，但会让前端建树时整棵子树找不到根而静默消失，
 * 所以必须在写入前被拒绝。这里不启动 Spring，只验证服务层的判定逻辑。
 */
class TransactionMessageFieldServiceTest {
    private static final long TRANSACTION_ID = 1L;
    private static final long SELF_ID = 10L;
    private static final String SIDE = "REQUEST";

    /** 现有结构：10 是根，11 是 10 的子节点，12 是 11 的子节点。 */
    private static List<TransactionMessageFieldEntity> tree() {
        return List.of(node(SELF_ID, 0L), node(11L, SELF_ID), node(12L, 11L));
    }

    private static TransactionMessageFieldEntity node(long id, long parentId) {
        TransactionMessageFieldEntity e = new TransactionMessageFieldEntity();
        e.setId(id);
        e.setTransactionId(TRANSACTION_ID);
        e.setMessageSide(SIDE);
        e.setParentId(parentId);
        return e;
    }

    private static TransactionMessageFieldRequest request(long parentId) {
        return new TransactionMessageFieldRequest(parentId, "FIELD", "子字段", "childField",
                "String", "32", 0, null, 10);
    }

    private static TransactionMessageFieldService service(long selfParentId, List<TransactionMessageFieldEntity> rows) {
        TransactionMessageFieldMapper mapper = mock(TransactionMessageFieldMapper.class);
        TransactionAssetMapper transactions = mock(TransactionAssetMapper.class);
        when(transactions.selectById(any())).thenReturn(new TransactionAssetEntity());
        when(mapper.selectOne(any())).thenReturn(node(SELF_ID, selfParentId));
        when(mapper.selectList(any())).thenReturn(rows);
        return new TransactionMessageFieldService(mapper, transactions);
    }

    @Test
    void rejectsParentThatIsItsOwnDescendant() {
        TransactionMessageFieldService service = service(0L, tree());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(TRANSACTION_ID, SIDE, SELF_ID, request(12L)));

        assertEquals("父节点不能选择自身的子节点", error.getMessage());
    }

    @Test
    void rejectsSelfAsParent() {
        TransactionMessageFieldService service = service(0L, tree());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(TRANSACTION_ID, SIDE, SELF_ID, request(SELF_ID)));

        assertEquals("父节点不能选择自身", error.getMessage());
    }

    @Test
    void rejectsUnknownParent() {
        TransactionMessageFieldService service = service(0L, tree());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(TRANSACTION_ID, SIDE, SELF_ID, request(99L)));

        assertEquals("父节点不存在", error.getMessage());
    }

    @Test
    void allowsMovingToRootAndToASiblingBranch() {
        List<TransactionMessageFieldEntity> rows = List.of(node(SELF_ID, 11L), node(11L, 0L), node(12L, 0L));

        assertEquals(Long.valueOf(0L), service(11L, rows).update(TRANSACTION_ID, SIDE, SELF_ID, request(0L)).parentId());
        assertEquals(Long.valueOf(12L), service(11L, rows).update(TRANSACTION_ID, SIDE, SELF_ID, request(12L)).parentId());
    }
}
