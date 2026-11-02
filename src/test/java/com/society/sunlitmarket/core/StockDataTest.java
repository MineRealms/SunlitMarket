package com.society.sunlitmarket.core;

import com.society.sunlitmarket.market.StockData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StockDataTest {
    @Test void explicitLegacyIndexMigratesWithoutRestocking() {
        StockData data = new StockData();
        assertTrue(data.take("shop#0", 10, 4));
        assertEquals(6, data.get("shop#id:stone", "shop#0", 10));
        assertEquals(6, data.get("shop#id:stone", "shop#0", 10));
        StockData restored = StockData.load(data.save(new CompoundTag()));
        assertEquals(6, restored.get("shop#id:stone", "shop#0", 10));
    }

    @Test void existingIdValueIsNotOverwrittenByLegacyValue() {
        StockData data = new StockData();
        assertTrue(data.take("shop#0", 10, 3));
        assertTrue(data.take("shop#id:stone", 10, 2));
        assertEquals(8, data.get("shop#id:stone", "shop#0", 10));
        assertEquals(7, data.get("shop#0", 10));
    }

    @Test void saleRestocksShelfUpToCapacity() {
        StockData data = new StockData();
        assertTrue(data.take("shop#0", 10, 6));
        data.add("shop#0", 10, 30, 3.0D);
        assertEquals(30, data.get("shop#0", 10));
        data.add("shop#0", 10, 5, 3.0D);
        assertEquals(30, data.get("shop#0", 10));
        data.add("shop#unlimited", -1, 5, 3.0D);
        assertEquals(-1, data.get("shop#unlimited", -1));
    }
}
