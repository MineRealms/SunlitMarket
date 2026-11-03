package com.society.sunlitmarket.currency;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The payout hook must be unable to mint, overshoot or block a payout. */
class PayoutSinkTest {

    @AfterEach void clear() { Currency.payoutSink(null); }

    @Test void aSinkCanNeverDivertMoreThanThePayout() {
        Currency.payoutSink((player, amount, reason) -> amount + 500L);
        assertEquals(300L, Currency.divert(Currency.payoutSink(), null, 300L, "MARKET_SELL"));
    }

    @Test void aNegativeAnswerDivertsNothing() {
        Currency.payoutSink((player, amount, reason) -> -40L);
        assertEquals(0L, Currency.divert(Currency.payoutSink(), null, 300L, "DEPOSIT_BIN_PAYOUT"));
    }

    @Test void aThrowingSinkLeavesThePayoutIntact() {
        Currency.payoutSink((player, amount, reason) -> { throw new IllegalStateException("boom"); });
        assertEquals(0L, Currency.divert(Currency.payoutSink(), null, 300L, "NPC_DEMAND"));
    }

    @Test void partialDiversionIsPassedThrough() {
        Currency.payoutSink((player, amount, reason) -> 120L);
        assertEquals(120L, Currency.divert(Currency.payoutSink(), null, 300L, "MARKET_SELL"));
    }

    @Test void clearingTheSinkRestoresPlainPayouts() {
        Currency.payoutSink((player, amount, reason) -> 99L);
        Currency.payoutSink(null);
        assertNull(Currency.payoutSink());
    }

    @Test void twoFunctionFormKeepsRollbackAndDefaultsIt() {
        long[] rolledBack = {0L};
        PayoutSink sink = PayoutSink.of((player, amount, reason) -> 50L,
                (player, diverted, reason) -> rolledBack[0] = diverted);
        assertEquals(50L, sink.divert(null, 200L, "MARKET_SELL"));
        sink.rollback(null, 50L, "MARKET_SELL");
        assertEquals(50L, rolledBack[0]);
        PayoutSink withoutRollback = PayoutSink.of((player, amount, reason) -> 0L, null);
        withoutRollback.rollback(null, 10L, "MARKET_SELL");
    }
}
