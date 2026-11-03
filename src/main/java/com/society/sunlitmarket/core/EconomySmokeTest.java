package com.society.sunlitmarket.core;

import com.mojang.authlib.GameProfile;
import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.config.CoreConfig;
import com.society.sunlitmarket.counter.CounterBlockEntity;
import com.society.sunlitmarket.counter.CounterRegistry;
import com.society.sunlitmarket.counter.CustomerRequest;
import com.society.sunlitmarket.currency.Currency;
import com.society.sunlitmarket.deposit.DepositBinBlockEntity;
import com.society.sunlitmarket.deposit.DepositConfig;
import com.society.sunlitmarket.deposit.DepositRegistry;
import com.society.sunlitmarket.futures.FuturesData;
import com.society.sunlitmarket.futures.FuturesMath;
import com.society.sunlitmarket.futures.FuturesService;
import com.society.sunlitmarket.futures.FuturesSpecs;
import com.society.sunlitmarket.market.MarketData;
import com.society.sunlitmarket.market.MarketRegistry;
import com.society.sunlitmarket.market.MarketService;
import com.society.sunlitmarket.market.TradeFill;
import com.society.sunlitmarket.treasury.Treasury;
import com.society.sunlitmarket.treasury.TreasuryData;
import com.society.sunlitmarket.wallet.Wallet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Explicit isolated-server probe for the treasury, NPC demand, trade-built indices and futures.
 * Uses two fake players so futures have distinct owners; restores the treasury, market and
 * futures data, scripts, blocks, coin mapping and fake-player state afterwards. Never runs
 * automatically, and refuses worlds with real players, a bank or FTB Teams.
 */
public final class EconomySmokeTest {
    private static final UUID A_ID = UUID.fromString("5f0a6f3e-1c64-4f0e-9a41-6b1d3c1e0a01");
    private static final UUID B_ID = UUID.fromString("5f0a6f3e-1c64-4f0e-9a41-6b1d3c1e0a02");
    private static final String FACTION = "sunlitmarket:smoke";

    private EconomySmokeTest() {
    }

