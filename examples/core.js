// Example only: copy deliberately into an isolated world's KubeJS server_scripts.
// Never install test rules automatically into a production pack.
SunlitMarket.clearRules()
SunlitMarket.rule({ id: 'example:customer', type: 'npc', tag: 'sunlitmarket.example.customer', faction: 'example:village', rank: 'CIVILIAN' })
SunlitMarket.rule({ id: 'example:apples', type: 'pool', faction: 'example:village', item: 'minecraft:apple', count: 2, baseValue: 5, marketValue: 6, askPrice: 7, repGain: 1 })

SunlitMarketEvents.beforeRequest(event => {
  // event.data contains npc, faction, requestId, item and price.
  // event.setPrice(7) changes the quote before it is shown.
  // event.cancelTrade() cancels creation without using the NPC quota.
})
SunlitMarketEvents.beforeTrade(event => {
  // For an existing counter quote, a changed price requires a new confirmation.
  // Stock price changes should use priceMultiplier and a refreshed quote.
  if (event.data.price <= 0) event.cancelTrade()
})
SunlitMarketEvents.afterTrade(event => {
  console.info('[market] completed ' + event.data.requestId)
})
SunlitMarketEvents.beforeInspection(event => {
  // event.cancelTrade() cancels this inspection; it does not erase the wanted record.
})

// ---- acceptance bin price table -------------------------------------------------
// Everything the bin pays is configurable; these calls write the same values the
// GUI-side config file holds, and the change is flushed back to
// config/sunlitmarket/deposit_values.json on the next save tick.
SunlitMarket.depositAccept('energy', false)              // no FE intake on this pack
SunlitMarket.depositPrice('minecraft:iron_ingot', 24)     // explicit spur each; 0 refuses it
SunlitMarket.depositPrice('minecraft:gold_ingot', 0)      // never buy gold from players
SunlitMarket.depositFluidRate('gtceu:diesel', 12)         // spur per bucket
console.info('[market] iron now worth ' + SunlitMarket.depositItemValue('minecraft:iron_ingot'))

// ---- virtual wallet -------------------------------------------------------------
// A per-player account that survives death and dimension changes. Ordinary trades
// still pay coins; the bin and these calls credit the wallet directly.
PlayerEvents.tick(event => {
  let player = event.player
  if (player.age % 200 !== 0) return
  // Quest-style reward that should not become loose coins:
  if (SunlitMarket.wallet(player) === 0) SunlitMarket.walletAdd(player, 50)
})
