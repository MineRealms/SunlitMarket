# SunlitMarket core

## Scope

This project owns the trading engine and optional ME/FTB adapters. It does not install Sunlit Valley recipes, production prices, quest gates or server releases. Player-to-player shop customers are deferred; current requests are NPCs selling goods to a player-operated counter. The pack-side economy/price analysis lives in `SUNLIT_VALLEY_INTEGRATION.md`.

## Configuration

- `config/sunlitmarket-common.toml`: existing alignment ranges, diplomatic backlash, decay and price ladder.
- `config/sunlitmarket-core.toml`: team sharing, refusal threshold, same-quote price protection, counter limits and crime policy.
- `trade.dynamicPricing` (default on) makes stocked shelves price by inventory: one scarcity factor `clamp((initial/max(stock,1))^elasticity, min, max)` scales BOTH directions of the same offer. Buying drains the shelf and raises prices; selling refills it (capped at `sellOverstockCapacity` x restock level) and lowers them. Unlimited offers (`stock: -1`) and fixed/coin offers keep fixed prices. Shelf stock still resets at the daily roll, which acts as the mean-reversion step.
- `config/sunlitmarket/merchants.json`: legacy merchant array or `{ "merchants": [...] }`.
- `config/sunlitmarket/rules.json`: NPC identities, explicit request pools and contraband laws. See `examples/core-rules.json`.
- `config/sunlitmarket/deposit_values.json`: the acceptance bin's price table plus the `wallet` block. Written with defaults on first start; see §Acceptance bin and §Virtual wallet.
- NPC identity rules accept `tag`, `npcType`, `entity` (registry id) and `profession` (villager profession). `entity` + `profession` is intended for pack NPCs such as vanilla villagers; `profession` alone matches any villager with that profession.
- Data packs: `data/<namespace>/sunlitmarket/factions/*.json`; the optional ME adapter reads `data/me/me/faction/*.json` without importing ME classes.

Use `/reload` for data packs and server scripts. `/sunlitmarket reload` reloads local JSON and faction/config snapshots; it does not re-evaluate KubeJS scripts. `SunlitMarket.reload()` retains its legacy merchant-JSON meaning. Reload invalidates network sessions: close and reopen the UI. Existing request prices are snapshots; requests are rechecked against current identity, pool existence, expiry and distance before settlement.

Invalid merchant/rule/faction JSON keeps that registry's previous valid snapshot. Script merchants, rules, price multipliers and coin values are staged together: a failed script evaluation keeps the whole previous script snapshot, while a successful reload drops definitions from deleted scripts. Failure detection must inspect `ScriptFileInfo.lines`; KubeJS does not reliably place syntax/evaluation failures in `console.errors`. Staged prices and coin values are invisible to game reads until commit. This does not roll back arbitrary external effects caused by a script before it fails.

## Existing shops

Old `buy` / `sell` remain prices paid/received by the player. Zero disables that direction. Omitted `reputation` means no item-specific alignment gate; explicitly specifying zero requires nonnegative alignment. Signed gates are supported. `trade.refuseBelow` is a separate faction-wide threshold.

An explicit offer `id` gives it a stable stock/limit key. Legacy entries retain the old index key for saved-data compatibility; do not reorder legacy offers during a stock cycle. When adding an ID to a previously deployed offer, set `legacyIndex` to its former zero-based index. On first access, the saved stock and purchase count move to the ID key instead of being replenished. Omitting `legacyIndex` intentionally starts a separate entry.

Ordinary two-way quotes clamp the resale value to the current purchase price by default. This is same-item/same-quote protection, not a guarantee against all cross-shop or time-based arbitrage. `allowArbitrage` is an explicit override. Coin items and `fixedPrice` offers bypass alignment/seasonal multipliers.

Network trades require a server-created session and matching quote/offer identity. Script `buy`/`sell` remain trusted server-side calls. Item matching is exact ItemStack data: ordinary stock sales do not silently consume enchanted, renamed or provenance-marked variants.

