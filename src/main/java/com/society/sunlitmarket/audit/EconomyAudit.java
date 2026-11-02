package com.society.sunlitmarket.audit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Fail-open observation sink. It never throws into a trade or blocks on a writer thread. */
public final class EconomyAudit {
    private static final Gson GSON=new GsonBuilder().disableHtmlEscaping().create();
    private static final AtomicLong SEQUENCE=new AtomicLong();
    private static volatile String epoch="";
    private static volatile Path file;
    private static volatile BufferedWriter writer;
    private static volatile boolean initialized;
    private static volatile long dropped;
    private EconomyAudit(){}
    public static synchronized void start(MinecraftServer server){
        if(initialized)return;
        epoch=UUID.randomUUID().toString();
        Path dir=server.getWorldPath(LevelResource.ROOT).resolve("sunlitmarket").resolve("audit");
        try{Files.createDirectories(dir);file=dir.resolve("events-"+epoch+".jsonl");writer=Files.newBufferedWriter(file,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);initialized=true;}
        catch(IOException ex){initialized=true;dropped++;}
    }
    public static synchronized void stop(){
        if(writer!=null)try{writer.flush();writer.close();}catch(IOException ignored){dropped++;}
        writer=null;initialized=false;
    }
    public static void observe(MinecraftServer server,String source,String reason,String backend,String outcome,long requested,Long confirmed,UUID player,String valuation,String note){
        try{
            if(!initialized)start(server);
            AuditCheckpoint checkpoint=AuditCheckpoint.get(server);checkpoint.markAttempted();
            long seq=SEQUENCE.incrementAndGet();long game=server.overworld().getGameTime(),day=server.overworld().getDayTime();
            ObservedMoneyEvent event=new ObservedMoneyEvent(UUID.randomUUID(),epoch,seq,game,day,source,reason,backend,outcome,requested,confirmed,player,valuation,note);
            BufferedWriter out=writer;
            if(out==null){dropped++;checkpoint.markDropped();return;}
            synchronized(EconomyAudit.class){out.write(GSON.toJson(event.json()));out.newLine();out.flush();}
            checkpoint.written(source,confirmed==null?0:confirmed,confirmed!=null);
        }catch(Throwable ignored){dropped++;try{AuditCheckpoint.get(server).markDropped();}catch(Throwable ignoredAgain){}}
    }
    public static void observeSettlement(MinecraftServer server,String reason,String backend,String outcome,long delta,UUID player,String note){observe(server,"CURRENCY_SETTLEMENT",reason,backend,outcome,delta,"CONFIRMED".equals(outcome)?delta:null,player,"runtime",note);}
    public static String status(MinecraftServer server){
        AuditCheckpoint c=AuditCheckpoint.get(server);return "scope=SUNLITMARKET_SETTLEMENTS_ONLY epoch="+epoch+" attempted="+c.attempted()+" written="+c.written()+" dropped="+(c.dropped()+dropped)+" confirmed="+c.confirmed()+" unknown="+c.unknown()+" failed="+c.failed()+" observedNet="+c.observedNet()+" utc="+LocalDate.now(ZoneOffset.UTC);
    }
    public static long dropped(){return dropped;}
}
