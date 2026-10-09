package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Pavement Markings V1: a tinted paint tile (pavement_hatch, pavement_crosshatch, pavement_bar). The texture is white paint;
 * {@link #COLOR} tints it (client/PavementMarkingClient). Right click with white, yellow or blue dye repaints it.
 */
public class PavementPaintBlock extends PavementMarkingBlock {
    public enum Paint implements StringRepresentable {
        WHITE("white", 0xFFFFFF), YELLOW("yellow", 0xF6C343), BLUE("blue", 0x326ABF);

        private final String name;
        private final int tint;

        Paint(String name, int tint) {
            this.name = name;
            this.tint = tint;
        }

        /** The multiplier on the white paint texture: white [236,235,230] to yellow [228,180,60] / blue [46,98,172]. */
        public int tint() {
            return tint;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** The paint a dye gives, or null. */
        public static @Nullable Paint of(ItemStack stack) {
            return stack.is(Items.WHITE_DYE) ? WHITE : stack.is(Items.YELLOW_DYE) ? YELLOW : stack.is(Items.BLUE_DYE) ? BLUE : null;
        }
    }

    public static final EnumProperty<Paint> COLOR = EnumProperty.create("color", Paint.class);

    public PavementPaintBlock(Properties properties, Paint paint) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(COLOR, paint));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COLOR);
    }

    /** The paint a dye in hand would change this to, or null (not a paint dye, or already that colour). */
    public static @Nullable Paint repaint(BlockState state, ItemStack stack) {
        Paint paint = Paint.of(stack);
        return paint == null || !state.hasProperty(COLOR) || state.getValue(COLOR) == paint ? null : paint;
    }

    /** Pick block keeps the colour. */
    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return asItem() instanceof com.antaurora.apofirstlight.item.PavementPaintBlockItem item ? item.colored(state.getValue(COLOR)) : super.getCloneItemStack(level, pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        Paint paint = repaint(state, stack);
        if (paint == null) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(COLOR, paint), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
