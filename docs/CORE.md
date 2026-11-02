# SunlitMarket core

## Scope

This project owns the trading engine and optional ME/FTB adapters. It does not install Sunlit Valley recipes, production prices, quest gates or server releases. Player-to-player shop customers are deferred; current requests are NPCs selling goods to a player-operated counter. The pack-side economy/price analysis lives in `SUNLIT_VALLEY_INTEGRATION.md`.

## Configuration

- `config/sunlitmarket-common.toml`: existing alignment ranges, diplomatic backlash, decay and price ladder.
- `config/sunlitmarket-core.toml`: team sharing, refusal threshold, same-quote price protection, counter limits and crime policy.
- `config/sunlitmarket/merchants.json`: legacy merchant array or `{ "merchants": [...] }`.
- `config/sunlitmarket/rules.json`: NPC identities, explicit request pools and contraband laws. See `examples/core-rules.json`.
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
