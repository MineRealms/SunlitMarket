package com.society.sunlitmarket.client;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

public final class AlignmentScreen extends Screen {
    private final List<String> lines=new ArrayList<>();private int offset;
    public AlignmentScreen(String json){super(Component.literal("派系结盟"));
        JsonObject o=JsonParser.parseString(json).getAsJsonObject();lines.add("归属："+o.get("owner").getAsString());lines.add("个人通缉："+o.get("wanted").getAsInt());
        for(JsonElement raw:o.getAsJsonArray("factions")){JsonObject f=raw.getAsJsonObject();lines.add(f.get("id").getAsString()+"  "+f.get("value").getAsInt()+"  "+f.get("title").getAsString());}
    }
    @Override protected void init(){addRenderableWidget(Button.builder(Component.literal("关闭"),b->onClose()).bounds(width/2-30,height-30,60,20).build());}
    @Override public boolean mouseScrolled(double x,double y,double delta){offset=Math.max(0,Math.min(Math.max(0,lines.size()-Math.max(1,(height-90)/18)),offset-(int)Math.signum(delta)));return true;}
    @Override public void render(GuiGraphics g,int x,int y,float partial){renderBackground(g);super.render(g,x,y,partial);g.drawString(font,title,20,15,0xFFD37F,false);int n=Math.min(lines.size()-offset,Math.max(1,(height-90)/18));for(int i=0;i<n;i++)g.drawString(font,lines.get(i+offset),20,42+i*18,0xFFFFFF,false);}
    @Override public boolean isPauseScreen(){return false;}
}
