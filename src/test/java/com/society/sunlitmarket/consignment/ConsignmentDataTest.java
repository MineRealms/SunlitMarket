package com.society.sunlitmarket.consignment;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConsignmentDataTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test void postsAndPartiallyFills() {
        ConsignmentData data = new ConsignmentData();
        ConsignmentData.Listing l = data.post(A, "alice", "minecraft:iron_ingot", 64, 3, 100L);
        assertEquals(1, l.id);
        assertEquals(64, l.remaining);
        data.take(l, 10);
        assertEquals(54, data.listing(l.id).remaining);
        assertEquals(10, data.itemsSold());
        assertEquals(1, data.listings().size());
    }

    @Test void soldOutListingDisappears() {
        ConsignmentData data = new ConsignmentData();
        ConsignmentData.Listing l = data.post(A, "alice", "minecraft:iron_ingot", 5, 3, 0L);
        data.take(l, 5);
        assertNull(data.listing(l.id));
        assertEquals(0, data.listings().size());
        assertTrue(data.consistent());
    }

    @Test void refusesBadTerms() {
        ConsignmentData data = new ConsignmentData();
        assertThrows(IllegalArgumentException.class, () -> data.post(A, "a", "minecraft:dirt", 0, 1, 0L));
        assertThrows(IllegalArgumentException.class, () -> data.post(A, "a", "minecraft:dirt", 1, 0, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> data.post(A, "a", "minecraft:dirt", 1, ConsignmentData.MAX_UNIT_PRICE + 1, 0L));
    }

    @Test void capsListingsPerSeller() {
        ConsignmentData data = new ConsignmentData();
        for (int i = 0; i < ConsignmentData.PER_SELLER; i++)
            data.post(A, "a", "minecraft:dirt", 1, 1, 0L);
        assertThrows(IllegalArgumentException.class, () -> data.post(A, "a", "minecraft:dirt", 1, 1, 0L));
        data.post(B, "b", "minecraft:dirt", 1, 1, 0L);
    }

    @Test void creditAndDeliverTrackTheClaim() {
        ConsignmentData data = new ConsignmentData();
        data.credit(A, "alice", 980, 20);
        assertEquals(980, data.claim(A));
        assertEquals(1000, data.grossSpur());
        assertEquals(20, data.feesSpur());
        data.delivered(A, 500);
        assertEquals(480, data.claim(A));
        data.delivered(A, 480);
        assertEquals(0, data.claim(A));
        assertEquals("", data.claimName(A));
    }

    @Test void restorePutsUnitsBackAndRevivesASoldOutListing() {
        ConsignmentData data = new ConsignmentData();
        ConsignmentData.Listing l = data.post(A, "alice", "minecraft:iron_ingot", 5, 3, 0L);
        data.take(l, 5);
        assertNull(data.listing(l.id));
        data.restore(l, 5);
        assertNotNull(data.listing(l.id));
        assertEquals(5, data.listing(l.id).remaining);
        assertEquals(0, data.itemsSold());
        assertTrue(data.consistent());
    }

    @Test void cancelReturnsTheRemainder() {
        ConsignmentData data = new ConsignmentData();
        ConsignmentData.Listing l = data.post(A, "alice", "minecraft:iron_ingot", 20, 5, 0L);
        data.take(l, 7);
        assertEquals(13, data.cancel(l));
        assertNull(data.listing(l.id));
    }

    @Test void survivesASaveLoadRoundTrip() {
        ConsignmentData data = new ConsignmentData();
        data.post(A, "alice", "minecraft:iron_ingot", 64, 3, 1234L);
        data.post(B, "bob", "minecraft:coal", 8, 2, 1235L);
        data.credit(B, "bob", 100, 5);
        ConsignmentData copy = ConsignmentData.load(data.save(new CompoundTag()));
        assertEquals(2, copy.listings().size());
        assertEquals(3, copy.nextId());
        assertEquals(100, copy.claim(B));
        assertEquals("bob", copy.claimName(B));
        assertEquals(64, copy.listingsOf(A).get(0).remaining);
        assertTrue(copy.consistent());
    }

    @Test void feeMathIsBounded() {
        ConsignmentService.feeBps(200);
        assertEquals(20, ConsignmentService.feeFor(1000));
        assertEquals(0, ConsignmentService.feeFor(49));
        assertEquals(1, ConsignmentService.feeFor(50));
        ConsignmentService.feeBps(0);
        assertEquals(0, ConsignmentService.feeFor(1000));
        ConsignmentService.feeBps(5000);
        assertEquals(2000, ConsignmentService.feeBps());
        assertEquals(200, ConsignmentService.feeFor(1000));
        ConsignmentService.feeBps(200);
    }
}
