package com.society.sunlitmarket.counter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

public final class CounterBlock extends BaseEntityBlock {
    public CounterBlock(){super(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).strength(2.5F,3600000F).pushReaction(PushReaction.BLOCK));}
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new CounterBlockEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type){return l.isClientSide?null:createTickerHelper(type,CounterRegistry.ENTITY.get(),CounterBlockEntity::tick);}
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,LivingEntity entity,ItemStack stack){
        super.setPlacedBy(l,p,s,entity,stack);
        if(!l.isClientSide&&l.getBlockEntity(p) instanceof CounterBlockEntity be&&entity instanceof Player player){be.clearContent();be.assign(player);}
    }
    @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
        if(!l.isClientSide&&player instanceof ServerPlayer sp&&l.getBlockEntity(p) instanceof CounterBlockEntity be){
            if(be.access(player))NetworkHooks.openScreen(sp,be,p);
            else player.displayClientMessage(net.minecraft.network.chat.Component.literal("该柜台属于其他玩家或队伍"),true);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    @Override public void onRemove(BlockState old,Level l,BlockPos p,BlockState next,boolean moving){
        if(old.getBlock()!=next.getBlock()&&l.getBlockEntity(p) instanceof CounterBlockEntity be){
            if(!l.isClientSide){Containers.dropContents(l,p,be);be.clearContent();be.requests.clear();}
        }
        super.onRemove(old,l,p,next,moving);
    }
}
