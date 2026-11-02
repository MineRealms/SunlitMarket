from pathlib import Path
root=Path(__file__).resolve().parents[1]/'src/main/java/com/society/sunlitmarket'
p=root/'market/MarketService.java';s=p.read_text(encoding='utf-8')
s=s.replace('        Merchant m = MarketRegistry.get(merchantId);','        if (com.society.sunlitmarket.core.MarketHooks.dispatching()) return false;\n        Merchant m = MarketRegistry.get(merchantId);')
s=s.replace('        Quote q = quote(p, m, offer);\n        if (!id.equals(offerId)', '        Quote q;\n        try { q = quote(p, m, offer); } catch (IllegalArgumentException ex) { update(p,m,"报价超出范围"); return; }\n        if (!id.equals(offerId)')
s=s.replace('        String result = Currency.settle(p,', '''        var hook = com.society.sunlitmarket.core.MarketHooks.fire(new com.society.sunlitmarket.core.MarketHooks.Event(
                "beforeTrade",p,null,m.faction,key,Currency.item(offer.item),total));
        if (hook.cancelled) return "交易被脚本取消";
        if (hook.price != total) return "货架价格变更请使用价格倍率接口并重新确认";
        String result = Currency.settle(p,''')
s=s.replace('        if (reward > 0) AlignmentData.get(p.server).add(p, m.faction, reward);','''        if (reward > 0) AlignmentData.get(p.server).add(p, m.faction, reward);
        com.society.sunlitmarket.core.MarketHooks.fire(new com.society.sunlitmarket.core.MarketHooks.Event(
                "afterTrade",p,null,m.faction,key,Currency.item(offer.item),total));''')
p.write_text(s,encoding='utf-8')
p=root/'counter/CounterBlockEntity.java';s=p.read_text(encoding='utf-8')
s=s.replace('            be.requests.add(request);be.setChanged();','''            var hook=com.society.sunlitmarket.core.MarketHooks.fire(new com.society.sunlitmarket.core.MarketHooks.Event(
                    "beforeRequest",null,npc,identity.faction(),request.id.toString(),request.goods,request.ask));
            if(hook.cancelled||hook.price<1||hook.price>10000000)continue;
            request.ask=hook.price;
            be.requests.add(request);be.setChanged();
            com.society.sunlitmarket.core.MarketHooks.fire(new com.society.sunlitmarket.core.MarketHooks.Event(
                    "afterRequest",null,npc,identity.faction(),request.id.toString(),request.goods,request.ask));''')
s=s.replace('        if(!access(p)||!stillValid(p))', '        if(com.society.sunlitmarket.core.MarketHooks.dispatching())return "禁止递归交易";\n        if(!access(p)||!stillValid(p))')
s=s.replace('        String result=Currency.settle(p,null,this,goods,goods.getCount(),-r.ask);','''        var npc=((ServerLevel)level).getEntity(r.npc);
        var hook=com.society.sunlitmarket.core.MarketHooks.fire(new com.society.sunlitmarket.core.MarketHooks.Event(
                "beforeTrade",p,npc,r.faction,r.id.toString(),goods,r.ask));
        if(hook.cancelled)return "收购被脚本取消";
        if(hook.price<1||hook.price>10000000)return "脚本报价无效";
        if(hook.price!=r.ask){r.ask=hook.price;setChanged();return "报价已更新，请重新确认";}
        String result=Currency.settle(p,null,this,goods,goods.getCount(),-r.ask);''')
s=s.replace('        return "收购完成，货物已入柜台";','''        com.society.sunlitmarket.core.MarketHooks.fire(new com.society.sunlitmarket.core.MarketHooks.Event(
                "afterTrade",p,npc,r.faction,r.id.toString(),goods,r.ask));
        return "收购完成，货物已入柜台";''')
s=s.replace('Math.min(100,Math.max(0,r.reward)*Math.max(1,TradeMath.amount(((long)Math.min(r.ask,r.market)+CoreConfig.FAIR_REWARD_DIVISOR.get()-1)/CoreConfig.FAIR_REWARD_DIVISOR.get())))','TradeMath.clamp((long)Math.max(0,r.reward)*Math.max(1,TradeMath.amount(((long)Math.min(r.ask,r.market)+CoreConfig.FAIR_REWARD_DIVISOR.get()-1)/CoreConfig.FAIR_REWARD_DIVISOR.get())),0,100)')
p.write_text(s,encoding='utf-8')
p=root/'market/LimitData.java';s=p.read_text(encoding='utf-8').replace('.merge(key, quantity, Integer::sum);','.merge(key, quantity, (a,b) -> com.society.sunlitmarket.core.TradeMath.clamp((long)a+b,0,Integer.MAX_VALUE));');p.write_text(s,encoding='utf-8')
