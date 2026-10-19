package com.antaurora.apofirstlight.tree;

import com.antaurora.apofirstlight.stamina.PlayerStamina;
import com.antaurora.apofirstlight.stamina.StaminaConfig;
import com.antaurora.apofirstlight.tree.TreeForms.Species;
import com.antaurora.apofirstlight.terrainsmooth.TerrainSmooth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;

/**
 * Mesh Trees P2 (docs/rendering/mesh_trees_p2_felling.md, B/C/F/G/I/K/L; formal since P4): the server's half of
 * felling, the only authority. Any world: wherever the server's tree index holds a tree (the overworld).
 * <ol>
 * <li>Target: one of a standing tree's three lowest trunk cells (the cells P0-B lets a player pick: root, and the two
 * above it), found through the server's tree index; a branch cannot be chopped (it is not pickable), a cell shared
 * with another tree's trunk belongs to the tree whose trunk column it is.</li>
 * <li>Vanilla breaking stays out: the cells are unbreakable in survival; a creative left click with an axe on them is
 * cancelled here and on the client, so no cell is ever broken by hand.</li>
 * <li>Chop (the client's ChopMsg): a player, in reach, with an axe (Forge's AXE_DIG action: any axe, modded included),
 * at least MIN_CHOP_TICKS after their last chop. Work = the tool's power (FellingRules#power; winded x stamina's
 * winded.dig_speed), into the ledger; the root's cut stage and notch side follow it in its block state; wood sound,
 * chips at the notch, one durability, stamina's costs.chop (2026-10-12).</li>
 * <li>Last chop: FellingRules#direction picks the fall direction, FellingRules#restAngle the angle it comes to rest at
 * on the ground there; the falling tree entity is added first (if it cannot be, the tree stays and the work too); then
 * the tree's cells go: each keeps what another tree has there (FellingRules#keeper), else empties (a development
 * command's tree gets back what it replaced, TreeServerEvents#onFelled); a cell something else took is left alone. The root becomes the stump last (its
 * removal updates both indexes).</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
public final class FellingServer {
    /** The ledger's saved data; P0-P3 test worlds wrote it under the development name, read once if the new one is absent. */
    static final String NAME = "afl_mesh_tree_felling", LEGACY = "afl_dev_mesh_tree_felling";
    static final LongAdder CHOPS = new LongAdder(), IGNORED = new LongAdder(), FELLS = new LongAdder(), SETTLES = new LongAdder(),
            ITEMS = new LongAdder(), FELL_NANOS = new LongAdder(), CHOP_NANOS = new LongAdder(), HITS = new LongAdder();
    /** A falling tree's hit (2026-10-12): armour counts, a helmet takes a quarter off (damages_helmet), like a falling block. */
    public static final ResourceKey<DamageType> FALLING_TREE = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation("apocalypse_firstlight", "falling_tree"));
    private static final Map<UUID, Long> lastRefusal = new HashMap<>();

    private FellingServer() {}

    /** The ledger as saved data: work per tree, falls under way, settled falls (FellingLedger). */
    static final class Journal extends SavedData {
        final FellingLedger ledger = new FellingLedger();

        static Journal load(CompoundTag tag) {
            var j = new Journal();
            for (Tag t : tag.getList("work", Tag.TAG_COMPOUND)) j.ledger.work.put(((CompoundTag) t).getLong("root"), ((CompoundTag) t).getFloat("done"));
            for (Tag t : tag.getList("falling", Tag.TAG_COMPOUND)) j.ledger.falling.put(((CompoundTag) t).getUUID("id"), ((CompoundTag) t).getLong("root"));
            for (Tag t : tag.getList("settled", Tag.TAG_COMPOUND)) j.ledger.settled.put(((CompoundTag) t).getUUID("id"), ((CompoundTag) t).getLong("tick"));
            return j;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            var work = new ListTag();
            ledger.work.forEach((k, v) -> { var c = new CompoundTag(); c.putLong("root", k); c.putFloat("done", v); work.add(c); });
            var falling = new ListTag();
            ledger.falling.forEach((k, v) -> { var c = new CompoundTag(); c.putUUID("id", k); c.putLong("root", v); falling.add(c); });
            var settled = new ListTag();
            ledger.settled.forEach((k, v) -> { var c = new CompoundTag(); c.putUUID("id", k); c.putLong("tick", v); settled.add(c); });
            tag.put("work", work);
            tag.put("falling", falling);
            tag.put("settled", settled);
            return tag;
        }
    }

    static Journal journal(ServerLevel level) {
        var storage = level.getDataStorage();
        return storage.computeIfAbsent(Journal::load, () -> {
            var old = storage.get(Journal::load, LEGACY);
            if (old == null) return new Journal();
            old.setDirty();
            return old;
        }, NAME);
    }

    // ------------------------------------------------------------------------------------------------- target

    /** The standing tree whose trunk base (root and the two cells above) is at this position, from an index; or null. */
    static TreeIndex.Root choppable(BlockGetter level, BlockPos pos, TreeIndex index) {
        if (!TreeBlocks.ready()) return null;
        Block b = level.getBlockState(pos).getBlock();
        if (b != TreeBlocks.root() && b != TreeBlocks.wood()) return null;
        var found = new ArrayList<TreeIndex.Root>(2);
        index.roots(pos.getX(), pos.getY(), pos.getZ(), found);
        for (var r : found) if (r.x == pos.getX() && r.z == pos.getZ() && pos.getY() - r.y >= 0 && pos.getY() - r.y <= 2) return r;
        return null;
    }

    static boolean axe(ItemStack stack) {
        return !stack.isEmpty() && stack.canPerformAction(ToolActions.AXE_DIG);
    }

    /** The tool's work per chop from its tier's mining speed (or its speed on oak logs) and Efficiency. */
    static float power(ItemStack stack) {
        float speed = stack.getItem() instanceof TieredItem t ? t.getTier().getSpeed() : stack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState());
        return FellingRules.power(speed, stack.getEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY));
    }

    /** Server side of a left click: an axe on a choppable trunk never starts vanilla breaking (creative would break the cell). */
    @SubscribeEvent
    public static void leftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide() || !TreeBlocks.ready()) return;
        if (axe(event.getEntity().getMainHandItem()) && choppable(event.getLevel(), event.getPos(), TreeIndex.SERVER) != null) event.setCanceled(true);
    }

    /** A short refusal on the action bar (a lang key under message.apocalypse_firstlight.felling), at most every 2 s. */
    private static void refuse(ServerPlayer player, String key) {
        long now = player.level().getGameTime();
        Long last = lastRefusal.get(player.getUUID());
        if (last != null && now - last < 40 && now >= last) return;
        lastRefusal.put(player.getUUID(), now);
        player.displayClientMessage(Component.translatable("message.apocalypse_firstlight.felling." + key), true);
    }

    // --------------------------------------------------------------------------------------------------- chop

    /** A chop from a client (server thread). */
    static void chop(ServerPlayer player, BlockPos pos) {
        if (player == null || !TreeBlocks.ready() || !FellingRegistry.ready() || player.isSpectator()) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        long t0 = System.nanoTime();
        if (!level.isLoaded(pos) || !player.canReach(pos, 1.0)) return;
        var r = choppable(level, pos, TreeIndex.SERVER);
        if (r == null) return;
        ItemStack tool = player.getMainHandItem();
        if (!axe(tool)) { refuse(player, "need_axe"); return; }
        BlockPos rootPos = new BlockPos(r.x, r.y, r.z);
        BlockState rootState = level.getBlockState(rootPos);
        if (rootState.getBlock() != TreeBlocks.root()) return;
        Species species = r.layout.species;
        var form = TreeForms.grow(species, r.layout.variant);
        float cut = FellingRules.cutHeight(species, r.x, r.y, r.z);
        float need = FellingRules.work(species, FellingRules.trunkRadius(form, cut));
        var journal = journal(level);
        // the notch never goes back: a stage the block shows (its ledger entry lost) counts as that much work done
        int shown = rootState.getValue(TreeBlocks.CHOP);
        if (shown > 0 && !journal.ledger.work.containsKey(rootPos.asLong())) journal.ledger.work.put(rootPos.asLong(), shown * need / 5F);
        // winded: the swing lands weaker, like winded digging (stamina's winded.dig_speed)
        float power = power(tool) * (PlayerStamina.winded(player) ? (float) StaminaConfig.get().winded.digSpeed : 1F);
        var c = journal.ledger.chop(rootPos.asLong(), player.getUUID(), level.getGameTime(), power, need);
        if (c.result() == FellingLedger.Result.COOLDOWN) { IGNORED.increment(); return; }
        journal.setDirty();
        CHOPS.increment();
        PlayerStamina.spend(player, StaminaConfig.get().costs.chop, false);
        com.antaurora.apofirstlight.survivor.SurvivorGrowth.chop(player);
        double cx = r.x + 0.5, cy = r.y + cut, cz = r.z + 0.5;
        // the notch faces the first chopper; its stage follows the work
        int side = c.stageBefore() == 0 && rootState.getValue(TreeBlocks.CHOP) == 0
                ? FellingRules.side(player.getX() - cx, player.getZ() - cz) : rootState.getValue(TreeBlocks.CUT);
        tool.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        player.causeFoodExhaustion(0.02F);
        player.swing(InteractionHand.MAIN_HAND, false);
        double sa = FellingRules.sideAngle(side), rad = FellingRules.trunkRadius(form, cut);
        double px = cx + Math.cos(sa) * rad, pz = cz + Math.sin(sa) * rad;
        com.antaurora.apofirstlight.noise.RangedSound.playWithNoise(level, player, new net.minecraft.world.phys.Vec3(px, cy, pz), com.antaurora.apofirstlight.registry.AflSounds.TREE_CHOP.get(), CHOP_RADIUS,
                0.96F + 0.08F * level.random.nextFloat(), com.antaurora.apofirstlight.noise.NoiseType.BLOCK_BREAK, null);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, chips(species)), px, cy, pz, 8, 0.12, 0.08, 0.12, 0.12);
        if (c.result() == FellingLedger.Result.FELL) {
            CHOP_NANOS.add(System.nanoTime() - t0);
            fell(level, player, rootPos, rootState.setValue(TreeBlocks.CHOP, FellingRules.STAGES).setValue(TreeBlocks.CUT, side), form, cut);
            return;
        }
        int stage = c.stageAfter();
        if (stage != rootState.getValue(TreeBlocks.CHOP) || side != rootState.getValue(TreeBlocks.CUT))
            level.setBlock(rootPos, rootState.setValue(TreeBlocks.CHOP, stage).setValue(TreeBlocks.CUT, side), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        CHOP_NANOS.add(System.nanoTime() - t0);
    }

    /** Chip particles: the vanilla stripped log nearest the species' fresh wood. */
    static BlockState chips(Species s) {
        return switch (s) {
            case WHITE_PINE, SHAGBARK_HICKORY, RED_MAPLE -> Blocks.STRIPPED_BIRCH_LOG.defaultBlockState();
            case EASTERN_HEMLOCK -> Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
            default -> Blocks.STRIPPED_OAK_LOG.defaultBlockState();
        };
    }

    // --------------------------------------------------------------------------------------------------- fell

    /** Natural ground (rests a tree, is no obstacle). */
    static boolean natural(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY)
                || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.SNOW) || s.is(BlockTags.ICE) || s.is(BlockTags.TERRACOTTA) || !s.getFluidState().isEmpty();
    }

    /** The top a falling tree would rest on in a column: {y, natural 1/0}; trees, leaves and anything without collision let it through. */
    static double[] support(ServerLevel level, int x, int z, int yTop, Map<Long, double[]> cache) {
        long key = (long) x << 32 ^ (z & 0xFFFFFFFFL);
        double[] done = cache.get(key);
        if (done != null) return done;
        var pos = new BlockPos.MutableBlockPos();
        int y = Math.min(yTop, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
        double[] out = {yTop - 48, 1};
        for (; y > yTop - 48; y--) {
            pos.set(x, y, z);
            BlockState s = level.getBlockState(pos);
            if (s.isAir() || TreeBlocks.isTree(s) || s.is(BlockTags.LEAVES)) continue;
            if (!s.getFluidState().isEmpty()) { out = new double[]{y + 0.9, 1}; break; }
            var shape = s.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            double top = TerrainSmooth.standingTop(s.getBlock(), level, pos, 0.5, 0.5);
            out = new double[]{y + (Double.isNaN(top) ? shape.max(net.minecraft.core.Direction.Axis.Y) : top), natural(s) ? 1 : 0};
            break;
        }
        cache.put(key, out);
        return out;
    }

    /** The last chop: direction, rest angle, the falling tree, then the tree's cells and the stump. */
    static void fell(ServerLevel level, ServerPlayer player, BlockPos root, BlockState rootState, TreeForms.Form form, float cut) {
        long t0 = System.nanoTime();
        int cx = root.getX() >> 4, cz = root.getZ() >> 4;
        // the tree reaches one chunk out, the crowns it shares cells with two
        for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++)
            if (!level.hasChunk(cx + dx, cz + dz)) { refuse(player, "not_loaded"); return; }
        Species species = TreeBlocks.species(rootState);
        int variant = rootState.getValue(TreeBlocks.VARIANT), turns = TreeBlocks.turns(rootState);
        var layout = TreeShape.layout(species, variant, turns);
        double px = root.getX() + 0.5, py = root.getY() + cut, pz = root.getZ() + 0.5;
        float[] profile = FellingRules.profile(form, cut, species);
        float length = form.crownTop - cut;
        var cache = new HashMap<Long, double[]>();
        int yTop = root.getY() + (int) Math.ceil(layout.height) + 4;
        // other trunks near, for the obstacle score
        var near = new ArrayList<TreeIndex.Root>();
        for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++)
            for (var o : TreeIndex.SERVER.chunk(cx + dx, cz + dz)) if (o.x != root.getX() || o.y != root.getY() || o.z != root.getZ()) near.add(o);
        var restBy = new HashMap<Double, Float>();
        FellingRules.Scorer scorer = a -> {
            double ux = Math.cos(a), uz = Math.sin(a);
            float rest = FellingRules.restAngle(profile, d -> support(level, (int) Math.floor(px + ux * d), (int) Math.floor(pz + uz * d), yTop, cache)[0] - py);
            restBy.put(a, rest);
            double cost = rest < 60 ? (60 - rest) / 10 : 0;
            for (var o : near) {
                double ox = o.x + 0.5 - px, oz = o.z + 0.5 - pz, along = ox * ux + oz * uz, across = Math.abs(-ox * uz + oz * ux);
                if (along > 1 && along < length && across < 1.5) cost += 2;
            }
            for (double d = 2; d < length; d += 2)
                if (support(level, (int) Math.floor(px + ux * d), (int) Math.floor(pz + uz * d), yTop, cache)[1] == 0) cost += 0.5;
            return cost;
        };
        var look = player.getLookAngle();
        double preferred = Math.atan2(look.z, look.x), toward = Math.atan2(player.getZ() - pz, player.getX() - px);
        // the crown's lean (its centre off the trunk), turned with the layout like TreeShape.Quad#rotated
        double lx = form.crownCx - 0.5, lz = form.crownCz - 0.5;
        for (int k = 0; k < turns; k++) { double t = lx; lx = -lz; lz = t; }
        double lean = Math.atan2(lz, lx), leanWeight = Math.hypot(lx, lz) / Math.max(1, form.crownRadius) * 2;
        double jitter = (FellingRules.unit(FellingRules.hash(root.getX(), root.getY(), root.getZ(), 2)) - 0.5) * 0.25;
        double dir = FellingRules.direction(preferred, toward, lean, leanWeight, jitter, scorer);
        Float rest = restBy.get(dir);
        if (rest == null) { scorer.cost(dir); rest = restBy.get(dir); }
        // the falling tree first: if it cannot be added, nothing is removed
        var fall = FallingTree.create(level, root, species, variant, turns, (float) dir, rest, cut, level.getGameTime() + FellingRules.PAUSE_TICKS);
        if (!level.addFreshEntity(fall)) { refuse(player, "no_fall"); return; }
        var journal = journal(level);
        journal.ledger.fell(root.asLong(), fall.getUUID());
        journal.setDirty();
        clear(level, root, rootState, layout);
        com.antaurora.apofirstlight.noise.RangedSound.playWithNoise(level, player, new net.minecraft.world.phys.Vec3(px, py, pz), com.antaurora.apofirstlight.registry.AflSounds.TREE_CRACK.get(), CRACK_RADIUS, 1.0F, com.antaurora.apofirstlight.noise.NoiseType.BLOCK_BREAK, null);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, chips(species)), px, py, pz, 24, 0.3, 0.1, 0.3, 0.15);
        FELLS.increment();
        FELL_NANOS.add(System.nanoTime() - t0);
    }

    /** Removes a felled tree's cells (shared cells keep the others' best) and leaves the stump at the root. */
    static void clear(ServerLevel level, BlockPos root, BlockState rootState, TreeShape.Layout layout) {
        // a development command's tree: its cells' former states come back where nothing else is left
        Map<Long, BlockState> replaced = new HashMap<>();
        for (var hook : TreeServerEvents.FELLED) replaced.putAll(hook.felled(level, root));
        var others = new ArrayList<TreeIndex.Root>();
        var pos = new BlockPos.MutableBlockPos();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (var c : layout.cells.values()) {
            if (c.x == 0 && c.y == 0 && c.z == 0) continue;
            pos.set(root.getX() + c.x, root.getY() + c.y, root.getZ() + c.z);
            BlockState now = level.getBlockState(pos);
            if (!TreeBlocks.isTree(now) || now.getBlock() == TreeBlocks.root()) continue;
            others.clear();
            TreeIndex.SERVER.roots(pos.getX(), pos.getY(), pos.getZ(), others);
            int[] rank = new int[others.size()], sp = new int[others.size()];
            int n = 0;
            for (var o : others) {
                if (o.x == root.getX() && o.y == root.getY() && o.z == root.getZ()) continue;
                var oc = o.cellAt(pos.getX(), pos.getY(), pos.getZ());
                if (oc == null) continue;
                rank[n] = ForestPlan.rank(oc);
                sp[n] = o.layout.species.ordinal();
                n++;
            }
            int k = FellingRules.keeper(rank, sp, n);
            BlockState want;
            if (k < 0) {
                BlockState was = replaced.get(pos.asLong());
                want = was != null && !TreeBlocks.isTree(was) ? was : Blocks.AIR.defaultBlockState();
            } else if (rank[k] >= 3) want = TreeBlocks.wood().defaultBlockState();
            else want = TreeBlocks.crown(Species.values()[sp[k]], rank[k] == 2).defaultBlockState();
            if (!want.equals(now)) level.setBlock(pos, want, flags);
        }
        level.setBlock(root, TreeBlocks.stumpState(rootState), flags);
    }

    // ------------------------------------------------------------------------------------------------- hits

    /**
     * One tick of a fall, a0 to a1 degrees from upright at omega rad/s (FellingRules#hit): each living body in the tree's
     * way is hurt (FALLING_TREE, no attacker) once per kind of hit, and pushed along the fall.
     */
    static void sweep(ServerLevel level, FallingTree e, float a0, float a1, double omega) {
        if (!(a1 > a0)) return;
        var form = e.form();
        double length = form.crownTop - e.cut, reach = form.crownRadius + 1;
        double ux = Math.cos(e.direction), uz = Math.sin(e.direction), px = e.getX(), py = e.getY(), pz = e.getZ();
        // the swept wedge's box: the pivot and the top at both angles (level between them), widened by the crown
        double far = length * (a0 <= 90 && a1 >= 90 ? 1 : Math.max(Math.sin(Math.toRadians(a0)), Math.sin(Math.toRadians(a1))));
        double top = length * Math.cos(Math.toRadians(a0));
        var box = new AABB(px, py, pz, px + ux * far, py + top, pz + uz * far).inflate(reach).expandTowards(0, -3, 0);
        Holder<DamageType> type = null;
        for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, box, b -> b.isAlive() && !b.isSpectator())) {
            AABB bb = body.getBoundingBox();
            double ex = (bb.minX + bb.maxX) / 2 - px, ez = (bb.minZ + bb.maxZ) / 2 - pz;
            var hit = FellingRules.hit(form, e.species, e.cut, a0, a1, omega, ex * ux + ez * uz, Math.abs(ez * ux - ex * uz),
                    (bb.getXsize() + bb.getZsize()) / 4, bb.minY - py, bb.maxY - py);
            if (hit == null) continue;
            int had = e.struck.getOrDefault(body.getUUID(), 0);
            if ((had & 1 << hit.kind()) != 0) continue;
            e.struck.put(body.getUUID(), had | 1 << hit.kind());
            if (type == null) type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(FALLING_TREE);
            if (body.hurt(new DamageSource(type), hit.damage())) {
                double k = Math.min(0.6, 0.04 * hit.speed()) * (hit.kind() == FellingRules.TWIGS ? 0.5 : 1);
                body.push(ux * k, 0.1, uz * k);
                body.hurtMarked = true;
            }
            HITS.increment();
        }
    }

    // ------------------------------------------------------------------------------------------- landing, drops

    /** A point on the fallen tree's axis at a distance from the pivot. */
    static double[] along(FallingTree e, double s) {
        double a = Math.toRadians(e.rest), dx = Math.cos(e.direction), dz = Math.sin(e.direction);
        return new double[]{e.getX() + dx * s * Math.sin(a), e.getY() + s * Math.cos(a), e.getZ() + dz * s * Math.sin(a)};
    }

    /**
     * Heard as far as the infected hear it (blocks; forest home sounds V1, 2026-10-14): an axe chop, the trunk cracking as
     * the tree goes (fell), the crash of trunk and crown on the ground (impact). One take: the crack is its first 2.75 s.
     */
    public static final double CHOP_RADIUS = 10, CRACK_RADIUS = 12, CRASH_RADIUS = 24;

    static void impact(ServerLevel level, FallingTree e) {
        var form = TreeForms.grow(e.species, e.variant);
        double length = form.crownTop - e.cut;
        double[] mid = along(e, length * 0.6);
        com.antaurora.apofirstlight.noise.RangedSound.playWithNoise(level, null, new net.minecraft.world.phys.Vec3(mid[0], mid[1], mid[2]), com.antaurora.apofirstlight.registry.AflSounds.TREE_CRASH.get(), CRASH_RADIUS,
                0.97F + 0.06F * level.random.nextFloat(), com.antaurora.apofirstlight.noise.NoiseType.BLOCK_BREAK, null);
        var leaves = new BlockParticleOption(ParticleTypes.BLOCK, TreeBlocks.crown(e.species, true).defaultBlockState());
        var wood = new BlockParticleOption(ParticleTypes.BLOCK, chips(e.species));
        for (int i = 1; i <= 6; i++) {
            double s = length * i / 6.0;
            double[] p = along(e, s);
            boolean crown = s + e.cut > form.crownBottom;
            level.sendParticles(crown ? leaves : wood, p[0], p[1], p[2], crown ? 10 : 4, 0.8, 0.4, 0.8, 0.05);
        }
    }

    /** The drops, once per fall (the caller settled it): species logs along the trunk, branches and leaves in the crown. */
    static void drops(ServerLevel level, FallingTree e) {
        var layout = e.layout();
        var form = TreeForms.grow(e.species, e.variant);
        var d = FellingRules.drops(e.species, layout.trunkVolume, layout.limbVolume, layout.twigVolume, layout.crownRadius);
        double trunk = Math.max(1.5, form.trunkTop - e.cut), crown0 = Math.max(1, form.crownBottom - e.cut), crown1 = Math.max(crown0 + 1, form.crownTop - e.cut);
        spawn(level, e, FellingRegistry.log(e.species), d.logs(), new double[]{0.25 * trunk + 0.8, 0.6 * trunk + 0.8, crown0 + 0.2 * (crown1 - crown0)});
        spawn(level, e, FellingRegistry.branch(e.species), d.branches(), new double[]{crown0 + 0.4 * (crown1 - crown0), crown0 + 0.75 * (crown1 - crown0)});
        spawn(level, e, FellingRegistry.leaves(e.species), d.leaves(), new double[]{crown0 + 0.55 * (crown1 - crown0)});
        var leaves = new BlockParticleOption(ParticleTypes.BLOCK, TreeBlocks.crown(e.species, true).defaultBlockState());
        for (double s = crown0; s < crown1; s += 1.5) {
            double[] p = along(e, s);
            level.sendParticles(leaves, p[0], p[1], p[2], 6, 1.0, 0.6, 1.0, 0.02);
        }
        SETTLES.increment();
    }

    /** count items of one kind as stacks over a few points along the fallen tree (split evenly, at most a stack each). */
    private static void spawn(ServerLevel level, FallingTree e, net.minecraft.world.item.Item item, int count, double[] at) {
        for (int i = 0; i < at.length && count > 0; i++) {
            int n = Math.min(64, (count + (at.length - i) - 1) / (at.length - i));
            count -= n;
            double[] p = along(e, at[i]);
            var drop = new ItemEntity(level, p[0], p[1] + 0.3, p[2], new ItemStack(item, n));
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
            ITEMS.increment();
        }
    }
}
