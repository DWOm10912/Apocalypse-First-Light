package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.earthwork.RockTypes;
import com.antaurora.apofirstlight.worldgen.underground.RockType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * An ore rock (U3b, docs/worldgen/underground_u3_rocks_ores_design.md 6, 14): the rock itself carrying an ore, one block
 * per ore body kind, its grade a block state (rich in a body's core, lean outside). To every system it is natural rock
 * (NaturalRockBlock: the pick cuts it, Terrain Smooth and the rock faces take it), with its own rock data ({@link #data}:
 * density, the pick's cut, the face's look, the stone it breaks into) in place of the host's table row.
 * <p>
 * Cut, it gives up its raw ore by volume ({@link #drops}): one piece a {@link #litres} of the grade, the rest the rock's
 * stones and pebbles as any rock's (a coal seam and an ore that is its own rock, banded iron, only pebbles). The raw ore is
 * what the dressing chain (design 6.5, B) turns into the concentrates.
 */
public class OreRockBlock extends NaturalRockBlock {
    public enum Grade implements StringRepresentable {
        LEAN("lean"), RICH("rich");

        private final String id;

        Grade(String id) { this.id = id; }

        @Override
        public String getSerializedName() { return id; }
    }

    public static final EnumProperty<Grade> GRADE = EnumProperty.create("grade", Grade.class);

    /** The rock data the pick, the faces and the stones read (RockTypes#of). */
    public final RockTypes.Rock data;
    /** Whether a cut gives the rock's stones (else only pebbles besides the ore). */
    public final boolean stones;
    /** litres of the rock a piece of raw ore comes out of, {lean, rich}. */
    public final double[] litres;
    private final String[] raw;
    private final double[] share;
    private volatile Item[] rawItems;

    /**
     * host: the rock it sits in (its RockType for the systems that read one); look: the face's look (RockTypes.LOOKS); raw:
     * the raw ore ids it gives, share: each one's part (sums to 1).
     */
    public OreRockBlock(RockType host, double density, double strike, String look, String stone, boolean stones, double leanLitres,
                        double richLitres, String[] raw, double[] share, Properties properties) {
        super(host, properties);
        this.data = new RockTypes.Rock(host, density, strike, look, host.anchor, ApocalypseFirstLight.MOD_ID + ":" + stone);
        this.stones = stones;
        this.litres = new double[]{leanLitres, richLitres};
        this.raw = raw;
        this.share = share;
        registerDefaultState(stateDefinition.any().setValue(GRADE, Grade.LEAN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GRADE);
    }

    /** The raw ore a piece of this state is (by share; the quartz vein by its metal). */
    public Item raw(BlockState state, RandomSource random) {
        Item[] items = rawItems;
        if (items == null) {
            items = new Item[raw.length];
            for (int i = 0; i < raw.length; i++) items[i] = ForgeRegistries.ITEMS.getValue(new ResourceLocation(ApocalypseFirstLight.MOD_ID, raw[i]));
            rawItems = items;
        }
        double r = random.nextDouble();
        for (int i = 0; i < items.length - 1; i++) if ((r -= share[i]) < 0) return items[i];
        return items[items.length - 1];
    }

    /** litres of this state a piece of raw ore comes out of. */
    public double litresPerPiece(BlockState state) { return litres[state.getValue(GRADE).ordinal()]; }

    /**
     * What litres cut out of state give, dropped at at (n on average: the whole part, one more by chance): the raw ore for an
     * ore rock and its stones (or only pebbles), the rock's stones and pebbles for any other rock (BoulderStrike's volumes).
     */
    public static void drops(ServerLevel level, net.minecraft.world.phys.Vec3 at, BlockState state, double litres, double stoneLitres, double pebbleLitres) {
        if (state.getBlock() instanceof OreRockBlock ore) {
            double per = ore.litresPerPiece(state);
            int n = (int) (litres / per) + (level.random.nextDouble() < litres / per - (int) (litres / per) ? 1 : 0);
            for (int i = 0; i < n; i++) com.antaurora.apofirstlight.boulder.BoulderStrike.drops(level, at, ore.raw(state, level.random), 1);
            if (ore.stones) com.antaurora.apofirstlight.boulder.BoulderStrike.drops(level, at, RockTypes.stone(ore.data), litres / stoneLitres);
        } else {
            com.antaurora.apofirstlight.boulder.BoulderStrike.drops(level, at, RockTypes.stone(state.getBlock()), litres / stoneLitres);
        }
        com.antaurora.apofirstlight.boulder.BoulderStrike.drops(level, at, com.antaurora.apofirstlight.registry.AflItems.PEBBLE.get(), litres / pebbleLitres);
    }
}
