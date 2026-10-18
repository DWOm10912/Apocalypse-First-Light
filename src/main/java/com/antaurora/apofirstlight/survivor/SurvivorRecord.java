package com.antaurora.apofirstlight.survivor;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One survivor's life (docs/gameplay/survivor_death_succession_v0.md 3): identity, start and end, death cause and place,
 * corpse. A successor is rolled at the moment of the previous death and stored as PENDING, so a relog or restart can
 * never re-roll it; it becomes ALIVE when the player takes it over (respawn).
 *
 * <p>Setup (docs/gameplay/survivor_background_skills_v1_plan.md 9, 11): background, traits, body stats and growth hold
 * final values, never "background + points", so a data change never alters a living survivor. originState and
 * profession stay reserved and empty.
 */
public final class SurvivorRecord {
    public enum State { PENDING, ALIVE, DEAD }

    /** A rolled successor's sex: it picks the given-name pool and the skin (and with it the arm model). */
    public enum Sex { FEMALE, MALE }

    public static final long DAY_TICKS = 24000L;

    final UUID id;
    /** Career index inside the player's lineage: the n-th survivor this player has played in this world, from 1. */
    final int lifeIndex;
    /** Not final only for the first survivor's one-time name choice (SurvivorIdentity); fixed once {@link #named}. */
    String firstName;
    String lastName;
    /**
     * Whether the name is final. A rolled successor is named at the roll; the first survivor of a lineage waits for the
     * player's one-time choice (the account name or a nickname, docs/gameplay/survivor_death_succession_v0.md 13).
     */
    boolean named = true;
    /** The roll that made this survivor (name, spawn plan); kept for audits. */
    final long rollSeed;

    /**
     * Appearance (SurvivorSkins), fixed when the survivor is rolled and never re-rolled: a pool skin id and its arm
     * model, or ACCOUNT for a survivor who looks like the account (the first one).
     */
    String skin = SurvivorSkins.ACCOUNT;
    SurvivorSkins.Arms arms = SurvivorSkins.Arms.ACCOUNT;
    /**
     * Rolled with the successor, before its name and skin; null for the first survivor (the player as they arrive, never
     * guessed from the account) and for successors saved before the sex roll existed.
     */
    @Nullable Sex sex;

    // Reserved for later systems; never filled yet.
    @Nullable String originState;
    @Nullable String profession;

    /**
     * {@link SurvivorSetup#CHOOSING} until the first survivor's setup is confirmed, then {@link SurvivorSetup#DONE}; null
     * only for a successor rolled before setups existed (set up at its takeover).
     */
    @Nullable String setup;
    /** The start scene chosen for this survivor ({@link SurvivorScenes}); where it actually started is spawnSource. */
    @Nullable String scene;
    /** Background id ({@code afl:<name>}); for display only, an unknown id changes nothing. */
    @Nullable String background;
    /** Every trait: the background's and the chosen ones. */
    final List<String> traits = new ArrayList<>();
    /** Body stats by id: level (start + growth) and progress inside it. Missing = the stat's default. */
    final Map<String, Progress> body = new LinkedHashMap<>();
    /** Growth items by id (firearms...), same shape. Unknown ids are kept but do nothing. */
    final Map<String, Progress> growth = new LinkedHashMap<>();

    /** A level and the experience inside it. */
    public static final class Progress {
        int level;
        int xp;

        Progress(int level, int xp) {
            this.level = level;
            this.xp = xp;
        }

        public int level() {
            return level;
        }

        public int xp() {
            return xp;
        }
    }

    State state;
    long startGameTime = -1L;
    long startDayTime = -1L;
    long deathGameTime = -1L;
    long deathDayTime = -1L;
    /** The death message as Component JSON, the survivor's name in place of the account name. */
    @Nullable String deathCauseJson;
    /** The damage type's message id (e.g. "mob", "fall", "apocalypse_firstlight.native_bullet"), or "unknown". */
    @Nullable String deathCauseId;
    @Nullable String deathDimension;
    @Nullable BlockPos deathPos;
    @Nullable UUID corpseId;

