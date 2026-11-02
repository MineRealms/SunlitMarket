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
