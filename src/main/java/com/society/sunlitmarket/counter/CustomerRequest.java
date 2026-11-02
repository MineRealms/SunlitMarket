package com.society.sunlitmarket.counter;

import com.society.sunlitmarket.core.MarketRules;
import com.society.sunlitmarket.currency.Currency;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

public final class CustomerRequest {
    public UUID id, npc;
    public String pool, faction;
    public ItemStack goods;
    public int base, market, ask, heat, wanted, reward;
    public long expires;
    public boolean negotiated;
    public static CustomerRequest create(UUID npc, String faction, MarketRules.Pool pool, long expires) {
        CustomerRequest r=new CustomerRequest(); r.id=UUID.randomUUID(); r.npc=npc; r.faction=faction; r.pool=pool.id();
        r.goods=Currency.item(pool.item()); r.goods.setCount(pool.count());
        if(!pool.nbt().isBlank()) try { r.goods.setTag(TagParser.parseTag(pool.nbt())); } catch(Exception ex) { throw new IllegalArgumentException("Invalid request NBT",ex); }
        r.base=pool.baseValue(); r.market=pool.marketValue(); r.ask=pool.askPrice(); r.heat=pool.heat(); r.wanted=pool.wanted(); r.reward=pool.reward(); r.expires=expires;
        return r;
    }
    public CompoundTag save() {
        CompoundTag t=new CompoundTag(); t.putUUID("id",id); t.putUUID("npc",npc); t.putString("pool",pool); t.putString("faction",faction);
        t.put("goods",goods.save(new CompoundTag())); t.putInt("base",base); t.putInt("market",market); t.putInt("ask",ask);
        t.putInt("heat",heat); t.putInt("wanted",wanted); t.putInt("reward",reward); t.putLong("expires",expires); t.putBoolean("negotiated",negotiated); return t;
    }
    public static CustomerRequest load(CompoundTag t) {
        CustomerRequest r=new CustomerRequest(); r.id=t.getUUID("id"); r.npc=t.getUUID("npc"); r.pool=t.getString("pool"); r.faction=t.getString("faction");
        r.goods=ItemStack.of(t.getCompound("goods")); r.base=t.getInt("base");r.market=t.getInt("market");r.ask=t.getInt("ask");
        r.heat=t.getInt("heat");r.wanted=t.getInt("wanted");r.reward=t.getInt("reward");r.expires=t.getLong("expires");r.negotiated=t.getBoolean("negotiated");
        if(r.goods.isEmpty()||r.goods.getCount()>64||r.base<1||r.market<1||r.ask<1) throw new IllegalArgumentException("Invalid request");
        return r;
    }
}