    /**
     * @param keep leave the settled index candles and futures history in place (scripts, treasury
     *             and players are still restored) so a restart can prove they persist
     */
    public static String run(MinecraftServer server, boolean keep) {
        if (!Boolean.getBoolean("sunlitmarket.audit.allowSmoke") || !server.getPlayerList().getPlayers().isEmpty()
                || ModList.get().isLoaded("numismatics") || ModList.get().isLoaded("ftbteams"))
            return "REFUSED: isolated -PauditSmoke server without players, bank or FTB Teams required";
        ServerLevel level = server.overworld();
        // Per-run ids: a "keep" run leaves its index history behind, which must not leak into the next run.
        String run = Long.toString(level.getGameTime());
        String SHOP = "__econ_smoke_" + run, INDEX = SHOP, VOID_INDEX = SHOP + "_void";
        FakePlayer a = FakePlayerFactory.get(level, new GameProfile(A_ID, "[EconSmokeA]"));
        FakePlayer b = FakePlayerFactory.get(level, new GameProfile(B_ID, "[EconSmokeB]"));
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos counterPos = new BlockPos(spawn.getX(), 250, spawn.getZ());
        BlockPos binPos = counterPos.east(2);
        BlockState oldCounter = level.getBlockState(counterPos), oldBin = level.getBlockState(binPos);
        if (!oldCounter.isAir() || !oldBin.isAir()) return "REFUSED: fixture positions above spawn must be air";

        var invA = Currency.snapshot(a.getInventory());
        var invB = Currency.snapshot(b.getInventory());
        CompoundTag dataA = a.getPersistentData().copy(), dataB = b.getPersistentData().copy();
        int emerald = Currency.coinValue("minecraft:emerald");
        long npcBudget = CoreConfig.NPC_DAILY_BUDGET.get();
        TreasuryData treasury = Treasury.data(server);
        MarketData market = MarketData.get(server);
        FuturesData futures = FuturesService.data(server);
        CompoundTag treasurySnapshot = treasury.save(new CompoundTag());
        CompoundTag marketSnapshot = market.save(new CompoundTag());
        CompoundTag futuresSnapshot = futures.save(new CompoundTag());
        List<String> passed = new ArrayList<>();
        Villager npc = null;
        try {
            if (!Treasury.enabled()) return "REFUSED: treasury disabled in config";
            Currency.setCoinValue("minecraft:emerald", 1);
            for (FakePlayer p : new FakePlayer[]{a, b}) {
                p.getInventory().clearContent();
                p.getPersistentData().remove("sunlitmarket.bankUncertain");
                Wallet.set(p, 50_000);
                p.setPos(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 2.5);
            }
            treasury.inject(TreasuryData.Account.CASH, 100_000 - treasury.cash());
            treasury.inject(TreasuryData.Account.RESERVE, 1_000_000 - treasury.reserve());

            // ---------------------------------------------------------------- shelves and C
            MarketRegistry.addScriptMerchant("{\"id\":\"" + SHOP + "\",\"name\":\"smoke\",\"faction\":\"" + FACTION + "\",\"tag\":\"sunlitmarket.smoke.none\","
                    + "\"offers\":[{\"id\":\"stone\",\"item\":\"minecraft:stone\",\"buy\":100,\"sell\":50,\"stock\":64},"
                    + "{\"id\":\"dirt\",\"item\":\"minecraft:dirt\",\"buy\":100,\"sell\":50,\"stock\":64}]}");
            market.defineIndex(INDEX, "smoke", List.of(new MarketData.Member(SHOP, "minecraft:stone")));
            market.defineIndex(VOID_INDEX, "smoke void", List.of(new MarketData.Member(SHOP, "minecraft:dirt")));

            long c0 = treasury.cash(), w0 = Wallet.balance(a);
            check(MarketService.handleAction(a, SHOP, 0, MarketService.ACTION_BUY, 2), "shelf buy accepted", passed);
            long paid = w0 - Wallet.balance(a);
            check(paid > 0 && treasury.cash() == c0 + paid && Currency.countItems(a, "minecraft:stone") == 2, "shelf buy pays into C (" + paid + ")", passed);
            check(market.value(INDEX) > 0 && market.candles(INDEX, MarketData.PERIODS[1], 10).size() == 1, "fill builds a candle", passed);
            check(MarketService.handleAction(b, SHOP, 1, MarketService.ACTION_BUY, 1) && market.value(VOID_INDEX) > 0, "second index traded", passed);

            b.getInventory().add(new ItemStack(Items.STONE, 1));
            long c1 = treasury.cash();
            int coins = Currency.coinBalance(b);
            check(MarketService.handleAction(b, SHOP, 0, MarketService.ACTION_SELL, 1), "shelf sell accepted", passed);
            int received = Currency.coinBalance(b) - coins;
            check(received > 0 && treasury.cash() == c1 - received && Currency.countItems(b, "minecraft:stone") == 0, "shelf sell paid out of C (" + received + ")", passed);

            treasury.inject(TreasuryData.Account.CASH, -treasury.cash());
            b.getInventory().add(new ItemStack(Items.STONE, 1));
            coins = Currency.coinBalance(b);
            check(!MarketService.handleAction(b, SHOP, 0, MarketService.ACTION_SELL, 1) && Currency.coinBalance(b) == coins
                    && Currency.countItems(b, "minecraft:stone") == 1 && treasury.cash() == 0, "empty C refuses shelf buyback", passed);
            treasury.inject(TreasuryData.Account.CASH, 100_000);

            // ---------------------------------------------------------------- acceptance bin
            level.setBlock(binPos, DepositRegistry.BLOCK.get().defaultBlockState(), 3);
            DepositBinBlockEntity bin = (DepositBinBlockEntity) level.getBlockEntity(binPos);
            if (bin == null) throw new IllegalStateException("no bin");
            bin.energy().receiveEnergy(DepositConfig.fePerSpur() * 10, false);
            long c2 = treasury.cash(), wa = Wallet.balance(a);
            String collected = bin.collect(a);
            check(Wallet.enabled() && Wallet.balance(a) == wa + 10 && treasury.cash() == c2 - 10 && bin.value() == 0, "bin payout drawn from C: " + collected, passed);
            treasury.inject(TreasuryData.Account.CASH, -treasury.cash());
            bin.energy().receiveEnergy(DepositConfig.fePerSpur() * 10, false);
            wa = Wallet.balance(a);
            String refused = bin.collect(a);
            check(refused.contains("当铺现金不足") && Wallet.balance(a) == wa && bin.value() == 10, "empty C refuses bin and keeps goods", passed);
            bin.energy().extractEnergy(bin.energy().getEnergyStored(), false);
            treasury.inject(TreasuryData.Account.CASH, 100_000);

            // ---------------------------------------------------------------- counter: NPC demand and R
            MarketRules.add("{\"type\":\"npc\",\"id\":\"__econ_npc\",\"tag\":\"sunlitmarket.smoke.customer\",\"faction\":\"" + FACTION + "\",\"rank\":\"CIVILIAN\"}");
            MarketRules.add("{\"type\":\"pool\",\"id\":\"__econ_buy\",\"faction\":\"" + FACTION + "\",\"item\":\"minecraft:apple\",\"count\":2,\"baseValue\":20,\"marketValue\":30,\"direction\":\"buy\",\"bidPrice\":30,\"repGain\":0}");
            MarketRules.add("{\"type\":\"pool\",\"id\":\"__econ_sell\",\"faction\":\"" + FACTION + "\",\"item\":\"minecraft:bread\",\"count\":1,\"baseValue\":10,\"marketValue\":10,\"askPrice\":10,\"repGain\":0}");
            level.setBlock(counterPos, CounterRegistry.BLOCK.get().defaultBlockState(), 3);
            CounterBlockEntity counter = (CounterBlockEntity) level.getBlockEntity(counterPos);
            if (counter == null) throw new IllegalStateException("no counter");
            counter.assign(a);
            npc = EntityType.VILLAGER.create(level);
            if (npc == null) throw new IllegalStateException("no villager");
            npc.setPos(counterPos.getX() + 1.5, counterPos.getY() + 1, counterPos.getZ() + 1.5);
            npc.addTag("sunlitmarket.smoke.customer");
            npc.setNoAi(true);
            npc.setNoGravity(true);
            level.addFreshEntity(npc);
            var buyPool = MarketRules.pools(FACTION).stream().filter(p -> p.id().equals("__econ_buy")).findFirst().orElseThrow();
            var sellPool = MarketRules.pools(FACTION).stream().filter(p -> p.id().equals("__econ_sell")).findFirst().orElseThrow();
            long now = EconomyClock.tick(server);

            CustomerRequest demand = CustomerRequest.create(npc.getUUID(), FACTION, buyPool, level.getGameTime() + 1000, true);
            counter.requests.add(demand);
            counter.setItem(0, new ItemStack(Items.APPLE, 2));
            long r0 = treasury.reserve(), spent0 = treasury.npcSpent();
            int coinsA = Currency.coinBalance(a);
            String sold = counter.act(a, demand.id, 0);
            check(sold.contains("出售完成") && counter.getItem(0).isEmpty() && treasury.reserve() == r0 - 30
                    && treasury.npcSpent() == spent0 + 30 && Currency.coinBalance(a) == coinsA + 30, "NPC demand consumes goods, pays from R budget", passed);

            CoreConfig.NPC_DAILY_BUDGET.set(treasury.npcSpent() + 10);
            CustomerRequest overBudget = CustomerRequest.create(npc.getUUID(), FACTION, buyPool, level.getGameTime() + 1000, true);
            counter.requests.add(overBudget);
            counter.setItem(0, new ItemStack(Items.APPLE, 2));
            r0 = treasury.reserve();
            String capped = counter.act(a, overBudget.id, 0);
            check(capped.contains("预算不足") && counter.getItem(0).getCount() == 2 && treasury.reserve() == r0, "NPC budget cap refuses", passed);
            CoreConfig.NPC_DAILY_BUDGET.set(npcBudget);
            counter.requests.clear();
            counter.clearContent();

            CustomerRequest purchase = CustomerRequest.create(npc.getUUID(), FACTION, sellPool, level.getGameTime() + 1000, false);
            counter.requests.add(purchase);
            r0 = treasury.reserve();
            wa = Wallet.balance(a);
            String bought = counter.act(a, purchase.id, 0);
            check(bought.contains("完成") && treasury.reserve() == r0 + 10 && Wallet.balance(a) == wa - 10, "counter purchase returns money to R", passed);
            counter.clearContent();

            // ---------------------------------------------------------------- futures
            FuturesSpecs.add("{\"index\":\"" + INDEX + "\",\"minTraders\":2,\"minBuckets\":1,\"maxLots\":5}");
            FuturesSpecs.add("{\"index\":\"" + VOID_INDEX + "\",\"minTraders\":2,\"minBuckets\":1,\"maxLots\":5}");
            int ref = market.recentClose(INDEX, now, FuturesMath.DAY);
            int refVoid = market.recentClose(VOID_INDEX, now, FuturesMath.DAY);
            check(ref > 0 && refVoid > 0, "reference prices from fills (" + ref + "/" + refVoid + ")", passed);
            long startA = Wallet.balance(a), startB = Wallet.balance(b), burned0 = futures.feesBurned();
            long[] bounds = FuturesMath.bounds(ref, 0.2);
            long fee = FuturesMath.feePerLot(ref, 10, 50);
            long longCost = 2 * (FuturesMath.collateralPerLot(true, ref, bounds[0], bounds[1], 10) + fee);
            String posted = FuturesService.offer(a, INDEX, true, 2, ref);
            check(posted.startsWith("挂单") && Wallet.balance(a) == startA - longCost, "offer escrows collateral and fee: " + posted, passed);
            int offerId = futures.offers().stream().filter(o -> o.terms.index().equals(INDEX)).findFirst().orElseThrow().id;
            check(FuturesService.take(a, offerId, 1).contains("不能与自己"), "self-trade refused", passed);
            check(FuturesService.take(b, offerId, 3).contains("手数"), "over-take refused", passed);
            String took = FuturesService.take(b, offerId, 1);
            check(took.startsWith("成交") && futures.consistent() && futures.contracts().size() == 1, "take forms contract: " + took, passed);
            check(FuturesService.offer(a, INDEX, true, 5, ref).contains("持仓上限"), "position limit", passed);
            check(FuturesService.offer(a, INDEX, true, 1, ref * 2).contains("超出参考价"), "circuit breaker band", passed);
            check(FuturesService.offer(b, VOID_INDEX, false, 1, refVoid).startsWith("挂单"), "void-series offer", passed);
            int voidId = futures.offers().stream().filter(o -> o.terms.index().equals(VOID_INDEX)).findFirst().orElseThrow().id;
            check(FuturesService.take(a, voidId, 1).startsWith("成交"), "void-series take", passed);

            FuturesData.Contract contract = futures.contracts().stream().filter(c -> c.terms.index().equals(INDEX)).findFirst().orElseThrow();
            long expiry = contract.terms.expiryTick();
            market.recordFill(new TradeFill(expiry - 20_000, SHOP, "minecraft:stone", "BUY", 100, 1, 1.1, "smoke:x"), 64);
            market.recordFill(new TradeFill(expiry - 5_000, SHOP, "minecraft:stone", "BUY", 100, 1, 1.1, "smoke:y"), 64);
            long escrowBefore = futures.escrow();
            var result = FuturesService.settle(server, expiry);
            long claims = futures.claim(A_ID) + futures.claim(B_ID);
            check(result.contracts() == 2 && futures.escrow() == 0 && futures.consistent() && claims == escrowBefore,
                    "settlement releases exactly the escrow (" + escrowBefore + ")", passed);
            long s = Math.max(contract.terms.low(), Math.min(contract.terms.high(), 1100));
            long expectedShort = (contract.terms.high() - s) * 10;
            var rows = futures.history();
            check(rows.stream().anyMatch(r -> r.get("reason").getAsString().equals("SETTLED") && r.get("price").getAsInt() == 1100)
                    && rows.stream().anyMatch(r -> r.get("reason").getAsString().equals("VOID_NO_TRADES")), "credible and void series recorded", passed);
            long claimB = futures.claim(B_ID);
            long voidShortBack = FuturesMath.collateralPerLot(false, refVoid, FuturesMath.bounds(refVoid, 0.2)[0], FuturesMath.bounds(refVoid, 0.2)[1], 10);
            check(claimB == expectedShort + voidShortBack, "short payout at S=1100 plus void refund (" + claimB + ")", passed);
            FuturesService.deliver(a);
            FuturesService.deliver(b);
            long burned = futures.feesBurned() - burned0;
            long net = (Wallet.balance(a) - startA) + (Wallet.balance(b) - startB);
            check(futures.claim(A_ID) == 0 && futures.claim(B_ID) == 0 && net == -burned, "players' net equals burned fees (" + burned + ")", passed);

            // ---------------------------------------------------------------- command permissions
            var dispatcher = server.getCommands().getDispatcher();
            var player0 = server.createCommandSourceStack().withPermission(0);
            var op = server.createCommandSourceStack().withPermission(2);
            check(parses(dispatcher, player0, "sunlitmarket consign browse") && parses(dispatcher, player0, "sunlitmarket chart"),
                    "players reach consign and chart", passed);
            check(!parses(dispatcher, player0, "sunlitmarket treasury inject cash 1") && !parses(dispatcher, player0, "sunlitmarket bankunlock @s")
                    && !parses(dispatcher, player0, "sunlitmarket open x") && parses(dispatcher, op, "sunlitmarket treasury inject cash 1"),
                    "admin nodes refuse permission 0", passed);

            // ---------------------------------------------------------------- consignment escrow
            treasury.inject(TreasuryData.Account.CASH, -treasury.cash());
            var consign = com.society.sunlitmarket.consignment.ConsignmentService.data(server);
            long claimsBefore = consign.claimsTotal(), r3 = treasury.reserve();
            b.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 4));
            String listed = com.society.sunlitmarket.consignment.ConsignmentService.list(b, 25, 4);
            int listingId = consign.listingsOf(B_ID).stream().mapToInt(l -> l.id).max().orElse(-1);
            check(listed.startsWith("已挂单") && listingId > 0, "consignment listed: " + listed, passed);
            long aBefore = Wallet.balance(a);
            String consignBought = com.society.sunlitmarket.consignment.ConsignmentService.buy(a, listingId, 4);
            long consignFee = com.society.sunlitmarket.consignment.ConsignmentService.feeFor(100);
            check(Wallet.balance(a) == aBefore - 100 && treasury.cash() == 0 && treasury.reserve() == r3 + consignFee
                    && consign.claim(B_ID) == 100 - consignFee && consign.claimsTotal() == claimsBefore + 100 - consignFee,
                    "sale escrowed outside C: " + consignBought, passed);
            int coinsB = Currency.coinBalance(b);
            String delivered = com.society.sunlitmarket.consignment.ConsignmentService.deliver(b);
            check(Currency.coinBalance(b) == coinsB + 100 - consignFee && consign.claim(B_ID) == 0 && treasury.cash() == 0,
                    "seller paid with C empty: " + delivered, passed);
            treasury.inject(TreasuryData.Account.CASH, 100_000);

