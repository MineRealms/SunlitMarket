# Sunlit Market

Forge 1.20.1 NPC market/reputation companion for the Sunlit Valley pack.

- Server-authoritative trading GUI opened by right-clicking tagged entities
  (Middle-earth `me:npc` supported through tags or `NpcData.Type`, without
  compiling against the Connector-remapped mod).
- Independent per-player/per-faction reputation stored in `SavedData`.
- Optional Create: Numismatics bank via reflection; falls back to coin items.
- High KubeJS customisation: `SunlitMarket.merchant/offer/open/coinValue/reputation`.
- JSON merchants: `config/sunlitmarket/merchants.json`.

## Build

```powershell
H:\MinecraftMods\gradle-8.14.5\bin\gradle.bat --no-daemon build
```

Output: `build/libs/sunlitmarket-1.20.1-0.1.0.jar` (reobfuscated).

## Test client

`run-client/kubejs/server_scripts/sunlitmarket_test.js` registers a test
merchant and opens it automatically. The script registers `minecraft:emerald`
as a value-1 coin for the dev harness only; emeralds are not money by default.

```powershell
H:\MinecraftMods\gradle-8.14.5\bin\gradle.bat --no-daemon runClient -PquickPlay=smtest
```

Pack integration examples:

- `kubejs/server_scripts/sunlitmarket/meMerchants.js`
- `kubejs/startup_scripts/me/meTrades.js`

See `docs/SUNLIT_MARKET.md` in the pack for the full design and compatibility notes.

## Handoff

Read [`AGENTS.md`](AGENTS.md) first. It contains the architecture, KubeJS API,
test-client/Rosetta workflow, Connector/Fabric compatibility rules, known
limitations and the next task checklist.
