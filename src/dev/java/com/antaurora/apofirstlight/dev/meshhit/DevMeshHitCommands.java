package com.antaurora.apofirstlight.dev.meshhit;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.meshhit.MeshBlockHitResult;
import com.antaurora.apofirstlight.meshhit.MeshHitClip;
import com.antaurora.apofirstlight.meshhit.MeshHitModels;
import com.antaurora.apofirstlight.meshhit.MeshHitProvider;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DEVELOPMENT ONLY (OP 2). The hit meshes (docs/rendering/mesh_hit_runtime_v1.md):
 * <ul>
 * <li>{@code /dev meshhit}: a test ray along the view (8 blocks): where the collision shape stops it and where the hit mesh
 * does, the normal, the mesh's triangles; particles at the mesh hit, along its normal.</li>
 * <li>{@code /dev meshhit check}: every block state of this mod: how many resolve to a hit mesh (dynamic ones, the pipes,
 * are counted apart: they need the world), the triangles loaded, and the blocks with none.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DevMeshHitCommands {
    private DevMeshHitCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dev")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("meshhit").executes(DevMeshHitCommands::ray)
                        .then(Commands.literal("check").executes(DevMeshHitCommands::check))));
    }

    private static int ray(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Vec3 eye = player.getEyePosition(), end = eye.add(player.getLookAngle().scale(8));
        ClipContext clip = new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
        BlockHitResult box = player.level().clip(clip), mesh = MeshHitClip.clip(player.level(), clip);
        StringBuilder out = new StringBuilder();
        out.append("box: ").append(describe(box, eye));
        out.append("\nmesh: ").append(describe(mesh, eye));
        if (mesh.getType() == HitResult.Type.BLOCK) {
            BlockState state = player.level().getBlockState(mesh.getBlockPos());
            MeshHitModels.Shape shape = MeshHitModels.shape(player.level(), mesh.getBlockPos(), state);
            out.append("\n").append(ForgeRegistries.BLOCKS.getKey(state.getBlock())).append(shape == null ? ": no hit mesh (collision shape)" : ": " + shape.triangles() + " triangles");
            Vec3 n = MeshBlockHitResult.normalOf(mesh);
            for (int k = 0; k < 6; k++) {
                Vec3 p = mesh.getLocation().add(n.scale(0.04 * k));
                player.serverLevel().sendParticles(player, ParticleTypes.END_ROD, true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        context.getSource().sendSuccess(() -> Component.literal(out.toString()), false);
        return 1;
    }

    private static String describe(BlockHitResult hit, Vec3 eye) {
        if (hit.getType() != HitResult.Type.BLOCK) return "nothing";
        Vec3 p = hit.getLocation(), n = MeshBlockHitResult.normalOf(hit);
        return String.format("%.3f blocks at (%.3f, %.3f, %.3f), normal (%.2f, %.2f, %.2f)%s", eye.distanceTo(p), p.x, p.y, p.z, n.x, n.y, n.z,
                hit instanceof MeshBlockHitResult ? " [mesh]" : "");
    }

    private static int check(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        int states = 0, meshed = 0, dynamic = 0;
        long triangles = 0;
        List<String> without = new ArrayList<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<Block>, Block> e : ForgeRegistries.BLOCKS.getEntries()) {
            ResourceLocation id = e.getKey().location();
            if (!ApocalypseFirstLight.MOD_ID.equals(id.getNamespace())) continue;
            if (e.getValue() instanceof MeshHitProvider) {
                dynamic++;
                continue;
            }
            boolean any = false;
            for (BlockState state : e.getValue().getStateDefinition().getPossibleStates()) {
                states++;
                MeshHitModels.Shape shape = MeshHitModels.shape(player.level(), BlockPos.ZERO, state);
                if (shape != null) {
                    meshed++;
                    triangles += shape.triangles();
                    any = true;
                }
            }
            if (!any) without.add(id.getPath());
        }
        final String summary = String.format("%d / %d block states have a hit mesh (%d triangles over them); %d dynamic blocks (pipes); %d blocks keep their collision shape",
                meshed, states, triangles, dynamic, without.size());
        context.getSource().sendSuccess(() -> Component.literal(summary), false);
        ApocalypseFirstLight.LOGGER.info("[AFL MESH HIT] {}; without: {}", summary, without);
        return 1;
    }
}
