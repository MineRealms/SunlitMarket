package com.society.sunlitmarket.client;

import com.google.gson.*;
import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.counter.CounterMenu;
import com.society.sunlitmarket.net.CounterActionMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.ArrayList;
import java.util.List;

public final class CounterScreen extends AbstractContainerScreen<CounterMenu>{
    private JsonObject state=new JsonObject();
    private final List<Button> actions=new ArrayList<>();
    private int offset;
    private boolean pending;
    public CounterScreen(CounterMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=310;imageHeight=236;inventoryLabelY=137;inventoryLabelX=67;}
    public static void update(int menu,String json){
        if(Minecraft.getInstance().screen instanceof CounterScreen s&&s.menu.containerId==menu){
            try{s.state=JsonParser.parseString(json).getAsJsonObject();s.pending=false;s.buttons();}catch(RuntimeException ignored){}
        }
    }
    @Override protected void init(){super.init();buttons();}
    private JsonArray rows(){return state.has("requests")?state.getAsJsonArray("requests"):new JsonArray();}
    private void buttons(){
        actions.forEach(this::removeWidget);actions.clear();
        offset=Math.min(offset,Math.max(0,rows().size()-3));
        for(int i=0;i<Math.min(3,rows().size()-offset);i++){
            JsonObject r=rows().get(i+offset).getAsJsonObject();int y=topPos+40+i*27;
            addAction("收购",leftPos+152,y,36,r,0);
            if(!r.get("negotiated").getAsBoolean()){addAction("-10%",leftPos+190,y,38,r,2);addAction("-20%",leftPos+230,y,38,r,3);}
            addAction("拒绝",leftPos+270,y,36,r,1);
        }
        if(offset>0)addNav("<",leftPos+260,-3);
        if(offset+3<rows().size())addNav(">",leftPos+284,3);
    }
    private void addNav(String text,int x,int delta){Button b=Button.builder(Component.literal(text),btn->{offset+=delta;buttons();}).bounds(x,topPos+7,20,18).build();actions.add(addRenderableWidget(b));}
    private void addAction(String text,int x,int y,int width,JsonObject request,int action){
        Button b=Button.builder(Component.literal(text),btn->{
            if(pending||!state.has("session"))return;pending=true;
            SunlitMarketMod.CHANNEL.sendToServer(new CounterActionMessage(menu.containerId,state.get("session").getAsString(),state.get("sequence").getAsLong(),request.get("id").getAsString(),action));
        }).bounds(x,y,width,18).build();actions.add(addRenderableWidget(b));
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF252B32);
        g.fill(leftPos+2,topPos+2,leftPos+imageWidth-2,topPos+25,0xFF4E4432);
        for(var slot:menu.slots)g.fill(leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y+17,0xFF60656B);
    }
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){
        g.drawString(font,title,8,8,0xFFD37F,false);
        g.drawString(font,"NPC 求售 · 基准 / 市场 / 开价",8,28,0xCCCCCC,false);
        for(int i=0;i<Math.min(3,rows().size()-offset);i++){
            JsonObject r=rows().get(i+offset).getAsJsonObject();int y=40+i*27;
            String item=r.get("item").getAsString();
            g.drawString(font,font.plainSubstrByWidth(item+" x"+r.get("count").getAsInt(),140),8,y,0xFFFFFF,false);
            String prices=r.get("base")+" / "+r.get("market")+" / "+r.get("ask")+"  "+r.get("seconds")+"s";
            g.drawString(font,prices,8,y+11,0xFFD37F,false);
        }
        if(rows().isEmpty())g.drawString(font,"等待附近合格顾客（需配置商品池）",8,55,0xAAAAAA,false);
        if(state.has("message"))g.drawString(font,font.plainSubstrByWidth(state.get("message").getAsString(),290),8,111,0xFFCC88,false);
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial){renderBackground(g);super.render(g,x,y,partial);renderTooltip(g,x,y);}
}
