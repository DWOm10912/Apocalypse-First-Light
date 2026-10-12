package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.tree.TreeBlocks;
import com.antaurora.apofirstlight.tree.TreeForms.Species;
import com.antaurora.apofirstlight.tree.TreeIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.Identifiers;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

import java.util.ArrayList;

/**
 * Jade for the mesh trees' trunk cells (docs/rendering/mesh_trees_p4_formal.md U2): tree_root, tree_wood and tree_stump are
 * one block each for all six species, so Jade named every trunk "Tree Root" / "Tree Trunk and Branches" / "Tree Stump".
 * The title becomes the species' trunk or stump ("Red Oak Trunk") and the icon its log. Root and stump carry the species
 * in their state; a wood cell asks the client's tree index which tree's trunk it is (only the trunk base is targetable,
 * TreeBlocks#collision). Client only: nothing comes from the server.
 */
public enum TreeSpeciesJadeProvider implements IBlockComponentProvider {
    INSTANCE;

    public static final ResourceLocation UID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "tree");
    private static final ThreadLocal<ArrayList<TreeIndex.Root>> FOUND = ThreadLocal.withInitial(ArrayList::new);

    /** The species of the trunk cell Jade looks at, or null (a wood cell no loaded tree claims). */
    static Species species(BlockAccessor accessor) {
        Block block = accessor.getBlock();
        if (block instanceof TreeBlocks.Root || block instanceof TreeBlocks.Stump) return TreeBlocks.species(accessor.getBlockState());
        if (!(block instanceof TreeBlocks.Wood)) return null;
        BlockPos p = accessor.getPosition();
        var found = FOUND.get();
        found.clear();
        TreeIndex.CLIENT.roots(p.getX(), p.getY(), p.getZ(), found);
        Species any = null;
        for (var r : found) {
            var cell = r.cellAt(p.getX(), p.getY(), p.getZ());
            if (cell == null) continue;
            if (cell.x == 0 && cell.z == 0) { any = r.layout.species; break; }   // this tree's own trunk column
            if (any == null) any = r.layout.species;                             // a branch of a neighbour passing through
        }
        found.clear();
        return any;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        Species s = species(accessor);
        if (s == null) return;
        String kind = accessor.getBlock() instanceof TreeBlocks.Stump ? "stump" : "trunk";
        tooltip.remove(Identifiers.CORE_OBJECT_NAME);
        tooltip.add(0, IThemeHelper.get().title(Component.translatable("jade.apocalypse_firstlight.tree." + kind + "." + s.id)),
                Identifiers.CORE_OBJECT_NAME);
    }

    @Override
    public IElement getIcon(BlockAccessor accessor, IPluginConfig config, IElement currentIcon) {
        Species s = species(accessor);
        if (s == null) return currentIcon;
        Item log = ForgeRegistries.ITEMS.getValue(new ResourceLocation(ApocalypseFirstLight.MOD_ID, s.id + "_log"));
        return log == null || log == Items.AIR ? currentIcon : IElementHelper.get().item(new ItemStack(log));
    }

    /** Right after Jade's own name line (ObjectNameProvider, HEAD - 100), which it replaces. */
    @Override
    public int getDefaultPriority() {
        return TooltipPosition.HEAD;
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
