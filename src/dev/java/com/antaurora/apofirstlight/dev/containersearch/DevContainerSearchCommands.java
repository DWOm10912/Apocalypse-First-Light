package com.antaurora.apofirstlight.dev.containersearch;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.containersearch.AflContainerGoods;
import com.antaurora.apofirstlight.containersearch.AflContainerSearch;
import com.antaurora.apofirstlight.containersearch.AflGoodsThemes;
import com.antaurora.apofirstlight.containersearch.AflSearchableContainer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * DEVELOPMENT ONLY (OP 2).
 * <ul>
 * <li>{@code /dev container_search info}: read-only state of the searchable container in view.</li>
 * <li>{@code /dev container_search spawn <block> [theme <theme>] [loot <table> | fill <slots>]}: places an AFL searchable
 * container on the block in view, as a player would (facing the player, every cell of a multi-cell block), then turns it
 * into unsearched world loot: from the loot table (default {@link #DEFAULT_LOOT}; the freezer rolls it on its first
 * server tick, the locker when its door first opens, the others on first opening), or with that many random slots of
 * test food, hidden until searched. {@code theme} sets the goods theme outright (containers with themed goods only,
 * AflContainerGoods.Themed); otherwise it follows the loot table (AflGoodsThemes).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DevContainerSearchCommands {
    private static final ResourceLocation DEFAULT_LOOT = new ResourceLocation("chests/simple_dungeon");
    private static final Item[] TEST_FOOD = {Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.MUTTON, Items.RABBIT, Items.COD,
            Items.SALMON, Items.SWEET_BERRIES, Items.MELON_SLICE, Items.PUMPKIN_PIE, Items.COOKIE, Items.ICE, Items.SNOWBALL};
    private static List<ResourceLocation> searchableBlocks;

    private DevContainerSearchCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dev")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("container_search")
                        .then(Commands.literal("info").executes(DevContainerSearchCommands::info))
                        .then(Commands.literal("spawn")
                                .then(contents(Commands.argument("block", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(searchableBlocks(), builder)), false)
                                        .then(Commands.literal("theme")
                                                .then(contents(Commands.argument("theme", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(AflGoodsThemes.themes(), builder)), true)))))));
    }

    /** The spawn endings under a node: nothing (default loot), loot <table>, fill <slots>; themed reads the theme argument. */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T contents(T node, boolean themed) {
        return node.executes(context -> spawn(context, DEFAULT_LOOT, -1, theme(context, themed)))
                .then(Commands.literal("loot")
                        .then(Commands.argument("table", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                        context.getSource().getServer().getLootData().getKeys(LootDataType.TABLE), builder))
                                .executes(context -> spawn(context, ResourceLocationArgument.getId(context, "table"), -1, theme(context, themed)))))
                .then(Commands.literal("fill")
                        .then(Commands.argument("slots", IntegerArgumentType.integer(0, 54))
                                .executes(context -> spawn(context, null, IntegerArgumentType.getInteger(context, "slots"), theme(context, themed)))));
    }

    @Nullable
    private static String theme(CommandContext<CommandSourceStack> context, boolean themed) {
        return themed ? StringArgumentType.getString(context, "theme") : null;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (player.pick(8.0D, 0.0F, false) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && player.level().getBlockEntity(hit.getBlockPos()) instanceof AflSearchableContainer container) {
            String summary = hit.getBlockPos().toShortString() + " " + AflContainerSearch.debugSummary(container);
            context.getSource().sendSuccess(() -> Component.literal(summary), false);
            return 1;
        }
        context.getSource().sendFailure(Component.literal("Look at a searchable container within 8 blocks."));
        return 0;
    }

    /** lootTable for loot mode, or fill >= 0 slots of test food; theme: the goods theme outright, or null. */
    private static int spawn(CommandContext<CommandSourceStack> context, @Nullable ResourceLocation lootTable, int fill,
                             @Nullable String theme) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(context, "block");
        if (theme != null && !AflGoodsThemes.themes().contains(theme)) {
            source.sendFailure(Component.literal("Unknown goods theme: " + theme + " (loaded: " + AflGoodsThemes.themes() + ")"));
            return 0;
        }
        if (!searchableBlocks().contains(id)) {
            source.sendFailure(Component.literal("Not an AFL searchable container: " + id + " (try " + searchableBlocks() + ")"));
            return 0;
        }
        if (!(player.pick(8.0D, 0.0F, false) instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("Look at a block within 8 blocks: the container goes on its face."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        BlockPos at;
        if (block.asItem() instanceof BlockItem item) {
            BlockPlaceContext place = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(item), hit);
            if (!item.place(place).consumesAction()) {
                source.sendFailure(Component.literal("Could not place " + id + " there (room, support or rules)."));
                return 0;
            }
            at = place.getClickedPos();
        } else {
            at = hit.getBlockPos().relative(hit.getDirection());
            if (!level.getBlockState(at).canBeReplaced()) {
                source.sendFailure(Component.literal("No room for " + id + " at " + at.toShortString()));
                return 0;
            }
            level.setBlock(at, block.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (!(level.getBlockEntity(at) instanceof RandomizableContainerBlockEntity container)
                || !(container instanceof AflSearchableContainer searchable)) {
            source.sendFailure(Component.literal("Placed " + id + " at " + at.toShortString() + ", but found no searchable container there."));
            return 0;
        }
        // forget the placement's "player's own, never searched": the next contact decides again, as for world loot
        container.clearContent();
        searchable.aflSearchState().load(new CompoundTag());
        long seed = level.random.nextLong();
        String what;
        if (fill < 0) {
            container.setLootTable(lootTable, seed);
            what = "loot " + lootTable;
        } else {
            container.setLootTable(BuiltInLootTables.EMPTY, seed);   // marks it world loot and rolls nothing
            List<Integer> slots = new ArrayList<>();
            for (int slot = 0; slot < container.getContainerSize(); slot++) slots.add(slot);
            int count = Math.min(fill, slots.size());
            for (int i = 0; i < count; i++) {
                int slot = slots.remove(level.random.nextInt(slots.size()));
                Item food = TEST_FOOD[level.random.nextInt(TEST_FOOD.length)];
                container.setItem(slot, new ItemStack(food, 1 + level.random.nextInt(Math.min(16, food.getMaxStackSize()))));
            }
            what = count + " slots of test food";
        }
        if (theme != null) {
            if (container instanceof AflContainerGoods.Themed themed) {
                themed.setGoodsTheme(theme);
                what += ", goods theme " + theme;
            } else {
                what += " (theme ignored: " + id + " has no goods themes)";
            }
        }
        container.setChanged();
        level.sendBlockUpdated(at, container.getBlockState(), container.getBlockState(), Block.UPDATE_CLIENTS);
        BlockPos placed = at;
        String message = "Spawned " + id + " at " + placed.toShortString() + " as unsearched world loot: " + what;
        source.sendSuccess(() -> Component.literal(message), true);
        return 1;
    }

    /** AFL blocks whose block entity (default state) is a searchable container; worked out once. */
    private static List<ResourceLocation> searchableBlocks() {
        if (searchableBlocks == null) {
            List<ResourceLocation> ids = new ArrayList<>();
            for (var entry : ForgeRegistries.BLOCKS.getEntries()) {
                ResourceLocation id = entry.getKey().location();
                if (!id.getNamespace().equals(ApocalypseFirstLight.MOD_ID) || !(entry.getValue() instanceof EntityBlock entity)) continue;
                try {
                    if (entity.newBlockEntity(BlockPos.ZERO, entry.getValue().defaultBlockState()) instanceof AflSearchableContainer) ids.add(id);
                } catch (RuntimeException ignored) {
                    // a block entity that cannot be built off-level is not one we can spawn here
                }
            }
            ids.sort(null);
            searchableBlocks = List.copyOf(ids);
        }
        return searchableBlocks;
    }
}