    /** Successor spawn plan, chosen at the previous death without loading chunks; resolved on the ground at takeover. */
    boolean hasSpawnPlan;
    int spawnPlanX;
    int spawnPlanZ;
    /** Where this survivor actually started (resolved at takeover), or null for the first survivor of a lineage. */
    @Nullable String spawnDimension;
    @Nullable BlockPos spawnPos;
    /** How the start position was found: a provider id, "fallback" (world spawn rules) or "initial". */
    @Nullable String spawnSource;

    SurvivorRecord(UUID id, int lifeIndex, String firstName, String lastName, long rollSeed, State state) {
        this.id = id;
        this.lifeIndex = lifeIndex;
        this.firstName = firstName;
        this.lastName = lastName;
        this.rollSeed = rollSeed;
        this.state = state;
    }

    public UUID id() {
        return id;
    }

    public int lifeIndex() {
        return lifeIndex;
    }

    /** "First Last", or the single name of a survivor named after the account. */
    public String fullName() {
        return lastName.isEmpty() ? firstName : firstName + " " + lastName;
    }

    public String skin() {
        return skin;
    }

    public SurvivorSkins.Arms arms() {
        return arms;
    }

    void setAppearance(SurvivorSkins.Skin look) {
        skin = look.id();
        arms = look.arms();
    }

    public State state() {
        return state;
    }

    @Nullable
    public String setup() {
        return setup;
    }

    @Nullable
    public String background() {
        return background;
    }

    public List<String> traits() {
        return Collections.unmodifiableList(traits);
    }

    public Map<String, Progress> body() {
        return Collections.unmodifiableMap(body);
    }

    public Map<String, Progress> growth() {
        return Collections.unmodifiableMap(growth);
    }

    /** Whole in-game days between the start and the death (or now), from the world's day time; never negative. */
    public double daysSurvived(long nowDayTime) {
        if (startDayTime < 0) return 0;
        long end = deathDayTime >= 0 ? deathDayTime : nowDayTime;
        return Math.max(0L, end - startDayTime) / (double) DAY_TICKS;
    }

    public Component deathCause() {
        if (deathCauseJson != null) {
            try {
                Component parsed = Component.Serializer.fromJson(deathCauseJson);
                if (parsed != null) return parsed;
            } catch (RuntimeException ignored) {
                // a damaged entry falls back to the generic line below
            }
        }
        return Component.translatable("survivor.apocalypse_firstlight.cause.unknown");
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putInt("Life", lifeIndex);
        tag.putString("First", firstName);
        tag.putString("Last", lastName);
        tag.putBoolean("Named", named);
        tag.putLong("Roll", rollSeed);
        tag.putString("State", state.name());
        tag.putString("Skin", skin);
        tag.putString("Arms", arms.id());
        if (sex != null) tag.putString("Sex", sex.name());
        putString(tag, "OriginState", originState);
        putString(tag, "Background", background);
        putString(tag, "Profession", profession);
        putString(tag, "Setup", setup);
        putString(tag, "Scene", scene);
        if (!body.isEmpty()) tag.put("Body", saveProgress(body));
        if (!growth.isEmpty()) tag.put("Growth", saveProgress(growth));
        if (!traits.isEmpty()) {
            ListTag list = new ListTag();
            for (String trait : traits) list.add(StringTag.valueOf(trait));
            tag.put("Traits", list);
        }
        tag.putLong("StartGame", startGameTime);
        tag.putLong("StartDay", startDayTime);
        tag.putLong("DeathGame", deathGameTime);
        tag.putLong("DeathDay", deathDayTime);
        putString(tag, "Cause", deathCauseJson);
        putString(tag, "CauseId", deathCauseId);
        putString(tag, "DeathDim", deathDimension);
        if (deathPos != null) tag.putLong("DeathPos", deathPos.asLong());
        if (corpseId != null) tag.putUUID("Corpse", corpseId);
        if (hasSpawnPlan) {
            tag.putInt("PlanX", spawnPlanX);
            tag.putInt("PlanZ", spawnPlanZ);
        }
        putString(tag, "SpawnDim", spawnDimension);
        if (spawnPos != null) tag.putLong("SpawnPos", spawnPos.asLong());
        putString(tag, "SpawnSource", spawnSource);
        return tag;
    }

