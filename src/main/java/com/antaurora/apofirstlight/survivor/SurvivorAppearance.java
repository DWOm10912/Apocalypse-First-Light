package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

/**
 * Character creation V1 (docs/ui/character_creation_v1.md, user 2026-10-26 "用方案A"): what the B5 body looks like, a skin
 * tone (1..{@link #TONES}) and a hair style ({@link #HAIR}), and the starting outfit, a set of the four garments. The first
 * survivor's are chosen on the appearance page before the setup page; a successor's tone and hair come from its roll seed
 * and it takes over in the everyday outfit ({@link #SUCCESSOR_OUTFIT}). Only the male B5 body exists yet: a rolled female
 * successor draws it too until the female body is in.
 */
public final class SurvivorAppearance {
    public static final int TONES = 5, DEFAULT_TONE = 2;
    /** Geometry hair on the B5 head (skins/block_mesh_v2/b5/hair_*.json); bald wears none. */
    public static final List<String> HAIR = List.of("short", "buzz", "bald");
    public static final String DEFAULT_HAIR = "short";

    /** The starting garments, bit by ordinal in an outfit mask. */
    public enum Garment {
        SHIRT(AflItems.CLOTHING_SHIRT), JACKET(AflItems.CLOTHING_JACKET), JEANS(AflItems.CLOTHING_JEANS), SHOES(AflItems.CLOTHING_SHOES);

        private final Supplier<Item> item;

        Garment(Supplier<Item> item) {
            this.item = item;
        }

        public int bit() {
            return 1 << ordinal();
        }

        public Item item() {
            return item.get();
        }
    }

    public static final int FULL_OUTFIT = 0b1111;
    /** A successor's clothes: shirt, jeans and shoes. */
    public static final int SUCCESSOR_OUTFIT = Garment.SHIRT.bit() | Garment.JEANS.bit() | Garment.SHOES.bit();

    /** A body's look and starting outfit, checked into range. */
    public record Look(int tone, String hair, int outfit) {
        public Look {
            tone = tone < 1 || tone > TONES ? DEFAULT_TONE : tone;
            hair = HAIR.contains(hair) ? hair : DEFAULT_HAIR;
            outfit &= FULL_OUTFIT;
        }

        public static final Look DEFAULT = new Look(DEFAULT_TONE, DEFAULT_HAIR, FULL_OUTFIT);

        public boolean wears(Garment g) {
            return (outfit & g.bit()) != 0;
        }

        /** Into a host setup tag (Tone, Hair, Outfit) or back. */
        public void write(CompoundTag tag) {
            tag.putInt("Tone", tone);
            tag.putString("Hair", hair);
            tag.putInt("Outfit", outfit);
        }

        public static Look read(CompoundTag tag) {
            return tag.contains("Tone") ? new Look(tag.getInt("Tone"), tag.getString("Hair"), tag.getInt("Outfit")) : DEFAULT;
        }
    }

    private SurvivorAppearance() {
    }

    /** A successor's tone and hair from its roll seed (its own stream, so the name and skin rolls stay as they were). */
    static void roll(SurvivorRecord record, long seed) {
        long r = SurvivorNames.stream(seed, 777) >>> 1;
        record.tone = 1 + (int) (r % TONES);
        record.hair = HAIR.get((int) ((r / TONES) % HAIR.size()));
    }

    /** The record's tone and hair, the defaults for a survivor saved before they existed. */
    public static int tone(SurvivorRecord record) {
        return record.tone < 1 || record.tone > TONES ? DEFAULT_TONE : record.tone;
    }

    public static String hair(SurvivorRecord record) {
        return record.hair != null && HAIR.contains(record.hair) ? record.hair : DEFAULT_HAIR;
    }

    /** Puts on the outfit's garments where their slot is empty (a filled slot keeps what it holds). */
    static void dress(ServerPlayer player, int outfit) {
        for (Garment g : Garment.values()) {
            if ((outfit & g.bit()) == 0) continue;
            ItemStack stack = new ItemStack(g.item());
            switch (g) {
                case SHIRT, JACKET -> {
                    String id = g == Garment.SHIRT ? AflEquipmentSlots.BASE : AflEquipmentSlots.COAT;
                    AflEquipmentSlots.stacks(player, id).filter(s -> s.getStackInSlot(0).isEmpty()).ifPresent(s -> s.setStackInSlot(0, stack));
                }
                case JEANS, SHOES -> {
                    EquipmentSlot slot = g == Garment.JEANS ? EquipmentSlot.LEGS : EquipmentSlot.FEET;
                    if (player.getItemBySlot(slot).isEmpty()) player.setItemSlot(slot, stack);
                }
            }
        }
    }
}
