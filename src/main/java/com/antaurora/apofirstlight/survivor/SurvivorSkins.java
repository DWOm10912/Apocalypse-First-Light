package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The AFL survivor appearance pool (docs/gameplay/survivor_death_succession_v0.md 13). A successor's look is chosen when
 * the successor is rolled, stored in its record (skin id and arm model) and never re-rolled; the first survivor of a
 * lineage keeps the account's own skin ({@link #ACCOUNT}). Textures are plain 64x64 player skins under
 * {@code textures/entity/survivor/<id>.png}.
 *
 * <p>Pool: one skin per roster person (SurvivorRoster; sixty, women on slim arms and men on classic arms, painted by
 * tools/build-survivor-skins.mjs). A skin shows only the base clothing layer (shirt, trousers, footwear); outer garments,
 * headwear and other gear are future modular equipment. The two prototype test skins (tools/build-survivor-test-skins.mjs)
 * are no longer given out but stay in the pool, since saves made with them store their ids. Ids stored in saves must stay
 * valid or map to a replacement.
 */
public final class SurvivorSkins {
    /** Skin id and arm model of a survivor who looks like the account (the first survivor). */
    public static final String ACCOUNT = "account";

    public enum Arms {
        /** The account's own model (follows the account skin). */
        ACCOUNT,
        /** 4 px arms (vanilla "default"). */
        CLASSIC,
        /** 3 px arms (vanilla "slim"). */
        SLIM;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The vanilla model name for the player renderers ("default" / "slim"); null for the account's own. */
        @Nullable
        public String modelName() {
            return switch (this) {
                case CLASSIC -> "default";
                case SLIM -> "slim";
                case ACCOUNT -> null;
            };
        }

        public static Arms byId(String id) {
            for (Arms arms : values()) {
                if (arms.id().equals(id)) return arms;
            }
            return ACCOUNT;
        }
    }

    public record Skin(String id, Arms arms) {
    }

    private static final List<Skin> LEGACY = List.of(new Skin("test_classic", Arms.CLASSIC), new Skin("test_slim", Arms.SLIM));
    private static final List<Skin> POOL = pool();
    /** Shown for an id no longer in the pool: a classic-arm skin, which also reads right on a slim model. */
    private static final String FALLBACK = "m01";

    private SurvivorSkins() {
    }

    private static List<Skin> pool() {
        List<Skin> pool = new ArrayList<>();
        for (SurvivorRoster.Person person : SurvivorRoster.PEOPLE) pool.add(of(person));
        pool.addAll(LEGACY);
        return List.copyOf(pool);
    }

    /** A roster person's skin: the person's id, slim arms for women and classic arms for men. */
    static Skin of(SurvivorRoster.Person person) {
        return new Skin(person.id(), person.sex() == SurvivorRecord.Sex.FEMALE ? Arms.SLIM : Arms.CLASSIC);
    }

    /**
     * The look of a successor saved before appearances existed, as the prototype roll gave it (the two test skins), so
     * an old save keeps the look it would have had.
     */
    static Skin legacyRoll(long seed) {
        return LEGACY.get((int) Math.floorMod(SurvivorNames.stream(seed, 7), (long) LEGACY.size()));
    }

    @Nullable
    public static Skin byId(String id) {
        for (Skin skin : POOL) {
            if (skin.id().equals(id)) return skin;
        }
        return null;
    }

    /**
     * The texture of a pool skin; an id no longer in the pool (a removed asset) shows {@link #FALLBACK}, never the
     * account's.
     */
    public static ResourceLocation texture(String id) {
        String known = byId(id) != null ? id : FALLBACK;
        return ResourceLocation.fromNamespaceAndPath(ApocalypseFirstLight.MOD_ID, "textures/entity/survivor/" + known + ".png");
    }
}