Payments are prepared against inventory copies before committing. Installed-bank errors do not silently switch to physical coins. An uncertain bank rollback sets a transaction lock for administrator reconciliation; `/sunlitmarket bankunlock <player>` clears it only after the balance is checked. This is not a cross-mod crash-atomic database transaction.

## Teams

FTB Teams is optional. Shared party teams use a typed `team:<uuid>` owner even with one remaining member; personal teams use `player:<uuid>`. Joining/leaving never copies alignment or assets. A counter keeps its original owner; current membership is checked for every interaction. Personal currency is never pooled automatically.

`/sunlitmarket team on|off` changes the runtime alignment-sharing policy; edit TOML for a persistent setting. `/sunlitmarket owner` shows the current identity. `/sunlitmarket recovercounter` transfers a targeted counter to the administrator's current owner and cancels requests; it is an explicit recovery action, not an automatic dissolution transfer.

## Price indices

`MarketData` (saved data `sunlitmarket_market`) keeps index membership and OHLC candle rings (1-day and 4-hour, 240 candles each). KubeJS registers members bound to merchant shelves: `SunlitMarket.index(id, name, [[merchantId, item], ...])`. The index is the geometric mean of member ratios (current mid / static script base), where the mid is the offer buy price times price modifiers times the E1 shelf-scarcity factor - so trading with the shelves is what moves the index. `/sunlitmarket index` prints live values. Sampling runs every 100 server ticks; missed buckets after downtime are backfilled flat at the last close. Candle rings persist in the same saved data; membership is replaced on every script reload.

## Chart panel

The 行情 button in the shop window sends `ChartRequestMessage` (C2S, read-only, no trade session needed); the server answers with a serialised `ChartView` through `ChartDataMessage`. Index and period tabs re-request rather than filter locally, so the client never holds a second copy of the pricing rules. `/sunlitmarket chart [index] [period]` opens the same panel from the console. Closing the chart returns to the shop instead of the world.

The panel shares `client/Ui` with the shop window, so both windows have the same frame, header stripe, chip tabs and footer. Candles are drawn with `GuiGraphics.fill` (no chart library); the y axis auto-scales to the visible high/low with 5% padding, and hovering shows that bucket's open/high/low/close. Colour convention is 红涨绿跌 (red up, green down). A period is 24000 ticks (one in-game day) or 4000 ticks (four in-game hours), matching `MarketData.PERIODS`.

## Shop window

`MarketScreen` is a fixed-size framed window: search box (filters by item id and localised name, `Ctrl+F` focuses it), scrollbar (wheel, track click, thumb drag) and item tooltips. The status line under each item shows stock, daily limit, reputation requirement, unlock state and the shelf premium - the difference between the current quote and the same quote without the E1 scarcity factor, which is what `baseBuy`/`baseSell` in `MarketView` carry.

Every pixel is drawn by `client/Ui` from `GuiGraphics.fill` primitives - no textures, no vanilla `Button`. The layout is a fixed rhythm (20px header, 18px toolbar, N rows of 22px, 18px footer, 6px gutters) inside a 360px window, so nothing reflows when the list is short and no asset can go missing at runtime. The one vanilla widget left is the borderless `EditBox` behind the search frame, because text input, selection and the caret are not worth reimplementing.

Client/server split: every class under `client/` may touch `net.minecraft.client.*`; nothing outside it may. S2C handlers reach the GUI only through `client/ClientPacketHandlers`, and the built jar is scanned to keep it that way (a dedicated server previously died with `NoClassDefFoundError: net/minecraft/client/gui/screens/Screen`).

## Acceptance bin

`sunlitmarket:deposit_bin` drains five different industrial outputs into one payout, so a late-game player has somewhere to dump surplus without a per-mod hopper chain.