            // ---------------------------------------------------------------- acceptance bin keeps what it does not pay for
            check(!bin.items().isItemValid(0, new ItemStack(Items.BEDROCK)), "unpriced item refused at the door", passed);
            DepositConfig.setItemPrice("minecraft:diamond", 5);
            bin.energy().extractEnergy(bin.energy().getEnergyStored(), false);
            check(bin.items().insertItem(0, new ItemStack(Items.DIAMOND, 1), false).isEmpty(), "priced item accepted", passed);
            DepositConfig.refuse("minecraft:diamond");
            bin.energy().receiveEnergy(DepositConfig.fePerSpur() * 3, false);
            wa = Wallet.balance(a);
            String partial = bin.collect(a);
            check(Wallet.balance(a) == wa + 3 && bin.items().getStackInSlot(0).is(Items.DIAMOND),
                    "payout drains only paid ports: " + partial, passed);
            check(bin.takeItem().is(Items.DIAMOND) && bin.items().getStackInSlot(0).isEmpty(), "held item handed back", passed);

            // ---------------------------------------------------------------- forward-only economy clock
            long dayTime = level.getDayTime();
            long eco0 = EconomyClock.tick(server);
            level.setDayTime(dayTime + EconomyClock.DAY);
            long eco1 = EconomyClock.tick(server);
            check(eco1 == eco0 + EconomyClock.DAY, "sleep-like jump advances the economy day", passed);
            level.setDayTime(1000);
            long eco2 = EconomyClock.tick(server);
            check(eco2 == eco1, "time set back does not rewind (" + eco1 + " -> " + eco2 + ")", passed);
            long rolledDay = eco2 / EconomyClock.DAY;
            check(Treasury.data(server).status(Treasury.policy()).get("economyDay").getAsLong() == rolledDay,
                    "treasury day follows the clock without going back", passed);
            level.setDayTime(dayTime);

