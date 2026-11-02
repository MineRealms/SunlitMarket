from pathlib import Path

root = Path(__file__).resolve().parents[1] / 'src/main/java/com/society/sunlitmarket'
p = root / 'market/MarketView.java'
s = p.read_text(encoding='utf-8')
s = s.replace('    public int balance;', '    public String session = "";\n    public long sequence;\n    public int balance;')
s = s.replace('        public String item = "";', '        public String offerId = "";\n        public boolean hasReputation;\n        public String item = "";')
s = s.replace('        o.addProperty("balance", balance);', '        o.addProperty("session", session);\n        o.addProperty("sequence", sequence);\n        o.addProperty("balance", balance);')
s = s.replace('            f.addProperty("item", line.item);', '            f.addProperty("offerId", line.offerId);\n            f.addProperty("hasReputation", line.hasReputation);\n            f.addProperty("item", line.item);')
s = s.replace('            view.balance =', '            view.session = o.has("session") ? o.get("session").getAsString() : "";\n            view.sequence = o.has("sequence") ? o.get("sequence").getAsLong() : 0;\n            view.balance =')
s = s.replace('                    Line line = new Line();', '                    Line line = new Line();\n                    line.offerId = f.has("offerId") ? f.get("offerId").getAsString() : "legacy:" + view.offers.size();\n                    line.hasReputation = f.has("hasReputation") && f.get("hasReputation").getAsBoolean();')
p.write_text(s, encoding='utf-8')
p = root / 'client/MarketScreen.java'
s = p.read_text(encoding='utf-8')
s = s.replace('    private String error;', '    private String error;\n    private int offset;\n    private boolean pending;\n    private int visibleRows() { return Math.max(1, Math.min(8, (height - 120) / 26)); }')
s = s.replace('        this.clearWidgets();', '        pending = false;\n        offset = Math.min(offset, Math.max(0, this.view.offers.size() - visibleRows()));\n        this.clearWidgets();')
s = s.replace('int rows = Math.min(view.offers.size(), 8);', 'int rows = Math.min(view.offers.size() - offset, visibleRows());')
s = s.replace('final int index = i;', 'final int index = i + offset;')
s = s.replace('view.offers.get(i)', 'view.offers.get(i + offset)')
s = s.replace('        addRenderableWidget(Button.builder(Component.literal("关闭")', '        if (offset > 0) addRenderableWidget(Button.builder(Component.literal("上一页"), b -> { offset = Math.max(0, offset - visibleRows()); clearWidgets(); init(); }).bounds(20, height - 30, 65, 20).build());\n        if (offset + visibleRows() < view.offers.size()) addRenderableWidget(Button.builder(Component.literal("下一页"), b -> { offset += visibleRows(); clearWidgets(); init(); }).bounds(width - 85, height - 30, 65, 20).build());\n        addRenderableWidget(Button.builder(Component.literal("关闭")')
s = s.replace('        SunlitMarketMod.CHANNEL.sendToServer(new MarketActionMessage(view.merchantId, index, action, 1));', '        if (pending || view.session.isEmpty()) return;\n        pending = true;\n        MarketView.Line line = view.offers.get(index);\n        SunlitMarketMod.CHANNEL.sendToServer(new MarketActionMessage(view.merchantId, index, action, 1, view.session, view.sequence, line.offerId, line.buy, line.sell));')
s = s.replace('if (line.reputation > 0)', 'if (line.hasReputation)')
s = s.replace('    @Override\n    public boolean isPauseScreen()', '    @Override\n    public void onClose() {\n        SunlitMarketMod.CHANNEL.sendToServer(new MarketActionMessage(view.merchantId, 0, -1, 0, view.session, view.sequence, "", 0, 0));\n        super.onClose();\n    }\n\n    @Override\n    public boolean isPauseScreen()')
p.write_text(s, encoding='utf-8')
p = root / 'SunlitMarketMod.java'
s = p.read_text(encoding='utf-8').replace('private static final String PROTOCOL = "1";', 'private static final String PROTOCOL = "2";')
s = s.replace('        MarketService.open(player, merchant);\n    }\n\n    private void onEntityJoin', '        MarketService.open(player, merchant, event.getTarget());\n    }\n\n    private void onEntityJoin')
s = s.replace('return entity.getTags().stream().anyMatch(tag -> tag.startsWith("sunlitmarket."));', 'return MarketRegistry.resolve(entity) != null;')
s = s.replace('event.getEntity().getTags().stream().anyMatch(tag -> tag.startsWith("sunlitmarket."))', 'isMerchant(event.getEntity())')
s = s.replace('        FactionRelations.reload(event.getServer());', '        FactionRelations.reload(event.getServer());\n        lastAlignmentDay = Long.MIN_VALUE;\n        com.society.sunlitmarket.market.TradeSessions.clear();')
p.write_text(s, encoding='utf-8')
p = root / 'market/MarketRegistry.java'
s = p.read_text(encoding='utf-8').replace('if (merchant.valid()) SCRIPTS.put(merchant.id, merchant);', 'if (merchant.valid()) { SCRIPTS.put(merchant.id, merchant); changed(); }')
s = s.replace('            merchant.offers.add(offer);', '            merchant.offers.add(offer); changed();').replace('        SCRIPTS.clear();', '        SCRIPTS.clear(); changed();')
p.write_text(s, encoding='utf-8')
