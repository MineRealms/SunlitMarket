package com.society.sunlitmarket.treasury;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static com.society.sunlitmarket.treasury.TreasuryData.Account.CASH;
import static com.society.sunlitmarket.treasury.TreasuryData.Account.RESERVE;
import static org.junit.jupiter.api.Assertions.*;

class TreasuryDataTest {
    private static final TreasuryData.Policy POLICY = new TreasuryData.Policy(1000, 10_000, 1000, 2000, 300, 500);

    private static TreasuryData fresh() {
        TreasuryData data = TreasuryData.create(POLICY);
        data.roll(10, POLICY);
        return data;
    }

    @Test void firstRollSeedsWithoutRefill() {
        TreasuryData data = fresh();
        assertEquals(1000, data.cash());
        assertEquals(10_000, data.reserve());
    }

    @Test void refillIsCappedByDailyRefillAndRunsOncePerDay() {
        TreasuryData data = fresh();
        assertTrue(data.withdraw(CASH, 900, "TEST"));
        data.roll(10, POLICY);
        assertEquals(100, data.cash(), "same day: no refill");
        data.roll(11, POLICY);
        assertEquals(400, data.cash());
        assertEquals(9_700, data.reserve());
        data.roll(11, POLICY);
        assertEquals(400, data.cash(), "a second roll for the same day changes nothing");
    }

    @Test void missedDaysCollapseIntoOneRoll() {
        TreasuryData data = fresh();
        assertTrue(data.withdraw(CASH, 1000, "TEST"));
        data.roll(50, POLICY);
        assertEquals(300, data.cash(), "40 days offline still issue only one refill");
    }

    @Test void anEarlierDayNeverRollsAgain() {
        TreasuryData data = fresh();
        assertTrue(data.withdraw(CASH, 900, "TEST"));
        data.roll(11, POLICY);
        assertEquals(400, data.cash());
        data.roll(10, POLICY);
        data.roll(11, POLICY);
        assertEquals(400, data.cash(), "a day seen before refills nothing");
        data.roll(12, POLICY);
        assertEquals(700, data.cash());
    }

    @Test void refillNeverExceedsReserve() {
        TreasuryData data = TreasuryData.create(new TreasuryData.Policy(0, 120, 1000, 2000, 300, 500));
        data.roll(1, POLICY);
        data.roll(2, new TreasuryData.Policy(0, 120, 1000, 2000, 300, 500));
        assertEquals(120, data.cash());
        assertEquals(0, data.reserve());
    }

    @Test void excessCashIsSweptBackToReserve() {
        TreasuryData data = fresh();
        data.deposit(CASH, 1500, "MARKET_BUY");
        data.roll(11, POLICY);
        assertEquals(2000, data.cash());
        assertEquals(10_500, data.reserve());
        assertEquals(12_500, data.cash() + data.reserve(), "sweeps conserve money");
    }

    @Test void withdrawRefusesShortfallAndReverseRestores() {
        TreasuryData data = fresh();
        assertFalse(data.withdraw(CASH, 1001, "TEST"));
        assertEquals(1000, data.cash());
        assertTrue(data.withdraw(CASH, 600, "TEST"));
        data.reverse(CASH, 600, "TEST");
        assertEquals(1000, data.cash());
        assertEquals(0, data.status(POLICY).getAsJsonObject("flows").getAsJsonObject("TEST").get("out").getAsLong());
    }

    @Test void npcBudgetIsCappedByBudgetAndReserve() {
        TreasuryData data = fresh();
        assertEquals(500, data.npcRemaining(POLICY));
        assertTrue(data.spendNpc(400, "NPC_DEMAND", POLICY));
        assertFalse(data.spendNpc(101, "NPC_DEMAND", POLICY));
        assertEquals(9_600, data.reserve());
        data.reverseNpc(400, "NPC_DEMAND");
        assertEquals(500, data.npcRemaining(POLICY));
        assertTrue(data.spendNpc(500, "NPC_DEMAND", POLICY));
        data.roll(11, POLICY);
        assertEquals(500, data.npcRemaining(POLICY), "budget resets with the economy day");

        TreasuryData poor = TreasuryData.create(new TreasuryData.Policy(0, 50, 0, 0, 0, 500));
        assertEquals(50, poor.npcRemaining(POLICY), "reserve bounds the budget");
    }

    @Test void injectNeverGoesNegative() {
        TreasuryData data = fresh();
        assertEquals(-1000, data.inject(CASH, -5000));
        assertEquals(0, data.cash());
        assertEquals(2500, data.inject(RESERVE, 2500));
        assertEquals(12_500, data.reserve());
        var flows = data.status(POLICY).getAsJsonObject("flows");
        assertEquals(1000, flows.getAsJsonObject("ADMIN_INJECT_CASH").get("out").getAsLong());
        assertEquals(2500, flows.getAsJsonObject("ADMIN_INJECT_RESERVE").get("in").getAsLong());
    }

    @Test void burnOnlyCounts() {
        TreasuryData data = fresh();
        data.burn(5, "WALLET_EXCHANGE_FEE");
        assertEquals(5, data.burned());
        assertEquals(1000, data.cash());
        assertEquals(10_000, data.reserve());
    }

    @Test void nbtRoundTrip() {
        TreasuryData data = fresh();
        data.withdraw(CASH, 250, "DEPOSIT_BIN_PAYOUT");
        data.spendNpc(70, "NPC_DEMAND", POLICY);
        data.burn(3, "FEE");
        TreasuryData loaded = TreasuryData.load(data.save(new CompoundTag()));
        assertEquals(data.status(POLICY).toString(), loaded.status(POLICY).toString());
        loaded.roll(10, POLICY);
        assertEquals(750, loaded.cash(), "reloading on the same day does not roll again");
    }
}