            return "PASS " + passed.size() + " economy assertions: " + String.join(", ", passed);
        } catch (RuntimeException ex) {
            SunlitMarketMod.LOG.error("[SM-ECON-TEST] FAIL after {}", passed, ex);
            return "FAIL after " + passed.size() + " (" + String.join(", ", passed) + "): " + ex;
        } finally {
            if (npc != null) npc.discard();
            if (level.getBlockEntity(counterPos) instanceof CounterBlockEntity be) {
                be.clearContent();
                be.requests.clear();
            }
            if (level.getBlockEntity(binPos) instanceof DepositBinBlockEntity be) be.energy().extractEnergy(be.energy().getEnergyStored(), false);
            level.setBlock(counterPos, oldCounter, 3);
            level.setBlock(binPos, oldBin, 3);
            CoreConfig.NPC_DAILY_BUDGET.set(npcBudget);
            FuturesSpecs.removeScript(INDEX);
            FuturesSpecs.removeScript(VOID_INDEX);
            MarketRegistry.removeScript(SHOP);
            MarketRules.removeScript("__econ_npc");
            MarketRules.removeScript("__econ_buy");
            MarketRules.removeScript("__econ_sell");
            if (!keep) {
                futures.restore(futuresSnapshot);
                market.restore(marketSnapshot);
            }
            treasury.restore(treasurySnapshot);
            restore(a, invA, dataA);
            restore(b, invB, dataB);
            Currency.setCoinValue("minecraft:emerald", emerald);
            // Drop the in-memory price/refusal edits made above; the file on disk is untouched.
            DepositConfig.beginScripts();
            DepositConfig.endScripts(true);
            DepositConfig.load();
            SunlitMarketMod.LOG.info("[SM-ECON-TEST] completed assertions {}", passed);
        }
    }

    /** True when the whole command parses for this source, i.e. every node on the path is usable. */
    private static boolean parses(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
                                  net.minecraft.commands.CommandSourceStack source, String command) {
        var result = dispatcher.parse(command, source);
        return !result.getReader().canRead() && result.getExceptions().isEmpty() && result.getContext().getCommand() != null;
    }

    private static void restore(FakePlayer p, List<ItemStack> inventory, CompoundTag data) {
        for (int i = 0; i < inventory.size(); i++) p.getInventory().setItem(i, inventory.get(i));
        for (String k : new java.util.HashSet<>(p.getPersistentData().getAllKeys())) p.getPersistentData().remove(k);
        p.getPersistentData().merge(data);
    }

    private static void check(boolean condition, String name, List<String> passed) {
        if (!condition) throw new IllegalStateException(name);
        passed.add(name);
    }
}
