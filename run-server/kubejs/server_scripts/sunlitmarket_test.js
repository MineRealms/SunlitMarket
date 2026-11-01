SunlitMarket.clear()
SunlitMarket.coinValue('minecraft:emerald', 1)
SunlitMarket.merchant({
  id: 'test_farmer',
  name: 'Test Farmer',
  faction: 'test:village',
  tag: 'sunlitmarket.test',
  offers: [
    { item: 'minecraft:wheat', count: 1, buy: 2, sell: 1 },
    { item: 'minecraft:bread', count: 1, buy: 4, sell: 2, reputation: 5 }
  ]
})
console.info('[SunlitMarket TEST] merchants=' + SunlitMarket.merchants())