    @Nullable
    static SurvivorRecord load(CompoundTag tag) {
        if (!tag.hasUUID("Id")) return null;
        State state;
        try {
            state = State.valueOf(tag.getString("State"));
        } catch (IllegalArgumentException e) {
            state = State.DEAD;
        }
        SurvivorRecord r = new SurvivorRecord(tag.getUUID("Id"), Math.max(1, tag.getInt("Life")), tag.getString("First"),
                tag.getString("Last"), tag.getLong("Roll"), state);
        // saved before the name choice existed: the name it has is final
        r.named = !tag.contains("Named", Tag.TAG_BYTE) || tag.getBoolean("Named");
        if (tag.contains("Skin", Tag.TAG_STRING)) {
            r.skin = tag.getString("Skin");
            r.arms = SurvivorSkins.Arms.byId(tag.getString("Arms"));
        } else if (r.lifeIndex > 1) {
            // a successor saved before appearances existed: its look follows from its roll seed, as the roll of that
            // time would have given it
            r.setAppearance(SurvivorSkins.legacyRoll(r.rollSeed));
        }
        if (tag.contains("Sex", Tag.TAG_STRING)) {
            try {
                r.sex = Sex.valueOf(tag.getString("Sex"));
            } catch (IllegalArgumentException ignored) {
                // an unknown value stays unset, like an older save
            }
        }
        r.originState = getString(tag, "OriginState");
        r.background = getString(tag, "Background");
        r.profession = getString(tag, "Profession");
        for (Tag t : tag.getList("Traits", Tag.TAG_STRING)) r.traits.add(t.getAsString());
        // V0 reserved "Body" as a string and never wrote it: only the compound form is read
        loadProgress(tag.getCompound("Body"), r.body);
        loadProgress(tag.getCompound("Growth"), r.growth);
        r.scene = getString(tag, "Scene");
        if (tag.contains("Setup", Tag.TAG_STRING)) {
            r.setup = tag.getString("Setup");
        } else if (state != State.PENDING) {
            // a V0 survivor (section 9): an ordinary person with default stats, never asked
            r.setup = SurvivorSetup.DONE;
            if (r.background == null) r.background = SurvivorSetup.ORDINARY;
        }
        r.startGameTime = tag.getLong("StartGame");
        r.startDayTime = tag.getLong("StartDay");
        r.deathGameTime = tag.getLong("DeathGame");
        r.deathDayTime = tag.getLong("DeathDay");
        r.deathCauseJson = getString(tag, "Cause");
        r.deathCauseId = getString(tag, "CauseId");
        r.deathDimension = getString(tag, "DeathDim");
        r.deathPos = tag.contains("DeathPos", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("DeathPos")) : null;
        r.corpseId = tag.hasUUID("Corpse") ? tag.getUUID("Corpse") : null;
        r.hasSpawnPlan = tag.contains("PlanX", Tag.TAG_INT);
        r.spawnPlanX = tag.getInt("PlanX");
        r.spawnPlanZ = tag.getInt("PlanZ");
        r.spawnDimension = getString(tag, "SpawnDim");
        r.spawnPos = tag.contains("SpawnPos", Tag.TAG_LONG) ? BlockPos.of(tag.getLong("SpawnPos")) : null;
        r.spawnSource = getString(tag, "SpawnSource");
        return r;
    }

    private static CompoundTag saveProgress(Map<String, Progress> map) {
        CompoundTag out = new CompoundTag();
        map.forEach((id, p) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Lv", p.level);
            entry.putInt("Xp", p.xp);
            out.put(id, entry);
        });
        return out;
    }

    private static void loadProgress(CompoundTag tag, Map<String, Progress> into) {
        for (String id : tag.getAllKeys()) {
            CompoundTag entry = tag.getCompound(id);
            into.put(id, new Progress(entry.getInt("Lv"), entry.getInt("Xp")));
        }
    }

    private static void putString(CompoundTag tag, String key, @Nullable String value) {
        if (value != null) tag.putString(key, value);
    }

    @Nullable
    private static String getString(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : null;
    }
}
