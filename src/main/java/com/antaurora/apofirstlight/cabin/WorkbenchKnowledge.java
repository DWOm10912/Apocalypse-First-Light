package com.antaurora.apofirstlight.cabin;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.building.BuildingPackets;
import com.antaurora.apofirstlight.cabin.WorkbenchCrafting.Recipe;
import com.antaurora.apofirstlight.cabin.WorkbenchCrafting.Unlock;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.survivor.SurvivorRecord;
import com.antaurora.apofirstlight.survivor.SurvivorWorldData;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who knows which workbench recipe (docs/gameplay/afl_workbench_v1_design.md 18). Knowledge follows one survivor, never
 * the player's account, and a successor starts without it. Two kinds:
 * <ul>
 * <li>from the recipe's own unlock list, worked out each time: everyone; the survivor's background;</li>
 * <li>learned (dotted later by materials in hand, a recipe made first, a book, a blueprint; today the dev command): kept
 * here, world data {@link #NAME}, per survivor id. It is kept apart from the survivor records, whose save format stays as
 * it is.</li>
 * </ul>
 * A creative player knows every recipe (no survivor). The server sends each player the recipes they know, whole
 * ({@link BuildingPackets.Known}): when the data loads or reloads, and when their survivor, game mode or knowledge changed
 * (checked once a second); unknown recipes never reach the client.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class WorkbenchKnowledge extends SavedData {
    public static final String NAME = ApocalypseFirstLight.MOD_ID + "_workbench_knowledge";

    /**
     * How a recipe is known: {@code source} experience (everyone, background), tinkering (materials, after), book,
     * blueprint, creative or command (screen.apocalypse_firstlight.workbench.source.&lt;source&gt;); {@code from}: the book
     * or blueprint item, else null.
     */
    public record How(String source, @Nullable ResourceLocation from) {}

    /** One known recipe as sent to its player. */
    public record Entry(Recipe recipe, How how) {}

    private record Learned(How how, long at) {}

    private final Map<UUID, Map<ResourceLocation, Learned>> survivors = new HashMap<>();
    /** Goes up with every learn or forget: the sync resends when it changed. */
    private int changes;
    /** What each player was last sent (#signature). */
    private static final Map<UUID, String> SENT = new HashMap<>();

    public static WorkbenchKnowledge get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(WorkbenchKnowledge::load, WorkbenchKnowledge::new, NAME);
    }

    @Nullable
    private static SurvivorRecord survivor(ServerPlayer player) {
        var lineage = SurvivorWorldData.get(player.server).lineage(player.getUUID());
        return lineage == null ? null : lineage.current();
    }

    /** How the player's survivor knows the recipe, or null: they do not. */
    @Nullable
    public static How how(ServerPlayer player, Recipe r) {
        if (player.getAbilities().instabuild) return new How("creative", null);
        SurvivorRecord s = survivor(player);
        for (Unlock u : r.unlock()) {
            if (u.type().equals("everyone")) return new How("experience", null);
            if (u.type().equals("background") && s != null && u.value().equals(s.background())) return new How("experience", null);
        }
        if (s == null) return null;
        Learned l = get(player.server).survivors.getOrDefault(s.id(), Map.of()).get(r.id());
        return l == null ? null : l.how();
    }

    public static boolean knows(ServerPlayer player, Recipe r) {
        return how(player, r) != null;
    }

    /** Teaches the player's survivor the recipe; false when there is no survivor or it is known already. */
    public static boolean learn(ServerPlayer player, Recipe r, How how) {
        SurvivorRecord s = survivor(player);
        if (s == null || knows(player, r)) return false;
        WorkbenchKnowledge data = get(player.server);
        data.survivors.computeIfAbsent(s.id(), k -> new LinkedHashMap<>()).put(r.id(), new Learned(how, player.serverLevel().getGameTime()));
        data.changes++;
        data.setDirty();
        sync(player);
        return true;
    }

    /** Forgets a learned recipe (not one from the unlock list); false when the survivor had not learned it. */
    public static boolean forget(ServerPlayer player, ResourceLocation recipe) {
        SurvivorRecord s = survivor(player);
        WorkbenchKnowledge data = get(player.server);
        var learned = s == null ? null : data.survivors.get(s.id());
        if (learned == null || learned.remove(recipe) == null) return false;
        if (learned.isEmpty()) data.survivors.remove(s.id());
        data.changes++;
        data.setDirty();
        sync(player);
        return true;
    }

    /** The recipes the player knows, and how. */
    public static List<Entry> known(ServerPlayer player) {
        List<Entry> out = new ArrayList<>();
        for (Recipe r : WorkbenchRecipeData.all()) {
            How how = how(player, r);
            if (how != null) out.add(new Entry(r, how));
        }
        return out;
    }

    public static void sync(ServerPlayer player) {
        SENT.put(player.getUUID(), signature(player));
        AflNetwork.skeletonTo(player, new BuildingPackets.Known(known(player)));
    }

    private static String signature(ServerPlayer player) {
        SurvivorRecord s = survivor(player);
        return (s == null ? "-" : s.id().toString()) + '|' + (s == null ? "" : s.background()) + '|' + player.getAbilities().instabuild
                + '|' + WorkbenchRecipeData.generation() + '|' + get(player.server).changes;
    }

    @SubscribeEvent
    public static void datapackSync(OnDatapackSyncEvent event) {
        for (var player : event.getPlayer() == null ? event.getPlayerList().getPlayers() : List.of(event.getPlayer())) sync(player);
    }

    /** Once a second: a player whose survivor, background, game mode or knowledge changed gets the list again. */
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers())
            if (!signature(player).equals(SENT.get(player.getUUID()))) sync(player);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        SENT.clear();
    }

    // ---------------------------------------------------------------------------------------------------- saving

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (var e : survivors.entrySet()) {
            CompoundTag s = new CompoundTag();
            s.putUUID("Survivor", e.getKey());
            ListTag recipes = new ListTag();
            for (var r : e.getValue().entrySet()) {
                CompoundTag t = new CompoundTag();
                t.putString("Recipe", r.getKey().toString());
                t.putString("Source", r.getValue().how().source());
                if (r.getValue().how().from() != null) t.putString("From", r.getValue().how().from().toString());
                t.putLong("At", r.getValue().at());
                recipes.add(t);
            }
            s.put("Recipes", recipes);
            list.add(s);
        }
        tag.putInt("Format", 1);
        tag.put("Survivors", list);
        return tag;
    }

    /** Learned recipes whose data is gone stay saved: a datapack that brings them back brings the knowledge back. */
    public static WorkbenchKnowledge load(CompoundTag tag) {
        WorkbenchKnowledge data = new WorkbenchKnowledge();
        for (Tag t : tag.getList("Survivors", Tag.TAG_COMPOUND)) {
            CompoundTag s = (CompoundTag) t;
            if (!s.hasUUID("Survivor")) continue;
            Map<ResourceLocation, Learned> recipes = new LinkedHashMap<>();
            for (Tag rt : s.getList("Recipes", Tag.TAG_COMPOUND)) {
                CompoundTag r = (CompoundTag) rt;
                ResourceLocation id = ResourceLocation.tryParse(r.getString("Recipe"));
                if (id == null) continue;
                ResourceLocation from = r.contains("From") ? ResourceLocation.tryParse(r.getString("From")) : null;
                recipes.put(id, new Learned(new How(r.getString("Source"), from), r.getLong("At")));
            }
            if (!recipes.isEmpty()) data.survivors.put(s.getUUID("Survivor"), recipes);
        }
        return data;
    }

    // ------------------------------------------------------------------------------------------------- dev command

    /** /afl dev workbench learn|forget &lt;recipe&gt;: the own survivor learns or forgets a recipe (source "command"). */
    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(Commands.literal("workbench")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("learn").then(Commands.argument("recipe", ResourceLocationArgument.id())
                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(WorkbenchRecipeData.all().stream().map(Recipe::id), b))
                        .executes(c -> learn(c))))
                .then(Commands.literal("forget").then(Commands.argument("recipe", ResourceLocationArgument.id())
                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(WorkbenchRecipeData.all().stream().map(Recipe::id), b))
                        .executes(c -> forget(c)))))));
    }

    private static int learn(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(c, "recipe");
        Recipe r = WorkbenchRecipeData.find(id);
        if (r == null) {
            c.getSource().sendFailure(Component.literal("no workbench recipe " + id));
            return 0;
        }
        if (!learn(player, r, new How("command", null))) {
            c.getSource().sendFailure(Component.literal("no living survivor, or " + id + " is known already"));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("learned " + id), false);
        return 1;
    }

    private static int forget(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(c, "recipe");
        if (!forget(player, id)) {
            c.getSource().sendFailure(Component.literal(id + " was not learned (recipes from their unlock list cannot be forgotten)"));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("forgot " + id), false);
        return 1;
    }
}
