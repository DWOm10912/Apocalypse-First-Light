package com.antaurora.apofirstlight.dev.meshtree;

import com.antaurora.apofirstlight.dev.meshtree.TreeForms.Species;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code /afl dev mesh_tree} (docs/rendering/mesh_trees_p0b.md, H; forest: mesh_trees_p1_forest.md, H): places and removes sample trees. Development runs
 * only, OP2 and a player; placing also needs creative mode, the overworld and a world folder named {@code AFL_TREE_*}
 * (the command says which one is missing).
 * <pre>
 *   place [white_oak|white_pine] [variant 0-15] [turns 0-3]   one tree, its root on the block you stand in
 *   grove [count 2-16] [white_oak|white_pine|mixed]            a grid of trees around you on the surface (default 9, mixed)
 *   clear                                                      removes every sample tree, restores what each cell replaced
 *   lod auto | 0 | 1 | 2 | proxy                               this client: distance levels, one level, the voxel proxy
 *   status                                                     trees here; on your client: index, levels, cost, shader IDs
 *   forest status | here | find open_oak|white_pine|mixed       the natural forest (P1, read only; ForestCommands)
 *   felling here | status                                       the felling (P2, read only; FellingCommands)
 * </pre>
 * A cell is written only into air or a replaceable plant (the journal keeps what was there); a trunk or branch cell that
 * finds anything else refuses the tree; a crown cell that finds another sample tree's crown shares it, anything else is
 * skipped and counted. At most {@value #MAX_TREES} trees. Writes use flags 18 (no neighbour updates, no drops).
 * <p>
 * Also keeps the server's tree index ({@link TreeIndex#SERVER}): roots of the overworld's loaded chunks, for collision.
 */
public final class TreeCommands {
    static final String PREFIX = "AFL_TREE_";
    static final int MAX_TREES = 32, SPACING = 13;
    static final int ROOT = 0, STATUS = 1, LOD = 2;

    public record Msg(int action, long pos, int value) {
        static void encode(Msg m, FriendlyByteBuf b) {
            b.writeByte(m.action);
            b.writeLong(m.pos);
            b.writeInt(m.value);
        }

        static Msg decode(FriendlyByteBuf b) {
            return new Msg(b.readByte(), b.readLong(), b.readInt());
        }

        static void handle(Msg m, Supplier<NetworkEvent.Context> supplier) {
            var c = supplier.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TreeClient.handle(m)));
            c.setPacketHandled(true);
        }
    }

    /** P2: a chop at a trunk cell, client to server (FellingClient sends, FellingServer#chop decides). */
    public record Chop(long pos) {
        static void encode(Chop m, FriendlyByteBuf b) { b.writeLong(m.pos); }

        static Chop decode(FriendlyByteBuf b) { return new Chop(b.readLong()); }

        static void handle(Chop m, Supplier<NetworkEvent.Context> supplier) {
            var c = supplier.get();
            ServerPlayer sender = c.getSender();
            c.enqueueWork(() -> FellingServer.chop(sender, BlockPos.of(m.pos)));
            c.setPacketHandled(true);
        }
    }

    static void sendChop(BlockPos pos) {
        if (channel != null) channel.sendToServer(new Chop(pos.asLong()));
    }

    private static SimpleChannel channel;

    private TreeCommands() {}

    @Mod.EventBusSubscriber(modid = "apocalypse_firstlight", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void setup(FMLCommonSetupEvent event) {
            if (FMLEnvironment.production) return;
            channel = NetworkRegistry.newSimpleChannel(new ResourceLocation("apocalypse_firstlight", "dev_mesh_tree"), () -> "3", "3"::equals, "3"::equals);
            channel.registerMessage(0, Msg.class, Msg::encode, Msg::decode, Msg::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            channel.registerMessage(1, Chop.class, Chop::encode, Chop::decode, Chop::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        }
    }

    @Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
    public static final class Register {
        @SubscribeEvent
        public static void register(RegisterCommandsEvent event) {
            if (FMLEnvironment.production) return;
            LiteralArgumentBuilder<CommandSourceStack> c = Commands.literal("mesh_tree")
                    .requires(s -> !FMLEnvironment.production && TreeBlocks.ready() && s.hasPermission(2) && s.getEntity() instanceof ServerPlayer);
            var place = Commands.literal("place").executes(x -> place(x, Species.WHITE_OAK, 0, 0));
            for (Species sp : Species.values()) {
                place.then(Commands.literal(sp.id).executes(x -> place(x, sp, 0, 0))
                        .then(Commands.argument("variant", IntegerArgumentType.integer(0, TreeShape.VARIANTS - 1))
                                .executes(x -> place(x, sp, IntegerArgumentType.getInteger(x, "variant"), 0))
                                .then(Commands.argument("turns", IntegerArgumentType.integer(0, 3))
                                        .executes(x -> place(x, sp, IntegerArgumentType.getInteger(x, "variant"), IntegerArgumentType.getInteger(x, "turns"))))));
            }
            c.then(place);
            ArgumentBuilder<CommandSourceStack, ?> count = Commands.argument("count", IntegerArgumentType.integer(2, 16))
                    .executes(x -> grove(x, IntegerArgumentType.getInteger(x, "count"), null));
            for (Species sp : Species.values())
                count.then(Commands.literal(sp.id).executes(x -> grove(x, IntegerArgumentType.getInteger(x, "count"), sp)));
            count.then(Commands.literal("mixed").executes(x -> grove(x, IntegerArgumentType.getInteger(x, "count"), null)));
            c.then(Commands.literal("grove").executes(x -> grove(x, 9, null)).then(count));
            c.then(Commands.literal("clear").executes(TreeCommands::clear));
            c.then(Commands.literal("status").executes(TreeCommands::status));
            var lod = Commands.literal("lod");
            lod.then(Commands.literal("auto").executes(x -> toPlayer(x, new Msg(LOD, 0, -1))));
            for (int l = 0; l < TreeShape.LODS; l++) { int level = l; lod.then(Commands.literal(String.valueOf(l)).executes(x -> toPlayer(x, new Msg(LOD, 0, level)))); }
            lod.then(Commands.literal("proxy").executes(x -> toPlayer(x, new Msg(LOD, 0, TreeShape.LODS))));
            c.then(lod);
            c.then(ForestCommands.node());
            c.then(FellingCommands.node());
            event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(c)));
        }
    }

    /** Why this player may not place or clear sample trees here, or null. */
    private static String refusal(CommandSourceStack s) {
        if (!(s.getEntity() instanceof ServerPlayer p)) return "needs a player";
        if (!p.isCreative()) return "needs creative mode";
        if (!s.getLevel().dimension().equals(Level.OVERWORLD)) return "needs the overworld";
        String name = worldName(s.getLevel());
        if (!name.startsWith(PREFIX)) return "needs a test world whose folder name starts with " + PREFIX + " (this one is \"" + name + "\")";
        return null;
    }

    private static boolean refused(CommandContext<CommandSourceStack> x) {
        String why = refusal(x.getSource());
        if (why != null) x.getSource().sendFailure(Component.literal("Mesh tree: " + why));
        return why != null;
    }

    static String worldName(ServerLevel level) {
        var p = level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
        return p == null ? "" : p.toString();
    }

    // ------------------------------------------------------------------------------------- the server's index

    @Mod.EventBusSubscriber(modid = "apocalypse_firstlight")
    public static final class ServerIndex {
        @SubscribeEvent
        public static void chunkLoaded(ChunkEvent.Load event) {
            if (FMLEnvironment.production || !TreeBlocks.ready() || !(event.getLevel() instanceof ServerLevel level)
                    || !level.dimension().equals(Level.OVERWORLD) || !(event.getChunk() instanceof LevelChunk chunk)) return;
            TreeIndex.SERVER.load(chunk);
        }

        @SubscribeEvent
        public static void chunkUnloaded(ChunkEvent.Unload event) {
            if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(Level.OVERWORLD)) TreeIndex.SERVER.unload(event.getChunk().getPos());
        }

        @SubscribeEvent
        public static void stopped(ServerStoppedEvent event) {
            TreeIndex.SERVER.clear();
            ForestGate.stopped();
        }
    }

    /** A root appeared or went (server): the server's index follows, and the clients tracking its chunk are told. */
    static void rootChanged(ServerLevel level, BlockPos pos, BlockState now) {
        if (level.dimension().equals(Level.OVERWORLD)) {
            if (TreeIndex.isRoot(now)) TreeIndex.SERVER.add(pos, now); else TreeIndex.SERVER.remove(pos);
        }
        if (channel == null) return;
        channel.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)), new Msg(ROOT, pos.asLong(), Block.getId(now)));
    }

    // ------------------------------------------------------------------------------------------------- journal

    /** The placed sample trees and, per tree, every cell it wrote with the state it replaced. */
    static final class Journal extends SavedData {
        static final String NAME = "afl_dev_mesh_trees";
        record Tree(long root, String species, int variant, int turns, long[] cells, BlockState[] replaced) {}
        final List<Tree> trees = new ArrayList<>();

        static Journal get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(Journal::load, Journal::new, NAME);
        }

        static Journal load(CompoundTag tag) {
            var j = new Journal();
            for (Tag t : tag.getList("trees", Tag.TAG_COMPOUND)) {
                var c = (CompoundTag) t;
                long[] cells = c.getLongArray("cells");
                var states = c.getList("replaced", Tag.TAG_COMPOUND);
                BlockState[] replaced = new BlockState[cells.length];
                for (int i = 0; i < cells.length; i++)
                    replaced[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), states.getCompound(i));
                j.trees.add(new Tree(c.getLong("root"), c.contains("species") ? c.getString("species") : "white_oak", c.getInt("variant"), c.getInt("turns"), cells, replaced));
            }
            return j;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            var list = new ListTag();
            for (var t : trees) {
                var c = new CompoundTag();
                c.putLong("root", t.root);
                c.putString("species", t.species);
                c.putInt("variant", t.variant);
                c.putInt("turns", t.turns);
                c.put("cells", new LongArrayTag(t.cells));
                var states = new ListTag();
                for (var s : t.replaced) states.add(NbtUtils.writeBlockState(s));
                c.put("replaced", states);
                list.add(c);
            }
            tag.put("trees", list);
            return tag;
        }
    }

    // --------------------------------------------------------------------------------------------- placement

    private static boolean replaceable(BlockState s) {
        return s.isAir() || s.canBeReplaced() || s.getBlock() instanceof BushBlock;
    }

    /** Places one tree; null when done, otherwise why not. skipped: [crown cells skipped, crown cells shared]. */
    static String place(ServerLevel level, BlockPos root, Species species, int variant, int turns, int[] skipped) {
        var journal = Journal.get(level);
        if (journal.trees.size() >= MAX_TREES) return "TOO_MANY_TREES (" + MAX_TREES + "; /afl dev mesh_tree clear)";
        BlockPos below = root.below();
        if (!level.isLoaded(below) || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return "NO_SOLID_GROUND under " + root.toShortString();
        BlockState rootState = TreeBlocks.rootState(species, variant, turns);
        var layout = TreeShape.layout(species, variant, turns);
        var write = new ArrayList<BlockPos>();
        var states = new ArrayList<BlockState>();
        int shared = 0, blocked = 0;
        for (var cell : TreeShape.ordered(layout)) {
            BlockPos p = root.offset(cell.x, cell.y, cell.z);
            if (!level.isInWorldBounds(p) || !level.isLoaded(p)) return "UNLOADED_OR_OUTSIDE at " + p.toShortString();
            BlockState was = level.getBlockState(p);
            boolean woody = cell.kind == TreeShape.WOOD;
            BlockState want = TreeBlocks.stateFor(cell, rootState);
            if (replaceable(was)) { write.add(p); states.add(want); continue; }
            if (!woody && was.getBlock() instanceof TreeBlocks.Foliage other) {
                shared++;
                // a carrier wins over a volume-only crown cell of another tree (that tree draws nothing there)
                if (cell.carries() && !other.carrier) { write.add(p); states.add(want); }
                continue;
            }
            if (woody) return "BLOCKED trunk or branch cell at " + p.toShortString() + " (" + BuiltInRegistries.BLOCK.getKey(was.getBlock()) + ")";
            blocked++;
        }
        long[] at = new long[write.size()];
        BlockState[] replaced = new BlockState[write.size()];
        // cells first, the root last (its onPlace indexes the tree on the server and tells the clients)
        int rootIndex = -1;
        for (int i = 0; i < write.size(); i++) {
            at[i] = write.get(i).asLong();
            replaced[i] = level.getBlockState(write.get(i));
            if (write.get(i).equals(root)) { rootIndex = i; continue; }
            level.setBlock(write.get(i), states.get(i), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        if (rootIndex >= 0) level.setBlock(root, states.get(rootIndex), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        // a crown cell taken over from another sample tree goes back to air (the other tree's journal has what was there)
        for (int i = 0; i < replaced.length; i++) if (TreeBlocks.isTree(replaced[i])) replaced[i] = Blocks.AIR.defaultBlockState();
        journal.trees.add(new Journal.Tree(root.asLong(), species.id, variant, turns, at, replaced));
        journal.setDirty();
        skipped[0] += blocked;
        skipped[1] += shared;
        return null;
    }

    private static int place(CommandContext<CommandSourceStack> x, Species species, int variant, int turns) {
        if (refused(x)) return 0;
        var player = (ServerPlayer) x.getSource().getEntity();
        var level = x.getSource().getLevel();
        int[] skipped = new int[2];
        String problem = place(level, player.blockPosition(), species, variant, turns, skipped);
        if (problem != null) { x.getSource().sendFailure(Component.literal("Mesh tree: " + problem)); return 0; }
        var l = TreeShape.layout(species, variant, turns);
        x.getSource().sendSuccess(() -> Component.literal(String.format("Mesh tree: %s variant %d, turns %d at %s; %.1f m tall, crown %.1f m; %d cells (wood %d, %d of them collide; crown %d, %d carry leaves); crown cells skipped %d, shared %d",
                species.id, variant, turns, player.blockPosition().toShortString(), l.height, 2 * l.crownRadius, l.cells.size(), l.count(TreeShape.WOOD),
                l.solidCells, l.count(TreeShape.FOLIAGE), l.carriers(TreeShape.FOLIAGE), skipped[0], skipped[1])), false);
        return 1;
    }

    private static int grove(CommandContext<CommandSourceStack> x, int count, Species only) {
        if (refused(x)) return 0;
        var player = (ServerPlayer) x.getSource().getEntity();
        var level = x.getSource().getLevel();
        int side = (int) Math.ceil(Math.sqrt(count)), placed = 0;
        int[] skipped = new int[2];
        var problems = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            int gx = i % side - side / 2, gz = i / side - side / 2;
            int px = player.getBlockX() + gx * SPACING + (i * 7 % 5 - 2), pz = player.getBlockZ() + gz * SPACING + (i * 3 % 5 - 2);
            if (!level.isLoaded(new BlockPos(px, level.getMinBuildHeight(), pz))) { problems.add("unloaded " + px + " " + pz); continue; }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
            // a mix from the position: species, variant and facing differ from tree to tree
            int h = (px * 73856093) ^ (pz * 19349663);
            Species sp = only != null ? only : Species.values()[Math.floorMod(h, Species.values().length)];
            String problem = place(level, new BlockPos(px, y, pz), sp, Math.floorMod(h >> 3, TreeShape.VARIANTS), Math.floorMod(h >> 9, 4), skipped);
            if (problem == null) placed++; else problems.add(problem);
        }
        int done = placed;
        x.getSource().sendSuccess(() -> Component.literal("Mesh tree grove: " + done + " of " + count + " placed; crown cells skipped "
                + skipped[0] + ", shared " + skipped[1] + (problems.isEmpty() ? "" : "; " + String.join("; ", problems.subList(0, Math.min(4, problems.size()))))), false);
        return done;
    }

    private static int clear(CommandContext<CommandSourceStack> x) {
        if (refused(x)) return 0;
        var level = x.getSource().getLevel();
        var journal = Journal.get(level);
        int restored = 0, kept = 0, trees = journal.trees.size();
        // oldest first: a crown cell a later tree took over goes back to what the first tree found there
        for (var tree : journal.trees) {
            BlockPos rootPos = BlockPos.of(tree.root());
            // the root first: its onRemove updates the indexes before the cells go
            for (int i = 0; i < tree.cells().length; i++) {
                if (tree.cells()[i] != tree.root()) continue;
                if (TreeBlocks.isTree(level.getBlockState(rootPos))) { level.setBlock(rootPos, tree.replaced()[i], Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE); restored++; }
            }
            for (int i = 0; i < tree.cells().length; i++) {
                if (tree.cells()[i] == tree.root()) continue;
                BlockPos p = BlockPos.of(tree.cells()[i]);
                if (!level.isLoaded(p) || !TreeBlocks.isTree(level.getBlockState(p))) { kept++; continue; }
                level.setBlock(p, tree.replaced()[i], Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                restored++;
            }
        }
        journal.trees.clear();
        journal.setDirty();
        int r = restored, k = kept;
        x.getSource().sendSuccess(() -> Component.literal("Mesh tree: cleared " + trees + " trees, " + r + " cells restored, " + k
                + " left alone (changed since, or unloaded)"), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> x) {
        var journal = Journal.get(x.getSource().getLevel());
        int cells = 0;
        for (var t : journal.trees) cells += t.cells().length;
        int c = cells;
        x.getSource().sendSuccess(() -> Component.literal("Mesh tree (server): " + journal.trees.size() + " sample trees, " + c
                + " cells written; server index " + TreeIndex.SERVER.size() + " roots in " + TreeIndex.SERVER.chunks() + " chunks"), false);
        return toPlayer(x, new Msg(STATUS, 0, 0));
    }

    private static int toPlayer(CommandContext<CommandSourceStack> x, Msg msg) {
        if (channel == null || !(x.getSource().getEntity() instanceof ServerPlayer player)) {
            x.getSource().sendFailure(Component.literal("Mesh tree: needs a player in a development run"));
            return 0;
        }
        channel.send(PacketDistributor.PLAYER.with(() -> player), msg);
        return 1;
    }
}
