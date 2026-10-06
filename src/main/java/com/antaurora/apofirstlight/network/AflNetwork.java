package com.antaurora.apofirstlight.network;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.radiation.RadiationZone;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.fml.DistExecutor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class AflNetwork {
    private static final String PROTOCOL = "37";
    private static SimpleChannel channel;
    private static int nextId;

    public static synchronized void register() {
        if (channel != null) {
            return;
        }
        channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(ApocalypseFirstLight.MOD_ID, "main"),
                () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
        channel.registerMessage(nextId++, RadiationSyncPacket.class,
                RadiationSyncPacket::encode, RadiationSyncPacket::decode, RadiationSyncPacket::handle);
        channel.registerMessage(nextId++, GeigerDataS2CPacket.class,
                GeigerDataS2CPacket::encode, GeigerDataS2CPacket::decode, GeigerDataS2CPacket::handle);
        channel.registerMessage(nextId++, ThermalGeneratorFuelSyncS2CPacket.class,
                ThermalGeneratorFuelSyncS2CPacket::encode, ThermalGeneratorFuelSyncS2CPacket::decode,
                ThermalGeneratorFuelSyncS2CPacket::handle);
        channel.registerMessage(nextId++, CrusherBalanceSyncS2CPacket.class,
                CrusherBalanceSyncS2CPacket::encode, CrusherBalanceSyncS2CPacket::decode,
                CrusherBalanceSyncS2CPacket::handle);
        channel.registerMessage(nextId++, CompressorBalanceSyncS2CPacket.class,
                CompressorBalanceSyncS2CPacket::encode, CompressorBalanceSyncS2CPacket::decode,
                CompressorBalanceSyncS2CPacket::handle);
        channel.registerMessage(nextId++, AlloyFurnaceBalanceSyncS2CPacket.class,
                AlloyFurnaceBalanceSyncS2CPacket::encode, AlloyFurnaceBalanceSyncS2CPacket::decode,
                AlloyFurnaceBalanceSyncS2CPacket::handle);
        channel.registerMessage(nextId++, ProcessingMachineBalanceSyncS2CPacket.class,
                ProcessingMachineBalanceSyncS2CPacket::encode,
                ProcessingMachineBalanceSyncS2CPacket::decode,
                ProcessingMachineBalanceSyncS2CPacket::handle);
        channel.registerMessage(nextId++, FluidPipeVisualS2CPacket.class,
                FluidPipeVisualS2CPacket::encode, FluidPipeVisualS2CPacket::decode,
                FluidPipeVisualS2CPacket::handle);
        channel.registerMessage(nextId++, FuelStainS2CPacket.class,
                FuelStainS2CPacket::encode, FuelStainS2CPacket::decode, FuelStainS2CPacket::handle);
        channel.registerMessage(nextId++, FuelLeakS2CPacket.class,
                FuelLeakS2CPacket::encode, FuelLeakS2CPacket::decode, FuelLeakS2CPacket::handle);
        channel.registerMessage(nextId++, BulletImpactS2CPacket.class,
                BulletImpactS2CPacket::encode, BulletImpactS2CPacket::decode, BulletImpactS2CPacket::handle);
        channel.registerMessage(nextId++, FuelBlastS2CPacket.class,
                FuelBlastS2CPacket::encode, FuelBlastS2CPacket::decode, FuelBlastS2CPacket::handle);
        channel.registerMessage(nextId++, FuelSprayHitsC2SPacket.class,
                FuelSprayHitsC2SPacket::encode, FuelSprayHitsC2SPacket::decode, FuelSprayHitsC2SPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(nextId++, ExplosionTinnitusS2CPacket.class,
                ExplosionTinnitusS2CPacket::encode, ExplosionTinnitusS2CPacket::decode,
                ExplosionTinnitusS2CPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(nextId++, NativeGunC2SPacket.class,
                NativeGunC2SPacket::encode, NativeGunC2SPacket::decode, NativeGunC2SPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(nextId++, NativeTriggerPacket.class,NativeTriggerPacket::encode,NativeTriggerPacket::decode,
                NativeTriggerPacket::handle,java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(nextId++, NativeShotS2CPacket.class,
                NativeShotS2CPacket::encode, NativeShotS2CPacket::decode, NativeShotS2CPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(nextId++, NativeShotFxS2CPacket.class,
                NativeShotFxS2CPacket::encode, NativeShotFxS2CPacket::decode, NativeShotFxS2CPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(nextId++, NativeHitS2CPacket.class,
                NativeHitS2CPacket::encode, NativeHitS2CPacket::decode, NativeHitS2CPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(nextId++, GunDataPacket.class, GunDataPacket::encode, GunDataPacket::decode,
                GunDataPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(nextId++, SightExchangePacket.class,SightExchangePacket::encode,SightExchangePacket::decode,
                SightExchangePacket::handle,java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(nextId++, MaintenancePacket.class,MaintenancePacket::encode,MaintenancePacket::decode,
                MaintenancePacket::handle,java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(nextId++, MaintenanceResult.class,MaintenanceResult::encode,MaintenanceResult::decode,
                MaintenanceResult::handle,java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(nextId++, NativeInspectPacket.class, NativeInspectPacket::encode, NativeInspectPacket::decode,
                NativeInspectPacket::handle, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(nextId++, BeverageCoolerDoorC2SPacket.class,
                BeverageCoolerDoorC2SPacket::encode, BeverageCoolerDoorC2SPacket::decode,
                BeverageCoolerDoorC2SPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(nextId++, RestroomDoorC2SPacket.class, RestroomDoorC2SPacket::encode,
                RestroomDoorC2SPacket::decode, RestroomDoorC2SPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(nextId++, CrowbarSmashPacket.class,CrowbarSmashPacket::encode,CrowbarSmashPacket::decode,
                CrowbarSmashPacket::handle,java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(nextId++, CrowbarSmashPacket.Cancel.class,CrowbarSmashPacket.Cancel::encode,CrowbarSmashPacket.Cancel::decode,
                CrowbarSmashPacket.Cancel::handle,java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(nextId++,FieldAttachmentPacket.class,FieldAttachmentPacket::encode,FieldAttachmentPacket::decode,
                FieldAttachmentPacket::handle,java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(nextId++,FieldAttachmentCancel.class,FieldAttachmentCancel::encode,FieldAttachmentCancel::decode,
                FieldAttachmentCancel::handle,java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(nextId++,FieldAttachmentResult.class,FieldAttachmentResult::encode,FieldAttachmentResult::decode,
                FieldAttachmentResult::handle,java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(nextId++, ContainerSearchSoundS2CPacket.class, ContainerSearchSoundS2CPacket::encode,
                ContainerSearchSoundS2CPacket::decode, ContainerSearchSoundS2CPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        nextId = com.antaurora.apofirstlight.weight.WeightPackets.register(channel, nextId);
        nextId = com.antaurora.apofirstlight.stamina.StaminaPackets.register(channel, nextId);
        nextId = com.antaurora.apofirstlight.thirst.ThirstPackets.register(channel, nextId);
        nextId = com.antaurora.apofirstlight.temperature.TemperaturePackets.register(channel, nextId);
    }

    /** Temperature V1: the player's own temperature state. */
    public static void temperatureState(ServerPlayer player, com.antaurora.apofirstlight.temperature.TemperaturePackets.State packet) {
        if (channel != null) channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Thirst V1: the player's own thirst. */
    public static void thirstState(ServerPlayer player, com.antaurora.apofirstlight.thirst.ThirstPackets.State packet) {
        if (channel != null) channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    /** Thirst V1 (client): one sip at the water source in view. */
    public static void thirstSip() {
        if (channel != null) channel.sendToServer(new com.antaurora.apofirstlight.thirst.ThirstPackets.Sip());
    }

    /** Stamina V1: the player's own stamina state. */
    public static void staminaState(ServerPlayer player, com.antaurora.apofirstlight.stamina.StaminaPackets.State packet) {
        if (channel != null) channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    /** Stamina V1: a player's breathing level, to the players tracking them (not themselves). */
    public static void staminaBreath(ServerPlayer player, com.antaurora.apofirstlight.stamina.StaminaPackets.Breath packet) {
        if (channel != null) channel.send(PacketDistributor.TRACKING_ENTITY.with(() -> player), packet);
    }

    public static void weightPolicy(ServerPlayer player, com.antaurora.apofirstlight.weight.WeightPackets.Policy packet) {
        if (channel != null) channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    public static void weightState(ServerPlayer player, com.antaurora.apofirstlight.weight.WeightPackets.State packet) {
        if (channel != null) channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    public static void weightData(ServerPlayer player, com.antaurora.apofirstlight.weight.WeightPackets.Data packet) {
        if (channel != null) channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Progressive Container Search: a container's search sound running (its id, repeated as a keep-alive) or stopped (null), to everyone tracking its chunk. */
    public static void containerSearchSound(ServerLevel level, BlockPos pos, @org.jetbrains.annotations.Nullable ResourceLocation sound) {
        if (channel != null) channel.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)),
                new ContainerSearchSoundS2CPacket(pos.immutable(), sound));
    }

    public static void requestFieldAttachment(com.antaurora.apofirstlight.weapon.FieldAttachmentActionRequest request){
        if(channel!=null)channel.sendToServer(new FieldAttachmentPacket(request));
    }
    public static void cancelFieldAttachment(long token){
        if(channel!=null)channel.sendToServer(new FieldAttachmentCancel(token));
    }
    public static void fieldAttachmentResult(ServerPlayer player,long token,int phase){
        if(channel!=null&&!player.hasDisconnected())channel.send(PacketDistributor.PLAYER.with(()->player),new FieldAttachmentResult(token,phase));
    }
    public record FieldAttachmentPacket(com.antaurora.apofirstlight.weapon.FieldAttachmentActionRequest request){
        static void encode(FieldAttachmentPacket p,FriendlyByteBuf b){
            var r=p.request;b.writeLong(r.token());b.writeVarInt(r.selectedSlot());b.writeLong(r.gunId());
            b.writeItem(r.expectedGun());b.writeEnum(r.target());b.writeVarInt(r.sourceSlot());b.writeItem(r.expectedSource());
        }
        static FieldAttachmentPacket decode(FriendlyByteBuf b){
            return new FieldAttachmentPacket(new com.antaurora.apofirstlight.weapon.FieldAttachmentActionRequest(
                    b.readLong(),b.readVarInt(),b.readLong(),b.readItem(),
                    b.readEnum(com.antaurora.apofirstlight.weapon.NativeAttachment.Slot.class),b.readVarInt(),b.readItem()));
        }
        static void handle(FieldAttachmentPacket p,Supplier<NetworkEvent.Context> supplier){
            var c=supplier.get();c.enqueueWork(()->{var player=c.getSender();
                if(player!=null)com.antaurora.apofirstlight.weapon.FieldAttachmentOperation.begin(player,p.request);
            });c.setPacketHandled(true);
        }
    }
    public record FieldAttachmentCancel(long token){
        static void encode(FieldAttachmentCancel p,FriendlyByteBuf b){b.writeLong(p.token);}
        static FieldAttachmentCancel decode(FriendlyByteBuf b){return new FieldAttachmentCancel(b.readLong());}
        static void handle(FieldAttachmentCancel p,Supplier<NetworkEvent.Context> supplier){
            var c=supplier.get();c.enqueueWork(()->{var player=c.getSender();
                if(player!=null)com.antaurora.apofirstlight.weapon.FieldAttachmentOperation.cancel(player,p.token);
            });c.setPacketHandled(true);
        }
    }
    public record FieldAttachmentResult(long token,int phase){
        static void encode(FieldAttachmentResult p,FriendlyByteBuf b){b.writeLong(p.token);b.writeByte(p.phase);}
        static FieldAttachmentResult decode(FriendlyByteBuf b){return new FieldAttachmentResult(b.readLong(),b.readUnsignedByte());}
        static void handle(FieldAttachmentResult p,Supplier<NetworkEvent.Context> supplier){
            var c=supplier.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->{
                if(net.minecraft.client.Minecraft.getInstance().screen instanceof com.antaurora.apofirstlight.client.FieldAttachmentScreen screen)
                    screen.result(p.token,p.phase);
            }));c.setPacketHandled(true);
        }
    }

    public static void crowbarSmash(ServerPlayer player,java.util.UUID action,BlockPos target,int phase) {
        if(channel!=null)channel.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->player),new CrowbarSmashPacket(player.getUUID(),action,target,phase));
    }
    public static void cancelCrowbarSmash(java.util.UUID action) {
        if(channel!=null)channel.sendToServer(new CrowbarSmashPacket.Cancel(action));
    }

    public static void requestMaintenance(com.antaurora.apofirstlight.weapon.MaintenanceActionRequest request){
        if(channel!=null)channel.sendToServer(new MaintenancePacket(request));
    }

    public static void requestBeverageCoolerDoor(BlockPos master, boolean left) {
        if (channel != null) channel.sendToServer(new BeverageCoolerDoorC2SPacket(master, left));
    }
    public static void requestRestroomDoor(BlockPos pos) {
        if(channel!=null)channel.sendToServer(new RestroomDoorC2SPacket(pos));
    }
    public record MaintenancePacket(com.antaurora.apofirstlight.weapon.MaintenanceActionRequest request){
        static void encode(MaintenancePacket p,FriendlyByteBuf b){var r=p.request;
            b.writeVarInt(r.containerId());b.writeBlockPos(r.bench());b.writeLong(r.revision());b.writeItem(r.expectedGun());
            b.writeEnum(r.target());b.writeVarInt(r.sourceSlot());b.writeItem(r.expectedSource());}
        static MaintenancePacket decode(FriendlyByteBuf b){return new MaintenancePacket(new com.antaurora.apofirstlight.weapon.MaintenanceActionRequest(
                b.readVarInt(),b.readBlockPos(),b.readLong(),b.readItem(),b.readEnum(com.antaurora.apofirstlight.weapon.NativeAttachment.Slot.class),b.readVarInt(),b.readItem()));}
        static void handle(MaintenancePacket p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->{
            var player=c.getSender();if(player==null)return;
            com.antaurora.apofirstlight.weapon.MaintenanceAttachmentOperation.begin(player,p.request);
        });c.setPacketHandled(true);}
    }
    /** 0=cancel/rejected, 1=committed, 2=server-approved action started. */
    public static void maintenanceResult(ServerPlayer player,int containerId,int phase){
        channel.send(PacketDistributor.PLAYER.with(()->player),new MaintenanceResult(containerId,phase));
    }
    public record MaintenanceResult(int containerId,int phase){
        static void encode(MaintenanceResult p,FriendlyByteBuf b){b.writeVarInt(p.containerId);b.writeByte(p.phase);}
        static MaintenanceResult decode(FriendlyByteBuf b){return new MaintenanceResult(b.readVarInt(),b.readUnsignedByte());}
        static void handle(MaintenanceResult p,Supplier<NetworkEvent.Context> supplier){var c=supplier.get();c.enqueueWork(()->
            DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->{
                if(net.minecraft.client.Minecraft.getInstance().screen instanceof com.antaurora.apofirstlight.client.GunMaintenanceScreen s
                        &&s.getMenu().containerId==p.containerId)s.attachmentResult(p.phase);
            }));c.setPacketHandled(true);}
    }

    /** Retired quick-exchange API. Kept inert for old development probes; use maintenance operations. */
    @Deprecated
    public static void requestSightExchange(int slot,net.minecraft.world.item.ItemStack gun,net.minecraft.world.item.ItemStack offhand){}
    /** Reserved wire ID: old clients cannot bypass the maintenance bench, and later packet IDs stay stable. */
    public record SightExchangePacket(int slot,net.minecraft.world.item.ItemStack gun,net.minecraft.world.item.ItemStack offhand){
        static void encode(SightExchangePacket p,FriendlyByteBuf b){b.writeVarInt(p.slot);b.writeItem(p.gun);b.writeItem(p.offhand);}
        static SightExchangePacket decode(FriendlyByteBuf b){return new SightExchangePacket(b.readVarInt(),b.readItem(),b.readItem());}
        static void handle(SightExchangePacket p,Supplier<NetworkEvent.Context> supplier){
            supplier.get().setPacketHandled(true); // Deliberately reject every legacy quick-exchange request.
        }
    }

    public static void sendNativeHit(ServerPlayer shooter, boolean head) {
        channel.send(PacketDistributor.PLAYER.with(()->shooter),new NativeHitS2CPacket(head));
    }
    public static void sendGunData(ServerPlayer player,String json) {
        channel.send(PacketDistributor.PLAYER.with(()->player),new GunDataPacket(json));
    }
    public record GunDataPacket(String json) {
        static void encode(GunDataPacket p,FriendlyByteBuf b){b.writeUtf(p.json,1048576);}
        static GunDataPacket decode(FriendlyByteBuf b){return new GunDataPacket(b.readUtf(1048576));}
        static void handle(GunDataPacket p,Supplier<NetworkEvent.Context> supplier) {
            var c=supplier.get();c.enqueueWork(()->com.antaurora.apofirstlight.weapon.NativeGunData.receive(p.json));c.setPacketHandled(true);
        }
    }
    public record NativeHitS2CPacket(boolean head) {
        static void encode(NativeHitS2CPacket p,FriendlyByteBuf b) { b.writeBoolean(p.head); }
        static NativeHitS2CPacket decode(FriendlyByteBuf b) { return new NativeHitS2CPacket(b.readBoolean()); }
        static void handle(NativeHitS2CPacket p,Supplier<NetworkEvent.Context> supplier) {
            var context=supplier.get();
            context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    ()->()->com.antaurora.apofirstlight.weapon.client.NativeGunCrosshair.hit(p.head)));
            context.setPacketHandled(true);
        }
    }

    public static void sendNativeShot(ServerPlayer player, int slot, long gunId, net.minecraft.world.phys.Vec3 shotEnd) {
        sendNativeShot(player,slot,gunId,shotEnd,0);
    }
    public static void sendNativeShot(ServerPlayer player, int slot, long gunId, net.minecraft.world.phys.Vec3 shotEnd,long shotId) {
        sendNativeShot(player,slot,gunId,shotEnd,shotId,"");
    }
    public static void sendNativeShot(ServerPlayer player, int slot, long gunId, net.minecraft.world.phys.Vec3 shotEnd,long shotId,String hitEffect) {
        sendNativeShot(player, slot, gunId, shotEnd, shotId, hitEffect, List.of(shotEnd));
    }
    public static void sendNativeShot(ServerPlayer player, int slot, long gunId, net.minecraft.world.phys.Vec3 shotEnd,long shotId,
                                      String hitEffect, List<net.minecraft.world.phys.Vec3> pelletEnds) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), new NativeShotS2CPacket(slot, gunId,shotEnd,shotId,hitEffect,pelletEnds));
        channel.send(PacketDistributor.TRACKING_ENTITY.with(() -> player),
                new NativeShotFxS2CPacket(player.getId(), gunId, shotEnd,shotId,hitEffect,pelletEnds));
    }

    private static void writePelletEnds(FriendlyByteBuf b, List<net.minecraft.world.phys.Vec3> ends) {
        b.writeVarInt(ends.size());
        for (var end : ends) { b.writeDouble(end.x); b.writeDouble(end.y); b.writeDouble(end.z); }
    }
    private static List<net.minecraft.world.phys.Vec3> readPelletEnds(FriendlyByteBuf b) {
        int count = b.readVarInt();
        if (count < 1 || count > 64) throw new IllegalArgumentException("Invalid pellet endpoint count: " + count);
        var ends = new ArrayList<net.minecraft.world.phys.Vec3>(count);
        for (int i = 0; i < count; i++) ends.add(new net.minecraft.world.phys.Vec3(b.readDouble(), b.readDouble(), b.readDouble()));
        return List.copyOf(ends);
    }

    /** Notification only; ammo, hitscan and HUD remain on their existing paths. */
    public record NativeShotFxS2CPacket(int shooterId, long gunId, net.minecraft.world.phys.Vec3 shotEnd,long shotId,String hitEffect,
                                         List<net.minecraft.world.phys.Vec3> pelletEnds) {
        public NativeShotFxS2CPacket { pelletEnds = List.copyOf(pelletEnds); }
        public NativeShotFxS2CPacket(int shooterId,long gunId,net.minecraft.world.phys.Vec3 end,long shotId){this(shooterId,gunId,end,shotId,"",List.of(end));}
        static void encode(NativeShotFxS2CPacket p, FriendlyByteBuf b) {
            b.writeVarInt(p.shooterId); b.writeLong(p.gunId);
            b.writeDouble(p.shotEnd.x); b.writeDouble(p.shotEnd.y); b.writeDouble(p.shotEnd.z);
            b.writeLong(p.shotId);
            b.writeUtf(p.hitEffect,64);
            writePelletEnds(b, p.pelletEnds);
        }
        static NativeShotFxS2CPacket decode(FriendlyByteBuf b) {
            return new NativeShotFxS2CPacket(b.readVarInt(), b.readLong(),
                    new net.minecraft.world.phys.Vec3(b.readDouble(), b.readDouble(), b.readDouble()),b.readLong(),b.readUtf(64),readPelletEnds(b));
        }
        static void handle(NativeShotFxS2CPacket p, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> {
                        com.antaurora.apofirstlight.weapon.client.NativeHitEffects.play(p.hitEffect,p.shotEnd);
                        if(!com.antaurora.apofirstlight.weapon.client.NativeShotVisualSnapshot.confirm(p.shooterId,p.gunId,p.shotId,p.pelletEnds)){
                            com.antaurora.apofirstlight.weapon.client.NativeGunFx.shot(p.shooterId, p.gunId);
                            com.antaurora.apofirstlight.weapon.client.NativeBulletTrails.shot(p.shooterId, p.gunId, p.pelletEnds,p.shotId);
                        }
                    }));
            context.setPacketHandled(true);
        }
    }

    public record NativeShotS2CPacket(int slot, long gunId,net.minecraft.world.phys.Vec3 shotEnd,long shotId,String hitEffect,
                                       List<net.minecraft.world.phys.Vec3> pelletEnds) {
        public NativeShotS2CPacket { pelletEnds = List.copyOf(pelletEnds); }
        public NativeShotS2CPacket(int slot,long gunId,net.minecraft.world.phys.Vec3 end,long shotId){this(slot,gunId,end,shotId,"",List.of(end));}
        static void encode(NativeShotS2CPacket p, FriendlyByteBuf b) { b.writeVarInt(p.slot); b.writeLong(p.gunId);b.writeDouble(p.shotEnd.x);b.writeDouble(p.shotEnd.y);b.writeDouble(p.shotEnd.z);b.writeLong(p.shotId);b.writeUtf(p.hitEffect,64);writePelletEnds(b,p.pelletEnds); }
        static NativeShotS2CPacket decode(FriendlyByteBuf b) { return new NativeShotS2CPacket(b.readVarInt(), b.readLong(),new net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readLong(),b.readUtf(64),readPelletEnds(b)); }
        static void handle(NativeShotS2CPacket p, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> {
                        com.antaurora.apofirstlight.weapon.client.NativeHitEffects.play(p.hitEffect,p.shotEnd);
                        com.antaurora.apofirstlight.weapon.client.NativeGunHud.shot(p.slot, p.gunId);
                        var player=net.minecraft.client.Minecraft.getInstance().player;
                        if(player!=null&&!com.antaurora.apofirstlight.weapon.client.NativeShotVisualSnapshot.confirm(player.getId(),p.gunId,p.shotId,p.pelletEnds)){
                            com.antaurora.apofirstlight.weapon.client.NativeGunFx.shot(player.getId(),p.gunId);
                            com.antaurora.apofirstlight.weapon.client.NativeBulletTrails.shot(player.getId(),p.gunId,p.pelletEnds,p.shotId);
                        }
                        com.antaurora.apofirstlight.weapon.client.NativeGunRecoil.confirmedShot(p.slot, p.gunId);
                    }));
            context.setPacketHandled(true);
        }
    }

    public static void requestNativeGun(boolean reload, int slot) {
        requestNativeGun(reload,slot,0);
    }
    public static void requestInspect(int slot, long id, boolean cancel) {
        if (channel != null) channel.sendToServer(new NativeInspectPacket(slot, id, cancel));
    }
    public record NativeInspectPacket(int slot, long id, boolean cancel) {
        static void encode(NativeInspectPacket p, FriendlyByteBuf b) { b.writeVarInt(p.slot); b.writeLong(p.id); b.writeBoolean(p.cancel); }
        static NativeInspectPacket decode(FriendlyByteBuf b) { return new NativeInspectPacket(b.readVarInt(), b.readLong(), b.readBoolean()); }
        static void handle(NativeInspectPacket p, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player == null) return;
                if (p.cancel) com.antaurora.apofirstlight.weapon.NativeGunActions.cancelInspect(player, p.id);
                else if (p.slot >= 0 && p.slot < 9 && player.getInventory().selected == p.slot
                        && software.bernie.geckolib.animatable.GeoItem.getId(player.getMainHandItem()) == p.id)
                    com.antaurora.apofirstlight.weapon.NativeGunActions.operation(player, "inspect");
            });
            context.setPacketHandled(true);
        }
    }
    public static void requestNativeGun(boolean reload,int slot,long shotId){
        if (channel != null) channel.sendToServer(new NativeGunC2SPacket(reload, slot,shotId));
    }

    public record NativeGunC2SPacket(boolean reload, int slot,long shotId) {
        public static void encode(NativeGunC2SPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBoolean(packet.reload);
            buffer.writeVarInt(packet.slot);
            buffer.writeLong(packet.shotId);
        }
        public static NativeGunC2SPacket decode(FriendlyByteBuf buffer) {
            return new NativeGunC2SPacket(buffer.readBoolean(), buffer.readVarInt(),buffer.readLong());
        }
        public static void handle(NativeGunC2SPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            if (context.getDirection() == net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER) {
                context.enqueueWork(() -> {
                    ServerPlayer player = context.getSender();
                    if (player != null && packet.reload) com.antaurora.apofirstlight.weapon.NativeGunActions
                            .request(player, packet.reload, packet.slot,packet.shotId);
                });
            }
            context.setPacketHandled(true);
        }
    }

    private AflNetwork() {
    }

    public static void nativeTrigger(int action,int slot,long shotId,long gunId) {
        if(channel!=null)channel.sendToServer(new NativeTriggerPacket(action,slot,shotId,gunId));
    }
    public static void nativeTrigger(int action,int slot,long shotId,long gunId,boolean aiming) {
        nativeTrigger(action == 1 && aiming ? 3 : action,slot,shotId,gunId);
    }
    public record NativeTriggerPacket(int action,int slot,long shotId,long gunId) {
        static void encode(NativeTriggerPacket p,FriendlyByteBuf b){b.writeByte(p.action);b.writeVarInt(p.slot);b.writeLong(p.shotId);b.writeLong(p.gunId);}
        static NativeTriggerPacket decode(FriendlyByteBuf b){return new NativeTriggerPacket(b.readUnsignedByte(),b.readVarInt(),b.readLong(),b.readLong());}
        static void handle(NativeTriggerPacket p,Supplier<NetworkEvent.Context> supplier){
            var c=supplier.get();
            c.enqueueWork(()->{var player=c.getSender();if(player==null)return;
                switch(p.action){
                    case 0 -> com.antaurora.apofirstlight.weapon.NativeFireControl.release(player);
                    case 1, 3 -> {if(p.shotId>0)com.antaurora.apofirstlight.weapon.NativeFireControl.press(player,p.slot,p.shotId,p.gunId,p.action==3);}
                    case 2 -> com.antaurora.apofirstlight.weapon.NativeFireControl.switchMode(player,p.slot,p.gunId);
                    default -> { }
                }
            });c.setPacketHandled(true);
        }
    }

    public static void sendExplosionTinnitus(ServerPlayer player, float severity) {
        sendTinnitusImpulse(player, severity);
    }

    /** Shared one-shot tinnitus impulse used by independent explosion and gunshot server calculations. */
    public static void sendTinnitusImpulse(ServerPlayer player, float severity) {
        if (channel == null) {
            throw new IllegalStateException("AFL network channel was not registered during mod initialization");
        }
        float effectiveSeverity = com.antaurora.apofirstlight.equipment.HearingProtectionManager
                .effectiveTinnitusSeverity(player, severity);
        channel.sendTo(new ExplosionTinnitusS2CPacket(effectiveSeverity, player.getId(),
                        player.level().dimension().location()), player.connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }

    public record ExplosionTinnitusS2CPacket(float severity, int playerId, ResourceLocation dimension) {
        public static void encode(ExplosionTinnitusS2CPacket packet, FriendlyByteBuf buffer) {
            buffer.writeFloat(packet.severity);
            buffer.writeVarInt(packet.playerId);
            buffer.writeResourceLocation(packet.dimension);
        }

        public static ExplosionTinnitusS2CPacket decode(FriendlyByteBuf buffer) {
            return new ExplosionTinnitusS2CPacket(buffer.readFloat(), buffer.readVarInt(),
                    buffer.readResourceLocation());
        }

        public static void handle(ExplosionTinnitusS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            if (context.getDirection() == net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT) {
                context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> com.antaurora.apofirstlight.client.ExplosionTinnitusClientState
                                .trigger(packet.severity, packet.playerId, packet.dimension)));
            }
            context.setPacketHandled(true);
        }
    }

    public static void sendRadiation(ServerPlayer player, double finalRadiation) {
        if (channel == null) {
            throw new IllegalStateException("AFL network channel was not registered during mod initialization");
        }
        channel.sendTo(new RadiationSyncPacket(finalRadiation), player.connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendGeigerData(ServerPlayer player, double measuredRadiation, double cumulativeDose,
                                      double residualRadiationRate, RadiationZone zone) {
        if (channel == null) throw new IllegalStateException("AFL network channel was not registered during mod initialization");
        channel.sendTo(new GeigerDataS2CPacket(measuredRadiation, cumulativeDose, residualRadiationRate, zone), player.connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendThermalGeneratorFuels(ServerPlayer player,
                                                  Map<ResourceLocation, Integer> fuelEnergies) {
        if (channel == null) {
            throw new IllegalStateException("AFL network channel was not registered during mod initialization");
        }
        channel.sendTo(new ThermalGeneratorFuelSyncS2CPacket(fuelEnergies), player.connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendCrusherBalance(ServerPlayer player, int workFePerTick) {
        if (channel == null) {
            throw new IllegalStateException("AFL network channel was not registered during mod initialization");
        }
        channel.sendTo(new CrusherBalanceSyncS2CPacket(workFePerTick), player.connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendCompressorBalance(ServerPlayer player, int workFePerTick) {
        if (channel == null) {
            throw new IllegalStateException("AFL network channel was not registered during mod initialization");
        }
        channel.sendTo(new CompressorBalanceSyncS2CPacket(workFePerTick), player.connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendAlloyFurnaceBalance(ServerPlayer player, int workFePerTick) {
        if (channel == null) {
            throw new IllegalStateException("AFL network channel was not registered during mod initialization");
        }
        channel.sendTo(new AlloyFurnaceBalanceSyncS2CPacket(workFePerTick), player.connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendProcessingMachineBalance(ServerPlayer player,
                                                    int chemicalWorkFePerTick,
                                                    int industrialWorkFePerTickPerLane) {
        if (channel == null) {
            throw new IllegalStateException("AFL network channel was not registered during mod initialization");
        }
        channel.sendTo(new ProcessingMachineBalanceSyncS2CPacket(
                        chemicalWorkFePerTick, industrialWorkFePerTickPerLane),
                player.connection.connection, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }

    /** The local player's fuel stream of a dispenser nozzle landed at these points (client/FuelNozzleJets). */
    public static void sendFuelSprayHits(BlockPos dispenser, int nozzle, List<net.minecraft.world.phys.Vec3> points, List<net.minecraft.core.Direction> faces) {
        if (channel != null) channel.sendToServer(new FuelSprayHitsC2SPacket(dispenser, nozzle, List.copyOf(points), List.copyOf(faces)));
    }

    /** Fuel stains changed in one chunk (fluid/FuelSpills): to the players tracking it. */
    public static void sendFuelStains(ServerLevel level, ChunkPos chunk, Collection<com.antaurora.apofirstlight.fluid.FuelStainIndex.Stain> upserts,
                                      Collection<Long> removed) {
        if (channel == null || !level.getChunkSource().hasChunk(chunk.x, chunk.z)) return;
        channel.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunk(chunk.x, chunk.z)), FuelStainS2CPacket.of(upserts, removed));
    }

    /** Fuel stains gone wherever they were (over the cap): to everyone in the level. */
    public static void sendFuelStainsRemovedEverywhere(ServerLevel level, Collection<Long> removed) {
        if (channel == null) return;
        channel.send(PacketDistributor.DIMENSION.with(level::dimension), FuelStainS2CPacket.of(List.of(), removed));
    }

    /** A fuel container went up (fluid/FuelLeaks): to the players watching it, for the smoke burst and column. */
    public static void sendFuelBlast(ServerLevel level, Vec3 at, float power, boolean diesel) {
        if (channel == null) return;
        ChunkPos chunk = new ChunkPos(BlockPos.containing(at));
        if (!level.getChunkSource().hasChunk(chunk.x, chunk.z)) return;
        channel.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunk(chunk.x, chunk.z)), new FuelBlastS2CPacket(at, power, diesel));
    }

    /** A bullet struck a block (weapon/BulletImpacts): to the players watching it, for the hole decal and the dust. */
    public static void sendBulletImpact(ServerLevel level, Vec3 at, net.minecraft.core.Direction face, BlockPos block, boolean holed, boolean spark) {
        if (channel == null) return;
        ChunkPos chunk = new ChunkPos(block);
        if (!level.getChunkSource().hasChunk(chunk.x, chunk.z)) return;
        channel.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunk(chunk.x, chunk.z)), new BulletImpactS2CPacket(at, face, block, holed, spark));
    }

    /** Bullet holes in fuel containers changed or gone (fluid/FuelLeaks): to everyone in the level (there are few). */
    public static void sendFuelLeaks(ServerLevel level, Collection<com.antaurora.apofirstlight.fluid.FuelLeaks.Hole> upserts, Collection<Long> removed) {
        if (channel == null) return;
        channel.send(PacketDistributor.DIMENSION.with(level::dimension), FuelLeakS2CPacket.of(upserts, removed, false));
    }

    /** All of a level's holes to a player who arrives in it (replacing what the client had). */
    public static void sendFuelLeaksTo(ServerPlayer player, Collection<com.antaurora.apofirstlight.fluid.FuelLeaks.Hole> holes) {
        if (channel == null) return;
        channel.sendTo(FuelLeakS2CPacket.of(holes, List.of(), true), player.connection.connection, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }

    /** A chunk's fuel stains to a player who starts tracking it. */
    public static void sendFuelStainsTo(ServerPlayer player, Collection<com.antaurora.apofirstlight.fluid.FuelStainIndex.Stain> stains) {
        if (channel == null) return;
        channel.sendTo(FuelStainS2CPacket.of(stains, List.of()), player.connection.connection, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
    }

    public static void sendFluidPipeVisuals(ServerLevel level,
                                            Collection<FluidPipeVisualUpdate> updates) {
        if (channel == null) {
            throw new IllegalStateException("AFL network channel was not registered during mod initialization");
        }
        Map<ChunkPos, List<FluidPipeVisualUpdate>> byChunk = new LinkedHashMap<>();
        for (FluidPipeVisualUpdate update : updates) {
            byChunk.computeIfAbsent(new ChunkPos(update.position()), ignored -> new ArrayList<>())
                    .add(update);
        }
        byChunk.forEach((chunkPosition, chunkUpdates) -> {
            if (level.getChunkSource().hasChunk(chunkPosition.x, chunkPosition.z)) {
                channel.send(PacketDistributor.TRACKING_CHUNK.with(
                                () -> level.getChunk(chunkPosition.x, chunkPosition.z)),
                        new FluidPipeVisualS2CPacket(chunkUpdates));
            }
        });
    }

    public record RadiationSyncPacket(double finalRadiation) {
        public static void encode(RadiationSyncPacket packet, FriendlyByteBuf buffer) {
            buffer.writeDouble(packet.finalRadiation);
        }

        public static RadiationSyncPacket decode(FriendlyByteBuf buffer) {
            return new RadiationSyncPacket(buffer.readDouble());
        }

        public static void handle(RadiationSyncPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.radiation.client.RadiationAtmosphereClient
                            .setTargetRadiation(packet.finalRadiation)));
            context.setPacketHandled(true);
        }
    }

    public record GeigerDataS2CPacket(double measuredRadiation, double cumulativeDose,
                                      double residualRadiationRate, RadiationZone zone) {
        public static void encode(GeigerDataS2CPacket packet, FriendlyByteBuf buffer) {
            buffer.writeDouble(packet.measuredRadiation);
            buffer.writeDouble(packet.cumulativeDose);
            buffer.writeDouble(packet.residualRadiationRate);
            buffer.writeEnum(packet.zone);
        }

        public static GeigerDataS2CPacket decode(FriendlyByteBuf buffer) {
            return new GeigerDataS2CPacket(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readEnum(RadiationZone.class));
        }

        public static void handle(GeigerDataS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.ClientGeigerData
                            .update(packet.measuredRadiation, packet.cumulativeDose, packet.residualRadiationRate, packet.zone)));
            context.setPacketHandled(true);
        }
    }

    public record ThermalGeneratorFuelSyncS2CPacket(Map<ResourceLocation, Integer> fuelEnergies) {
        public ThermalGeneratorFuelSyncS2CPacket {
            fuelEnergies = Map.copyOf(fuelEnergies);
        }

        public static void encode(ThermalGeneratorFuelSyncS2CPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.fuelEnergies.size());
            packet.fuelEnergies.forEach((itemId, energyFe) -> {
                buffer.writeResourceLocation(itemId);
                buffer.writeVarInt(energyFe);
            });
        }

        public static ThermalGeneratorFuelSyncS2CPacket decode(FriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            Map<ResourceLocation, Integer> fuelEnergies = new LinkedHashMap<>();
            for (int index = 0; index < size; index++) {
                fuelEnergies.put(buffer.readResourceLocation(), buffer.readVarInt());
            }
            return new ThermalGeneratorFuelSyncS2CPacket(fuelEnergies);
        }

        public static void handle(ThermalGeneratorFuelSyncS2CPacket packet,
                                  Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.ClientThermalGeneratorFuelData
                            .replace(packet.fuelEnergies)));
            context.setPacketHandled(true);
        }
    }

    public record CrusherBalanceSyncS2CPacket(int workFePerTick) {
        public static void encode(CrusherBalanceSyncS2CPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.workFePerTick);
        }

        public static CrusherBalanceSyncS2CPacket decode(FriendlyByteBuf buffer) {
            return new CrusherBalanceSyncS2CPacket(buffer.readVarInt());
        }

        public static void handle(CrusherBalanceSyncS2CPacket packet,
                                  Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.ClientCrusherBalanceData
                            .update(packet.workFePerTick)));
            context.setPacketHandled(true);
        }
    }

    public record CompressorBalanceSyncS2CPacket(int workFePerTick) {
        public static void encode(CompressorBalanceSyncS2CPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.workFePerTick);
        }

        public static CompressorBalanceSyncS2CPacket decode(FriendlyByteBuf buffer) {
            return new CompressorBalanceSyncS2CPacket(buffer.readVarInt());
        }

        public static void handle(CompressorBalanceSyncS2CPacket packet,
                                  Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.ClientCompressorBalanceData
                            .update(packet.workFePerTick)));
            context.setPacketHandled(true);
        }
    }

    public record AlloyFurnaceBalanceSyncS2CPacket(int workFePerTick) {
        public static void encode(AlloyFurnaceBalanceSyncS2CPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.workFePerTick);
        }

        public static AlloyFurnaceBalanceSyncS2CPacket decode(FriendlyByteBuf buffer) {
            return new AlloyFurnaceBalanceSyncS2CPacket(buffer.readVarInt());
        }

        public static void handle(AlloyFurnaceBalanceSyncS2CPacket packet,
                                  Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.ClientAlloyFurnaceBalanceData
                            .update(packet.workFePerTick)));
            context.setPacketHandled(true);
        }
    }

    public record ProcessingMachineBalanceSyncS2CPacket(int chemicalWorkFePerTick,
                                                         int industrialWorkFePerTickPerLane) {
        public static void encode(ProcessingMachineBalanceSyncS2CPacket packet,
                                  FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.chemicalWorkFePerTick);
            buffer.writeVarInt(packet.industrialWorkFePerTickPerLane);
        }

        public static ProcessingMachineBalanceSyncS2CPacket decode(FriendlyByteBuf buffer) {
            return new ProcessingMachineBalanceSyncS2CPacket(buffer.readVarInt(), buffer.readVarInt());
        }

        public static void handle(ProcessingMachineBalanceSyncS2CPacket packet,
                                  Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.ClientProcessingMachineBalanceData
                            .update(packet.chemicalWorkFePerTick,
                                    packet.industrialWorkFePerTickPerLane)));
            context.setPacketHandled(true);
        }
    }

    /** inflow: Direction.get3DDataValue() of the pipe side the fluid comes in from (one of directionMask's two), -1 if none. */
    public record FluidPipeVisualUpdate(BlockPos position, ResourceLocation fluidId,
                                        int directionMask, int inflow, boolean active, boolean isFlowing) {
        private static final ResourceLocation EMPTY_FLUID_ID = new ResourceLocation("minecraft", "empty");

        public FluidPipeVisualUpdate {
            position = position.immutable();
        }

        public static FluidPipeVisualUpdate clear(BlockPos position) {
            return new FluidPipeVisualUpdate(position, EMPTY_FLUID_ID, 0, -1, false, false);
        }
    }

    /** Where a player's fuel stream landed (FuelDispenserBlockEntity#landed checks it). */
    public record FuelSprayHitsC2SPacket(BlockPos dispenser, int nozzle, List<net.minecraft.world.phys.Vec3> points, List<net.minecraft.core.Direction> faces) {
        private static final int MAX = 16;

        public static void encode(FuelSprayHitsC2SPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBlockPos(packet.dispenser);
            buffer.writeByte(packet.nozzle);
            int n = Math.min(MAX, Math.min(packet.points.size(), packet.faces.size()));
            buffer.writeByte(n);
            for (int i = 0; i < n; i++) {
                buffer.writeDouble(packet.points.get(i).x);
                buffer.writeDouble(packet.points.get(i).y);
                buffer.writeDouble(packet.points.get(i).z);
                buffer.writeByte(packet.faces.get(i).get3DDataValue());
            }
        }

        public static FuelSprayHitsC2SPacket decode(FriendlyByteBuf buffer) {
            BlockPos dispenser = buffer.readBlockPos();
            int nozzle = buffer.readByte();
            int n = Math.min(MAX, buffer.readUnsignedByte());
            List<net.minecraft.world.phys.Vec3> points = new ArrayList<>(n);
            List<net.minecraft.core.Direction> faces = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                points.add(new net.minecraft.world.phys.Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
                faces.add(net.minecraft.core.Direction.from3DDataValue(buffer.readByte()));
            }
            return new FuelSprayHitsC2SPacket(dispenser, nozzle, points, faces);
        }

        public static void handle(FuelSprayHitsC2SPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || packet.nozzle < 0 || packet.nozzle >= com.antaurora.apofirstlight.block.FuelDispenserBlock.Nozzle.values().length) return;
                if (!player.serverLevel().isLoaded(packet.dispenser)) return;
                if (player.serverLevel().getBlockEntity(packet.dispenser) instanceof com.antaurora.apofirstlight.blockentity.FuelDispenserBlockEntity dispenser) {
                    dispenser.landed(player, com.antaurora.apofirstlight.block.FuelDispenserBlock.Nozzle.values()[packet.nozzle], packet.points, packet.faces);
                }
            });
            context.setPacketHandled(true);
        }
    }

    /** Fuel stains added or changed (whole state) and removed (ids); see fluid/FuelStainIndex. */
    public record FuelStainS2CPacket(List<com.antaurora.apofirstlight.fluid.FuelStainIndex.Stain> upserts, long[] removed) {
        static FuelStainS2CPacket of(Collection<com.antaurora.apofirstlight.fluid.FuelStainIndex.Stain> upserts, Collection<Long> removed) {
            return new FuelStainS2CPacket(List.copyOf(upserts), removed.stream().mapToLong(Long::longValue).toArray());
        }

        public static void encode(FuelStainS2CPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.upserts.size());
            for (com.antaurora.apofirstlight.fluid.FuelStainIndex.Stain s : packet.upserts) {
                buffer.writeVarLong(s.id);
                buffer.writeDouble(s.pos.x);
                buffer.writeDouble(s.pos.y);
                buffer.writeDouble(s.pos.z);
                buffer.writeByte(s.face.get3DDataValue());
                buffer.writeBoolean(s.diesel);
                buffer.writeFloat(s.size);
                buffer.writeLong(s.wet);
                buffer.writeLong(s.born);
                buffer.writeFloat(s.u0);
                buffer.writeFloat(s.u1);
                buffer.writeFloat(s.v0);
                buffer.writeFloat(s.v1);
                buffer.writeLong(s.ignite);
            }
            buffer.writeLongArray(packet.removed);
        }

        public static FuelStainS2CPacket decode(FriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            List<com.antaurora.apofirstlight.fluid.FuelStainIndex.Stain> upserts = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                com.antaurora.apofirstlight.fluid.FuelStainIndex.Stain stain = new com.antaurora.apofirstlight.fluid.FuelStainIndex.Stain(buffer.readVarLong(),
                        new net.minecraft.world.phys.Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                        net.minecraft.core.Direction.from3DDataValue(buffer.readByte()), buffer.readBoolean(), buffer.readFloat(), buffer.readLong());
                stain.born = buffer.readLong();
                stain.u0 = buffer.readFloat();
                stain.u1 = buffer.readFloat();
                stain.v0 = buffer.readFloat();
                stain.v1 = buffer.readFloat();
                stain.ignite = buffer.readLong();
                upserts.add(stain);
            }
            return new FuelStainS2CPacket(upserts, buffer.readLongArray());
        }

        public static void handle(FuelStainS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.ClientFuelStains.apply(packet.upserts, packet.removed)));
            context.setPacketHandled(true);
        }
    }

    /** A fuel container's blast: where, how strong, which fuel (client/FireFx#blast). */
    public record FuelBlastS2CPacket(Vec3 at, float power, boolean diesel) {
        public static void encode(FuelBlastS2CPacket packet, FriendlyByteBuf buffer) {
            buffer.writeDouble(packet.at.x);
            buffer.writeDouble(packet.at.y);
            buffer.writeDouble(packet.at.z);
            buffer.writeFloat(packet.power);
            buffer.writeBoolean(packet.diesel);
        }

        public static FuelBlastS2CPacket decode(FriendlyByteBuf buffer) {
            return new FuelBlastS2CPacket(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()), buffer.readFloat(), buffer.readBoolean());
        }

        public static void handle(FuelBlastS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.FireFx.blast(packet.at, packet.power, packet.diesel)));
            context.setPacketHandled(true);
        }
    }

    /** Where a bullet struck a block; {@code holed}: it made (or hit) a fuel container's hole, drawn from the leak. */
    public record BulletImpactS2CPacket(Vec3 at, net.minecraft.core.Direction face, BlockPos block, boolean holed, boolean spark) {
        public static void encode(BulletImpactS2CPacket packet, FriendlyByteBuf buffer) {
            buffer.writeDouble(packet.at.x);
            buffer.writeDouble(packet.at.y);
            buffer.writeDouble(packet.at.z);
            buffer.writeByte(packet.face.get3DDataValue());
            buffer.writeBlockPos(packet.block);
            buffer.writeBoolean(packet.holed);
            buffer.writeBoolean(packet.spark);
        }

        public static BulletImpactS2CPacket decode(FriendlyByteBuf buffer) {
            return new BulletImpactS2CPacket(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                    net.minecraft.core.Direction.from3DDataValue(buffer.readByte()), buffer.readBlockPos(), buffer.readBoolean(), buffer.readBoolean());
        }

        public static void handle(BulletImpactS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.BulletHoles.impact(packet.at, packet.face, packet.block, packet.holed, packet.spark)));
            context.setPacketHandled(true);
        }
    }

    /** Bullet holes in fuel containers: where, which way, leaking how fast, burning. {@code reset}: the whole level's list. */
    public record FuelLeakS2CPacket(List<Leak> upserts, long[] removed, boolean reset) {
        public record Leak(long id, Vec3 at, net.minecraft.core.Direction face, boolean diesel, boolean flowing, boolean burning, float speed) {}

        static FuelLeakS2CPacket of(Collection<com.antaurora.apofirstlight.fluid.FuelLeaks.Hole> holes, Collection<Long> removed, boolean reset) {
            List<Leak> leaks = new ArrayList<>(holes.size());
            for (com.antaurora.apofirstlight.fluid.FuelLeaks.Hole h : holes) leaks.add(new Leak(h.id, h.at, h.face, h.diesel, h.flowing, h.burning, h.speed));
            return new FuelLeakS2CPacket(leaks, removed.stream().mapToLong(Long::longValue).toArray(), reset);
        }

        public static void encode(FuelLeakS2CPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBoolean(packet.reset);
            buffer.writeVarInt(packet.upserts.size());
            for (Leak l : packet.upserts) {
                buffer.writeVarLong(l.id);
                buffer.writeDouble(l.at.x);
                buffer.writeDouble(l.at.y);
                buffer.writeDouble(l.at.z);
                buffer.writeByte(l.face.get3DDataValue());
                buffer.writeByte((l.diesel ? 1 : 0) | (l.flowing ? 2 : 0) | (l.burning ? 4 : 0));
                buffer.writeFloat(l.speed);
            }
            buffer.writeLongArray(packet.removed);
        }

        public static FuelLeakS2CPacket decode(FriendlyByteBuf buffer) {
            boolean reset = buffer.readBoolean();
            int size = buffer.readVarInt();
            List<Leak> leaks = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                long id = buffer.readVarLong();
                Vec3 at = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
                net.minecraft.core.Direction face = net.minecraft.core.Direction.from3DDataValue(buffer.readByte());
                int flags = buffer.readByte();
                leaks.add(new Leak(id, at, face, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, buffer.readFloat()));
            }
            return new FuelLeakS2CPacket(leaks, buffer.readLongArray(), reset);
        }

        public static void handle(FuelLeakS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.ClientFuelLeaks.apply(packet.upserts, packet.removed, packet.reset)));
            context.setPacketHandled(true);
        }
    }

    public record FluidPipeVisualS2CPacket(List<FluidPipeVisualUpdate> updates) {
        public FluidPipeVisualS2CPacket {
            updates = List.copyOf(updates);
        }

        public static void encode(FluidPipeVisualS2CPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.updates.size());
            for (FluidPipeVisualUpdate update : packet.updates) {
                buffer.writeBlockPos(update.position());
                buffer.writeBoolean(update.active());
                if (update.active()) {
                    buffer.writeResourceLocation(update.fluidId());
                    buffer.writeVarInt(update.directionMask());
                    buffer.writeByte(update.inflow());
                    buffer.writeBoolean(update.isFlowing());
                }
            }
        }

        public static FluidPipeVisualS2CPacket decode(FriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            List<FluidPipeVisualUpdate> updates = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                BlockPos position = buffer.readBlockPos();
                boolean active = buffer.readBoolean();
                updates.add(active
                        ? new FluidPipeVisualUpdate(position, buffer.readResourceLocation(),
                        buffer.readVarInt(), buffer.readByte(), true, buffer.readBoolean())
                        : FluidPipeVisualUpdate.clear(position));
            }
            return new FluidPipeVisualS2CPacket(updates);
        }

        public static void handle(FluidPipeVisualS2CPacket packet,
                                  Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.antaurora.apofirstlight.client.ClientFluidPipeVisuals
                            .apply(packet.updates)));
            context.setPacketHandled(true);
        }
    }
}
