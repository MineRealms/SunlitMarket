// priority: 0
// ============================================================
// Seasonal price curve for Middle-earth produce merchants.
//
// Reads the real SereneSeasons season (no pack-specific global helper) and
// drives SunlitMarket's runtime price multipliers. Reputation discount/bonus
// is applied by the mod on top of these multipliers.
//
// Each item has a factor per season; the value is interpolated across the
// season boundary with a smoothstep so prices glide instead of snapping.
//
// NOTE: KubeJS/Rhino rejects array destructuring in arrow parameters, so this
// file uses plain index access everywhere.
// ============================================================
// KubeJS evaluates server scripts in a shared top-level scope: a bare top-level
// const collides on reload ("redeclaration of var X"), so the body is wrapped.
;(function () {
  if (typeof SunlitMarket !== 'undefined' && Platform.isLoaded('sereneseasons')) {
    const SeasonHelper = Java.loadClass('sereneseasons.api.season.SeasonHelper')
    const ORDER = ['spring', 'summer', 'autumn', 'winter']

    // [merchant, item, spring, summer, autumn, winter]
    const TABLE = [
      // Shire farmer: field crops peak in summer/autumn, scarce in winter.
      ['me_shire_farmer', 'me:leek', 0.90, 0.95, 1.10, 1.40],
      ['me_shire_farmer', 'me:cucumber', 1.00, 0.90, 1.05, 1.35],
      ['me_shire_farmer', 'me:lettuce', 0.90, 1.00, 1.05, 1.30],
      ['me_shire_farmer', 'me:tomato', 1.05, 0.90, 1.05, 1.35],
      ['me_shire_farmer', 'me:strawberry', 0.85, 1.00, 1.10, 1.45],
      // Forage: mushrooms are cheapest in autumn, available all year.
      ['me_shire_farmer', 'me:white_mushroom', 1.00, 1.05, 0.90, 1.10],
      // Hearty stews cost more when ingredients are scarce.
      ['me_shire_farmer', 'me:vegetable_soup', 0.95, 0.95, 1.00, 1.20],
      // Lothlorien herbalist: spring foraging, winter scarcity.
      ['me_lothlorien_herbalist', 'me:strawberries', 0.85, 1.00, 1.10, 1.40],
      ['me_lothlorien_herbalist', 'me:ghostshroom', 1.00, 1.05, 0.90, 1.15],
      ['me_lothlorien_herbalist', 'me:yellow_amanita', 1.00, 1.05, 0.90, 1.15],
      ['me_lothlorien_herbalist', 'me:trumpet_shroom', 1.00, 1.05, 0.90, 1.15],
      ['me_lothlorien_herbalist', 'me:pipeweed', 0.95, 0.90, 1.00, 1.25],
    ]

    const smoothstep = (t) => t * t * (3 - 2 * t)

    const multipliers = (season, progress) => {
      const index = Math.max(0, ORDER.indexOf(season))
      const next = (index + 1) % ORDER.length
      const blend = smoothstep(Math.min(1, Math.max(0, progress)))
      const result = []
      for (let i = 0; i < TABLE.length; i++) {
        const row = TABLE[i]
        const current = row[2 + index]
        const following = row[2 + next]
        const value = current + (following - current) * blend
        result.push([row[0], row[1], Math.round(value * 100) / 100])
      }
      return result
    }

    const apply = (server) => {
      let season = 'spring'
      let progress = 0
      try {
        const state = SeasonHelper.getSeasonState(server.overworld())
        season = String(state.getSeason()).toLowerCase()
        const duration = state.getSeasonDuration()
        if (duration > 0) progress = (state.getSeasonCycleTicks() % duration) / duration
      } catch (error) {
        console.warn('[SunlitMarket] season lookup failed: ' + error)
        return null
      }
      const values = multipliers(season, progress)
      let sample = ''
      for (let i = 0; i < values.length; i++) {
        const entry = values[i]
        SunlitMarket.priceMultiplier(entry[0], entry[1], entry[2])
        if (entry[1] === 'me:leek' || entry[1] === 'me:white_mushroom') {
          sample = sample + entry[1].replace('me:', '') + '=' + entry[2] + ' '
        }
      }
      return season + '@' + Math.round(progress * 100) + '% ' + sample
    }

    let lastKey = ''
    ServerEvents.tick((event) => {
      if (event.server.getTickCount() % 200 !== 0) return
      const key = apply(event.server)
      if (key && key !== lastKey) {
        lastKey = key
        console.info('[SunlitMarket] seasonal multipliers applied: ' + key)
      }
    })

    // Reapply immediately after /reload so an open shop keeps the right prices.
    ServerEvents.loaded((event) => {
      const key = apply(event.server)
      if (key) console.info('[SunlitMarket] seasonal multipliers (load): ' + key)
    })
  }

})()
