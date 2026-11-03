package com.society.sunlitmarket.deposit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * The acceptance bin block. Right-click with goods in hand to feed it, sneak-right-click (or empty
 * hand) to open the payout screen.
 */
public final class DepositBinBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public DepositBinBlock() {
        super(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(3.5F, 6.0F).requiresCorrectToolForDrops());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DepositBinBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof DepositBinBlockEntity bin)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty() && !player.isShiftKeyDown()) {
            if (!bin.items().isItemValid(0, held)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "接受仓不收此物品（出货箱能卖的请放进出货箱）"), true);
                return InteractionResult.CONSUME;
            }
            ItemStack remainder = bin.items().insertItem(0, held, false);
            player.setItemInHand(hand, remainder);
            bin.setChanged();
            return InteractionResult.CONSUME;
        }
        if (held.isEmpty() && player.isShiftKeyDown()) {
            ItemStack back = bin.takeItem();
            if (!back.isEmpty() && !player.getInventory().add(back)) player.drop(back, false);
            return InteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, bin, pos);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState old, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (old.getBlock() != next.getBlock() && level.getBlockEntity(pos) instanceof DepositBinBlockEntity bin) {
            if (!level.isClientSide) bin.dropContents(level, pos);
        }
        super.onRemove(old, level, pos, next, moving);
    }
}