| Intake | Capability | Capacity | Rate source |
|---|---|---:|---|
| Item | Forge `IItemHandler` | 1 stack | `items` entry in `deposit_values.json`; else the pawnshop's own sell table (`Offer.sell / count`); else 0 |
| Fluid | Forge `IFluidHandler` | 8 buckets | `fluids` entry per fluid id, `fluids.default` otherwise |
| FE | Forge `IEnergyStorage` | 2M | `energy.fePerSpur` |
| Essentia | Thaumcraft `EssentiaCapabilities.TRANSPORT` | 128 | `essentia.pointsPerSpur`, overridden per aspect by `essentia.aspects` |
| Mana | Botania `MANA_RECEIVER` | 1M | `mana.manaPerSpur` |

No rate is hard-coded. `config/sunlitmarket/deposit_values.json` is written with defaults on first start; `accept` switches a whole channel off (it then values 0 and the screen row greys out), and `items` / `fluids` / `essentia.aspects` carry the per-id table. The file is the price table; KubeJS overrides land on top and are flushed back to it on the next save tick, so a script can stay the single source of truth.

Energy is priced worst on purpose: GT electricity has no production ceiling, so any meaningful rate would be a printing press. GT itself needs no special code - GTCEu machines and converters expose Forge `IEnergyStorage`, so a `gtceu:*_energy_converter` bridges EU into the FE port.

`collect()` prices everything, credits the wallet **first**, and only then clears the intakes. A failed payout therefore never destroys goods. With `wallet.enabled` off it falls back to `Currency.settle`, which can still fail on a full inventory and then leaves the bin untouched.

Optional mods are optional at runtime, not just at compile time: the Thaumcraft port compiles against the exact 20748 api jar vendored in `libs/` and is only instantiated behind `ModList.isLoaded("thaumcraft")`; Botania is reached with a dynamic proxy, so there is no 12 MB compile dependency and no class-loading failure on a pack without it.

Client/server split: the block, block entity, menu and valuation live outside `client/`; only `DepositBinScreen` touches `net.minecraft.client.*`. State reaches the screen as `DepositStateMessage` (S2C id 8), and the three buttons ride `clickMenuButton(0..2)` (collect / deposit cash / withdraw all) so no C2S packet is needed.

Known gap: the bin still pays without a daily budget. That is the `BuybackBudgetManager` from `ECONOMY_MODEL_V2.md` §7 and is not implemented yet.

## Virtual wallet

`Wallet` is a per-player spur balance kept in the player's Forge persistent data, so it survives death, dimension changes and reloads without a capability registration pass. It is not a second inventory of coins: it is an account.

- `Currency.balance()` is cash/bank **plus** wallet; `Currency.cashBalance()` is the physical/bank number only. Affordability displays use the former.
- A debit is paid from the wallet when the wallet covers it whole, otherwise it falls through to coins/bank exactly as before. A payment is never split across two backends, so a half-empty wallet behaves like an absent one.
- Credits from ordinary trade still go to coins/bank; only the acceptance bin and `Wallet.add` credit the wallet directly.
- `WalletExchange.deposit()` converts cash into the wallet and `withdraw()` converts back; both charge `wallet.exchangeFeePercent` and the fee is simply never minted, which is what stops the pair from being a free laundering loop. `deposit` debits through `Currency.payCash`, which skips the wallet leg - otherwise the wallet would pay for its own top-up and the fee would be pure burn.
- GUI: the bin screen's wallet band shows the balance, the fee and 存入/取出. `/sunlitmarket wallet [deposit|withdraw <n>]` does the same without the block.

## Trading counter

Craft `sunlitmarket:counter` using wooden slabs and planks, or obtain it with a development command. Its nine inventory slots are shared only with the owning player/team. Hoppers have no exposed sided slots.

A CIVILIAN NPC within the configured radius can generate a request from an explicit pool. Default range: 8 blocks; maximum: 3 requests; lifetime: 1200 ticks. A world-wide per-NPC cooldown/quota prevents simultaneous counters multiplying a customer's offers. No pathfinding or ME NPC inventory/AI is modified.

