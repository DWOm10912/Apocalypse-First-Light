package com.antaurora.apofirstlight.dev.meshtree;

import com.antaurora.apofirstlight.dev.meshtree.TreeForms.Species;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Mesh Trees P2 (docs/rendering/mesh_trees_p2_felling.md, K, T): the development-only registrations of the felling
 * test, outside production only (like the tree blocks; the dev package, models, textures and lang leave the jar).
 * <ul>
 * <li>Raw materials a felled tree yields, named as the AFL wood system plans them (docs/dev/wood_system_plan_v1.md):
 * {@code white_oak_log}, {@code white_pine_log}, {@code white_oak_branch}, {@code white_pine_branch}, and leaf material
 * {@code white_oak_leaves}, {@code white_pine_needles}. No recipes; the wood system's later formal registration takes
 * these ids over and this one goes. P2.2 (V): the logs are blocks ({@link TreeLogBlock}, one cubic metre, placeable for
 * building) with their block items; branches and leaf material stay plain items (the leaf material shows 3D in hand and on
 * the ground, its own icon in the inventory: item models only).</li>
 * <li>{@code dev_falling_tree}: the falling tree (FallingTree), one per tree only while it falls.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FellingRegistry {
    public static final ResourceLocation FALLING_TREE_ID = new ResourceLocation("apocalypse_firstlight", "dev_falling_tree");
    static EntityType<FallingTree> fallingTree;
    private static final TreeLogBlock[] LOG_BLOCKS = new TreeLogBlock[Species.values().length];
    private static final Item[] LOGS = new Item[Species.values().length], BRANCHES = new Item[Species.values().length],
            LEAVES = new Item[Species.values().length];

    private FellingRegistry() {}

    static String leavesId(Species s) { return s.leavesId; }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (FMLEnvironment.production) return;
        event.register(ForgeRegistries.Keys.BLOCKS, helper -> {
            for (Species s : Species.values()) {
                LOG_BLOCKS[s.ordinal()] = new TreeLogBlock(s);
                helper.register(new ResourceLocation("apocalypse_firstlight", s.id + "_log"), LOG_BLOCKS[s.ordinal()]);
            }
        });
        event.register(ForgeRegistries.Keys.ITEMS, helper -> {
            for (Species s : Species.values()) {
                int i = s.ordinal();
                LOGS[i] = new BlockItem(LOG_BLOCKS[i], new Item.Properties());
                BRANCHES[i] = new Item(new Item.Properties());
                LEAVES[i] = new Item(new Item.Properties());
                helper.register(new ResourceLocation("apocalypse_firstlight", s.id + "_log"), LOGS[i]);
                helper.register(new ResourceLocation("apocalypse_firstlight", s.id + "_branch"), BRANCHES[i]);
                helper.register(new ResourceLocation("apocalypse_firstlight", leavesId(s)), LEAVES[i]);
            }
        });
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            fallingTree = EntityType.Builder.<FallingTree>of(FallingTree::new, MobCategory.MISC).sized(0.8F, 0.8F)
                    .clientTrackingRange(12).updateInterval(40).noSummon().fireImmune().build(FALLING_TREE_ID.toString());
            helper.register(FALLING_TREE_ID, fallingTree);
        });
    }

    @SubscribeEvent
    public static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (FMLEnvironment.production || LOGS[0] == null) return;
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) for (Species s : Species.values()) event.accept(LOGS[s.ordinal()]);
        if (event.getTabKey() != CreativeModeTabs.INGREDIENTS) return;
        for (Species s : Species.values()) {
            event.accept(LOGS[s.ordinal()]);
            event.accept(BRANCHES[s.ordinal()]);
            event.accept(LEAVES[s.ordinal()]);
        }
    }

    static boolean ready() { return fallingTree != null; }

    static Item log(Species s) { return LOGS[s.ordinal()]; }
    static TreeLogBlock logBlock(Species s) { return LOG_BLOCKS[s.ordinal()]; }
    static Item branch(Species s) { return BRANCHES[s.ordinal()]; }
    static Item leaves(Species s) { return LEAVES[s.ordinal()]; }
}
