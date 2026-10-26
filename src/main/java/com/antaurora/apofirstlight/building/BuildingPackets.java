package com.antaurora.apofirstlight.building;

import com.antaurora.apofirstlight.cabin.FurnitureWork;
import com.antaurora.apofirstlight.cabin.WorkbenchCrafting;
import com.antaurora.apofirstlight.cabin.WorkbenchKnowledge;
import com.antaurora.apofirstlight.client.workbench.WorkbenchRecipes;
import com.antaurora.apofirstlight.client.workbench.WorkbenchWork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Building hammer B0 (docs/gameplay/building_hammer_b0.md), client to server: install a catalog piece, take one down, and
 * finish one item at the workbench. The server checks everything again (BuildingRules, WorkbenchCrafting). Work at the
 * bench (docs/gameplay/afl_workbench_v1_design.md 17): the client starts and stops it, the server tells the players around.
 * The recipes a player knows (18) go server to client whole, each with how it is known.
 */
public final class BuildingPackets {
    /** Install the piece at the placement (B0: block position and quarter-turn index). */
    public record Place(ResourceLocation piece, BlockPos pos, int orientation) {}

    /** Take down the piece at pos. */
    public record Remove(BlockPos pos) {}

    /** One item of the recipe, finished on the page of the bench at {@code bench}. */
    public record Craft(BlockPos bench, ResourceLocation recipe) {}

    /**
     * Start or stop working at the bench on a work recipe (WorkbenchCrafting#work); {@code discard}: the player said yes to
     * throwing away the half-built piece of another recipe on the bench (furniture, FurnitureWork).
     */
    public record Work(BlockPos bench, ResourceLocation recipe, boolean start, boolean discard) {
        public Work(BlockPos bench, ResourceLocation recipe, boolean start) {
            this(bench, recipe, start, false);
        }
    }

    /**
     * A furniture piece's word (FurnitureWork, WorkbenchCrafting#act): a timed step played to its end ({@code press} false),
     * or a press on the nail's or screw's bar ({@code good}: in the zone, the client's word as the generator's pull).
     */
    public record Act(BlockPos bench, ResourceLocation recipe, boolean press, boolean good) {}

    /** Server to the players around: what a player works at now, or null when it stopped. */
    public record WorkOf(int entity, @Nullable WorkbenchCrafting.Working work) {}

    /** Server to one player: every workbench recipe their survivor knows (WorkbenchKnowledge#sync), replacing the last list. */
    public record Known(List<WorkbenchKnowledge.Entry> recipes) {}

    private BuildingPackets() {}