Each request contains a stable ID, NPC, faction, goods, base value, market value, asking price and expiry. Accepting makes the acting player pay from their own wallet and delivers goods to counter storage. Haggle once using the displayed 10%/20% reductions, or refuse. Replayed/expired requests cannot settle again. Full storage or failed payment leaves the request and possessions unchanged.

An `Offer` with `kind: "offer"` explicitly contributes to a merchant faction's request pool; missing `kind` stays `stock`. Supply `baseValue`, optional `marketValue`/`askPrice`, item/count and optional `repGain`/heat. Existing stock goods are not automatically made into NPC supply.

## Crime and enforcement

Stolen goods require explicit provenance: a configured request or `SunlitMarket.markStolen`. Ordinary possessions are never inferred to be stolen. Heat lives on the actual stack and decays in online player inventories. Current non-inventory containers do not automatically cool items.

Contraband rules explicitly match an item/tag, faction, dimension and tier 1–4. Inspectors must be explicitly selected by NPC tag/type and jurisdiction. No default pack laws are installed. Inspection checks wanted threshold, cooldown, nearby living inspector and same-dimension jurisdiction. Confiscation and wanted-price effects have independent switches; fines default to zero. Wanted records use `teams.wantedScope`: `PERSONAL` (default) or `TEAM` (the FTB party owner). Switching scope preserves both record sets without copying them, so joining/leaving or changing policy cannot erase or duplicate wanted value. `/sunlitmarket wantedscope personal|team` changes the runtime policy; edit TOML for persistence.

## KubeJS

Keep existing `SunlitMarket` methods and legacy reputation aliases. New examples live in `examples/core.js`:

- `rule({...})`, `clearRules()` register/clear script rules.
- `owner(player)`, `identity(entity)`, `regionWeight(entity, faction)` expose adapter results.
- `heat(stack)`, `markStolen(stack, heat)` manage explicit provenance.
- `depositPrice(itemId, spur)`, `depositFluidRate(fluidId, perBucket)` and `depositAccept(channel, allowed)` override the acceptance-bin table at runtime; `depositItemValue(itemId)` reads the effective rate and `depositReload()` re-reads the file. Overrides are flushed back to `deposit_values.json` on the next save tick.
- `wallet(player)`, `walletAdd`, `walletTake`, `walletDeposit(player)` and `walletWithdraw(player, amount)` expose the virtual account.
- `playerFaction(player)` returns a cached on-disk ME allegiance snapshot or `UNKNOWN`; it is not a live authority and never writes ME SavedData.
- `SunlitMarketEvents.beforeRequest/afterRequest/beforeTrade/afterTrade/beforeInspection/afterInspection` expose `event.data`.
- `event.cancelTrade()` cancels a before action. `event.setPrice(n)` changes generated request quotes; changing an existing counter quote requires another player confirmation. Stock price changes should use the pricing API.

After events are notifications, not rollback hooks. Recursive settlement from within a hook is rejected. Scripts must not use `const` in this project's Rhino environment; use `let`.

## Verification

- Pure tests: `H:/MinecraftMods/gradle-8.14.5/bin/gradle.bat --no-daemon test build`.
- Minimal dedicated server: `-PcoreMinimal runServer` uses ignored `run-minimal` without KubeJS/ME/FTB/Numismatics runtime dependencies.
- `/sunlitmarket selftest`: explicitly invoked in an isolated single-player world (dedicated servers need `-Dsunlitmarket.allowSelfTest=true`). It temporarily uses inventory and an empty position two blocks overhead, restores them, and reports assertions. Do not use it in production.
- `/sunlitmarket identity <entity>` shows resolved NPC identity.
- `/sunlitalignment` opens the scrollable alignment view.

See `TESTING.md` for observed results and remaining verification gaps, rather than treating a successful build as gameplay acceptance.
