package com.society.sunlitmarket.futures;

import com.google.gson.JsonObject;
import com.society.sunlitmarket.market.FillMath;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FuturesDataTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final long DAY = FuturesMath.DAY;

    private static FuturesData.Terms terms(int price) {
        long[] b = FuturesMath.bounds(price, 0.2);
        return new FuturesData.Terms("sv.ore", 7, price, b[0], b[1], 10, 0.2, 2, 2, DAY);
    }

    @Test void boundsAndCollateralAddUpToTheBand() {
        long[] b = FuturesMath.bounds(1000, 0.2);
        assertArrayEquals(new long[]{800, 1200}, b);
        long longSide = FuturesMath.collateralPerLot(true, 1000, b[0], b[1], 10);
        long shortSide = FuturesMath.collateralPerLot(false, 1000, b[0], b[1], 10);
        assertEquals(2000, longSide);
        assertEquals(2000, shortSide);
        for (int s : new int[]{0, 700, 800, 950, 1000, 1130, 1200, 5000}) {
            long sum = FuturesMath.payout(true, s, b[0], b[1], 10, 3) + FuturesMath.payout(false, s, b[0], b[1], 10, 3);
            assertEquals((longSide + shortSide) * 3, sum, "zero-sum at S=" + s);
        }
        assertEquals(0, FuturesMath.payout(true, 500, b[0], b[1], 10, 1), "settlement clamps to the floor");
        assertEquals(4000, FuturesMath.payout(true, 9999, b[0], b[1], 10, 1), "and to the cap");
    }

    @Test void feeIsBasisPointsOfNotional() {
        assertEquals(50, FuturesMath.feePerLot(1000, 10, 50));
        assertEquals(0, FuturesMath.feePerLot(1000, 10, 0));
    }

    @Test void expiryIsAFixedTenorMultipleOutsideTheWindow() {
        assertEquals(7, FuturesMath.expiryDay(0, 7, DAY));
        assertEquals(7, FuturesMath.expiryDay(5 * DAY, 7, DAY));
        assertEquals(14, FuturesMath.expiryDay(6 * DAY + 1, 7, DAY), "inside day 7's settlement window: next series");
        assertEquals(14, FuturesMath.expiryDay(7 * DAY, 7, DAY));
    }

    @Test void escrowInvariantThroughPostTakeCancelAndSettle() {
        FuturesData data = new FuturesData();
        long fee = FuturesMath.feePerLot(1000, 10, 50);
        FuturesData.Offer offer = data.post(A, "player:a", "A", true, 5, terms(1000), fee, 0);
        assertEquals(5 * (2000 + 50), data.escrow());
        assertTrue(data.consistent());

        assertEquals(3 * (2000 + 50), FuturesData.takeCost(offer, 3));
        long burned = data.take(offer, 3, B, "player:b", 10);
        assertEquals(300, burned, "both sides' fees for three lots");
        assertTrue(data.consistent());
        assertEquals(2, data.offer(offer.id).lots);
        assertEquals(1, data.contracts().size());

        assertEquals(6, data.position("player:a", "sv.ore", 7) + data.position("player:b", "sv.ore", 7) - 2,
                "A holds 2 resting + 3 long, B holds 3 short");

        long refund = data.cancel(offer);
        assertEquals(2 * 2050, refund);
        assertEquals(refund, data.claim(A));
        assertTrue(data.consistent());

        long before = data.escrow();
        var result = data.settleExpired(7 * DAY, t -> new FillMath.Settlement(true, 1100, "OK", 3, 2));
        assertEquals(1, result.contracts());
        assertEquals(before, result.paid(), "everything escrowed is paid out");
        assertEquals(0, data.escrow());
        assertTrue(data.consistent());
        assertEquals(refund + 3 * 3000, data.claim(A), "long: (1100-800)*10*3");
        assertEquals(3 * 1000, data.claim(B), "short: (1200-1100)*10*3");
        JsonObject row = data.history().get(0);
        assertEquals("SETTLED", row.get("reason").getAsString());
        assertEquals(1100, row.get("price").getAsInt());
    }

    @Test void voidSettlementRefundsCollateralButNotFees() {
        FuturesData data = new FuturesData();
        FuturesData.Offer offer = data.post(A, "player:a", "A", false, 2, terms(1000), 50, 0);
        data.take(offer, 2, B, "player:b", 1);
        var result = data.settleExpired(7 * DAY, t -> new FillMath.Settlement(false, -1, "TOO_FEW_TRADERS", 1, 1));
        assertEquals(8000, result.refunded());
        assertEquals(4000, data.claim(A));
        assertEquals(4000, data.claim(B));
        assertEquals(200, data.feesBurned());
        assertEquals(0, data.escrow());
        assertEquals("VOID_TOO_FEW_TRADERS", data.history().get(0).get("reason").getAsString());
    }

    @Test void unexpiredSeriesAreUntouchedAndExpiredOffersRefund() {
        FuturesData data = new FuturesData();
        data.post(A, "player:a", "A", true, 1, terms(1000), 50, 0);
        assertEquals(0, data.settleExpired(7 * DAY - 1, t -> { throw new AssertionError("not yet"); }).series());
        var result = data.settleExpired(7 * DAY, t -> { throw new AssertionError("no contracts to price"); });
        assertEquals(2050, result.refunded());
        assertEquals(2050, data.claim(A));
        assertTrue(data.offers().isEmpty());
    }

    @Test void deliveredReducesClaims() {
        FuturesData data = new FuturesData();
        data.credit(A, 500);
        data.delivered(A, 200);
        assertEquals(300, data.claim(A));
        data.delivered(A, 300);
        assertEquals(0, data.claim(A));
        assertEquals(0, data.claimsTotal());
    }

    @Test void nbtRoundTripKeepsTheBookConsistent() {
        FuturesData data = new FuturesData();
        FuturesData.Offer offer = data.post(A, "player:a", "A", true, 4, terms(1000), 50, 0);
        data.take(offer, 1, B, "player:b", 5);
        data.credit(B, 77);
        data.settleExpired(0, t -> null);
        FuturesData loaded = FuturesData.load(data.save(new CompoundTag()));
        assertEquals(data.status().toString(), loaded.status().toString());
        assertTrue(loaded.consistent());
        assertEquals(3, loaded.offer(offer.id).lots);
        assertEquals(77, loaded.claim(B));
        FuturesData.Offer next = loaded.post(B, "player:b", "B", false, 1, terms(1000), 50, 6);
        assertTrue(next.id > offer.id, "ids keep increasing after a reload");
    }

    @Test void specValidation() {
        JsonObject o = new JsonObject();
        o.addProperty("index", "sv.ore");
        FuturesSpec spec = FuturesSpec.parse(o);
        assertEquals(10, spec.multiplier());
        assertEquals(7, spec.tenorDays());
        o.addProperty("band", 0.0);
        assertThrows(IllegalArgumentException.class, () -> FuturesSpec.parse(o));
        JsonObject window = new JsonObject();
        window.addProperty("index", "x");
        window.addProperty("tenorDays", 1);
        window.addProperty("windowTicks", DAY);
        assertThrows(IllegalArgumentException.class, () -> FuturesSpec.parse(window), "window must be shorter than the tenor");
    }
}
