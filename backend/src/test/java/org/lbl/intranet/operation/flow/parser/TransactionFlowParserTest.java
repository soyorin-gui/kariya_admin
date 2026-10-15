package org.lbl.intranet.operation.flow.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransactionFlowParserTest {
    private final TransactionFlowParser parser = new TransactionFlowParser();

    @Test
    void parsesConfirmedTenFieldFormat() {
        var result = parser.parse("00330001,21.64.172.67-S12002338,BF57,2026-09-23 20:51:47,2026-09-23 20:51:47,124.0,N,04670ERR0001,2026-09-23,00332609230007100263198031");

        assertTrue(result.isPresent());
        assertEquals("00330001", result.orElseThrow().callerId());
        assertEquals("BF57", result.orElseThrow().transactionCode());
        assertEquals("124.0", result.orElseThrow().elapsedTime().toPlainString());
        assertEquals("N", result.orElseThrow().responseStatus());
    }

    @Test
    void ignoresMalformedRecords() {
        assertTrue(parser.parse("字段数量不足,N").isEmpty());
        assertTrue(parser.parse("00330001,address,BF57,wrong-time,2026-09-23 20:51:47,124.0,N,CODE,2026-09-23,FLOW").isEmpty());
        assertTrue(parser.parse("00330001,address,BF57,2026-09-23 20:51:47,2026-09-23 20:51:47,124.0,X,CODE,2026-09-23,FLOW").isEmpty());
    }
}
