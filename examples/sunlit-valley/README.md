# Sunlit Valley integration scripts

These files are the versioned source of the deployed KubeJS integration. The live copies are in
the pack instance, which does not track them in its own git:

- `kubejs/server_scripts/sunlitmarket/*.js` - every `sv*.js`, `me*.js` and `themedMerchants.js` here
- `kubejs/startup_scripts/me/meTrades.js`
- `kubejs/data/dialog/dialogs/sunlitmarket_<npc>_choice.json` - copies in `dialogs/`
- `pack-patches/npcInteraction.js.diff` - the change made to the pack's own
  `kubejs/server_scripts/npcs/npcInteraction.js` (shows the shop/chart choice dialog); re-apply it
  if the pack script is replaced upstream

| Script | Role |
|---|---|
| `svCustomers.js` | Sunlit NPCs (by `dialog.npc.<id>.name`) and ambient villagers as counter customers, pools priced from `global.trades` |
| `svMeCustomers.js`, `meMerchants.js` | Middle-earth customers and merchants (types checked against the shipped ME 1.4.2 data) |
| `svMagicCustomers.js`, `themedMerchants.js` | AS / TC / Botania materials and themed merchants |
| `svMastery.js`, `svTradeStages.js` | stages <-> SunlitMarket unlocks |
| `svFriendship.js` | NPC friendship -> `society:<id>` alignment (raise only) |
| `svPayout.js` | payouts settle hospital debt first, like the shipping bin |
| `svMoneySupply.js` | bank total and debts -> audit (`M0`, bank accounts only) |
| `svHud.js` | SunlitHud providers and panel |
| `svAcceptBoundary.js`, `svDepositRates.js`, `svDepositItems.js` | acceptance bin: refuse shipping-bin items, rates for fluids/FE/essentia/mana/items |
| `svConsignment.js` | consignment fee |
| `svIndices.js`, `meSeasonPrices.js` | price indices, seasonal multipliers |

Deployment copies these to the main instance, -DEV and mirror. If you edit the live pack copies,
copy them back here when the change is accepted.

Price policy and deployment checklist: `docs/SUNLIT_VALLEY_INTEGRATION.md`; accounts and
futures: `docs/ECONOMY_ACCOUNTS.md`.
