package com.society.sunlitmarket.audit;

import com.mojang.authlib.GameProfile;
import com.society.sunlitmarket.currency.Currency;
import com.society.sunlitmarket.wallet.Wallet;
import com.society.sunlitmarket.wallet.WalletExchange;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import java.util.UUID;

/** Explicit isolated-server fixture, never run automatically or on connected players. */
public final class AuditSmokeTest {
    private AuditSmokeTest() {}
    public static String run(MinecraftServer server) {
        if (!Boolean.getBoolean("sunlitmarket.audit.allowSmoke") || !server.getPlayerList().getPlayers().isEmpty()
                || ModList.get().isLoaded("numismatics")) return "REFUSED: isolated -PauditSmoke server without players/bank required";
        var p = FakePlayerFactory.get(server.overworld(), new GameProfile(UUID.fromString("758c2fbe-5c52-4705-87c4-ac46a5e17919"), "[AuditSmoke]"));
        var inventory = Currency.snapshot(p.getInventory());
        CompoundTag original = p.getPersistentData().copy();
        String disabled = System.getProperty("sunlitmarket.audit.disabled");
        int emerald = Currency.coinValue("minecraft:emerald");
        var treasury = com.society.sunlitmarket.treasury.Treasury.data(server);
        CompoundTag treasurySnapshot = treasury.save(new CompoundTag());
        int checks = 0;
        try {
            // Emeralds are not money by default; this fixture opts them in for the run only.
            Currency.setCoinValue("minecraft:emerald", 1);
            for (boolean off : new boolean[]{false, true}) {
                System.setProperty("sunlitmarket.audit.disabled", Boolean.toString(off));
                p.getInventory().clearContent(); p.getPersistentData().remove("sunlitmarket.bankUncertain");
                p.getInventory().setItem(0, new ItemStack(Items.EMERALD, 64)); Wallet.set(p, 1000);
                check(Currency.pay(p, 500) && Wallet.balance(p)==500 && Currency.coinBalance(p)==64, "wallet priority"); checks++;
                check(Currency.payCash(p, 10) && Wallet.balance(p)==500 && Currency.coinBalance(p)==54, "cash only"); checks++;
                String reject=Currency.settle(p,null,p.getInventory(),new ItemStack(Items.DIRT),1,-900);
                check(!reject.isEmpty()&&Wallet.balance(p)==500&&Currency.coinBalance(p)==54, "failure unchanged"); checks++;
                check(Currency.settle(p,null,p.getInventory(),new ItemStack(Items.DIRT),1,-20).isEmpty()
                        &&Wallet.balance(p)==480&&Currency.countItems(p,"minecraft:dirt")==1,"goods settled once"); checks++;
                Wallet.set(p,0);p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(Items.EMERALD,100));
                long credit=WalletExchange.afterFee(100);WalletExchange.deposit(p);
                check(Wallet.balance(p)==credit&&Currency.coinBalance(p)==0,"exchange in");checks++;
                long payout=WalletExchange.afterFee(50);WalletExchange.withdraw(p,50);
                check(Wallet.balance(p)==credit-50&&Currency.coinBalance(p)==payout,"exchange out");checks++;
                Wallet.set(p,Long.MAX_VALUE-2);Long before=EconomyAudit.walletBefore(p);Wallet.add(p,10);
                EconomyAudit.walletAfter(p,before,10,true,"SMOKE_SATURATION");
                check(Wallet.balance(p)==Long.MAX_VALUE,"saturation");checks++;
            }
            return "PASS "+checks+" real Currency assertions (audit on/off); fake-player fixture restored";
        } catch (RuntimeException ex) {
            return "FAIL after "+checks+": "+ex.getMessage();
        } finally {
            for(int i=0;i<inventory.size();i++)p.getInventory().setItem(i,inventory.get(i));
            for(String k:new java.util.HashSet<>(p.getPersistentData().getAllKeys()))p.getPersistentData().remove(k);
            p.getPersistentData().merge(original);
            Currency.setCoinValue("minecraft:emerald",emerald);
            treasury.restore(treasurySnapshot);
            if(disabled==null)System.clearProperty("sunlitmarket.audit.disabled");else System.setProperty("sunlitmarket.audit.disabled",disabled);
        }
    }
    private static void check(boolean value,String label){if(!value)throw new IllegalStateException(label);}
}