    public static int register(SimpleChannel channel, int id) {
        channel.registerMessage(id++, Place.class, (p, b) -> {
            b.writeResourceLocation(p.piece());
            b.writeBlockPos(p.pos());
            b.writeVarInt(p.orientation());
        }, b -> new Place(b.readResourceLocation(), b.readBlockPos(), b.readVarInt()),
                (p, s) -> server(s, player -> BuildingActions.place(player, p.piece(), new BuildingRules.Placement(p.pos(), p.orientation()))),
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(id++, Remove.class, (p, b) -> b.writeBlockPos(p.pos()), b -> new Remove(b.readBlockPos()),
                (p, s) -> server(s, player -> BuildingActions.remove(player, p.pos())), Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(id++, Craft.class, (p, b) -> {
            b.writeBlockPos(p.bench());
            b.writeResourceLocation(p.recipe());
        }, b -> new Craft(b.readBlockPos(), b.readResourceLocation()),
                (p, s) -> server(s, player -> WorkbenchCrafting.craft(player, p.bench(), p.recipe())), Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(id++, Work.class, (p, b) -> {
            b.writeBlockPos(p.bench());
            b.writeResourceLocation(p.recipe());
            b.writeBoolean(p.start());
            b.writeBoolean(p.discard());
        }, b -> new Work(b.readBlockPos(), b.readResourceLocation(), b.readBoolean(), b.readBoolean()),
                (p, s) -> server(s, player -> WorkbenchCrafting.work(player, p.bench(), p.recipe(), p.start(), p.discard())), Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(id++, WorkOf.class, (p, b) -> {
            b.writeVarInt(p.entity());
            b.writeBoolean(p.work() != null);
            if (p.work() != null) {
                b.writeBlockPos(p.work().bench());
                b.writeResourceLocation(p.work().recipe());
                b.writeUtf(p.work().action());
                b.writeVarInt(p.work().ticks());
            }
        }, b -> new WorkOf(b.readVarInt(), b.readBoolean()
                        ? new WorkbenchCrafting.Working(b.readBlockPos(), b.readResourceLocation(), b.readUtf(), b.readVarInt()) : null),
                (p, s) -> {
                    NetworkEvent.Context context = s.get();
                    context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> WorkbenchWork.remote(p.entity(), p.work())));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Known.class, BuildingPackets::writeKnown, BuildingPackets::readKnown,
                (p, s) -> {
                    NetworkEvent.Context context = s.get();
                    context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> WorkbenchRecipes.receive(p.recipes())));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id++, Act.class, (p, b) -> {
            b.writeBlockPos(p.bench());
            b.writeResourceLocation(p.recipe());
            b.writeBoolean(p.press());
            b.writeBoolean(p.good());
        }, b -> new Act(b.readBlockPos(), b.readResourceLocation(), b.readBoolean(), b.readBoolean()),
                (p, s) -> server(s, player -> WorkbenchCrafting.act(player, p.bench(), p.recipe(), p.press(), p.good())), Optional.of(NetworkDirection.PLAY_TO_SERVER));
        return id;
    }

    private static void writeKnown(Known p, FriendlyByteBuf b) {
        b.writeVarInt(p.recipes().size());
        for (var e : p.recipes()) {
            var r = e.recipe();
            b.writeResourceLocation(r.id());
            b.writeUtf(r.category());
            b.writeResourceLocation(r.result());
            b.writeVarInt(r.count());
            b.writeVarInt(r.materials().size());
            for (var m : r.materials()) {
                b.writeVarInt(m.items().size());
                for (var item : m.items()) b.writeResourceLocation(item);
                b.writeVarInt(m.count());
                b.writeEnum(m.unit());
            }
            b.writeVarInt(r.tools().size());
            for (var tool : r.tools()) b.writeResourceLocation(tool);
            b.writeVarInt(r.ticks());
            b.writeNullable(r.work(), FriendlyByteBuf::writeUtf);
            b.writeVarInt(r.steps().size());
            for (var st : r.steps()) {
                b.writeUtf(st.step());
                b.writeVarInt(st.times());
                b.writeNullable(st.piece(), FriendlyByteBuf::writeUtf);
            }
            b.writeVarInt(r.unlock().size());
            for (var u : r.unlock()) {
                b.writeUtf(u.type());
                b.writeNullable(u.value(), FriendlyByteBuf::writeUtf);
            }
            b.writeUtf(e.how().source());
            b.writeNullable(e.how().from(), FriendlyByteBuf::writeResourceLocation);
        }
    }

    private static Known readKnown(FriendlyByteBuf b) {
        int n = b.readVarInt();
        List<WorkbenchKnowledge.Entry> recipes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ResourceLocation id = b.readResourceLocation();
            String category = b.readUtf();
            ResourceLocation result = b.readResourceLocation();
            int count = b.readVarInt();
            int lines = b.readVarInt();
            List<WorkbenchCrafting.Material> materials = new ArrayList<>(lines);
            for (int j = 0; j < lines; j++) {
                int k = b.readVarInt();
                List<ResourceLocation> items = new ArrayList<>(k);
                for (int l = 0; l < k; l++) items.add(b.readResourceLocation());
                materials.add(new WorkbenchCrafting.Material(List.copyOf(items), b.readVarInt(), b.readEnum(WorkbenchCrafting.Unit.class)));
            }
            int t = b.readVarInt();
            List<ResourceLocation> tools = new ArrayList<>(t);
            for (int j = 0; j < t; j++) tools.add(b.readResourceLocation());
            int ticks = b.readVarInt();
            String work = b.readNullable(FriendlyByteBuf::readUtf);
            int ns = b.readVarInt();
            List<FurnitureWork.Step> steps = new ArrayList<>(ns);
            for (int j = 0; j < ns; j++) steps.add(new FurnitureWork.Step(b.readUtf(), b.readVarInt(), b.readNullable(FriendlyByteBuf::readUtf)));
            int u = b.readVarInt();
            List<WorkbenchCrafting.Unlock> unlock = new ArrayList<>(u);
            for (int j = 0; j < u; j++) unlock.add(new WorkbenchCrafting.Unlock(b.readUtf(), b.readNullable(FriendlyByteBuf::readUtf)));
            var recipe = new WorkbenchCrafting.Recipe(id, category, result, count, List.copyOf(materials), List.copyOf(tools), ticks, work, List.copyOf(unlock), List.copyOf(steps));
            recipes.add(new WorkbenchKnowledge.Entry(recipe, new WorkbenchKnowledge.How(b.readUtf(), b.readNullable(FriendlyByteBuf::readResourceLocation))));
        }
        return new Known(List.copyOf(recipes));
    }

    private static void server(Supplier<NetworkEvent.Context> supplier, Consumer<ServerPlayer> action) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) context.enqueueWork(() -> {
            if (player.isAlive() && !player.isSpectator()) action.accept(player);
        });
        context.setPacketHandled(true);
    }
}
