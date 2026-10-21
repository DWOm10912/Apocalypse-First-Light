package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.health.BodyPart;
import com.antaurora.apofirstlight.health.ClientHealth;
import com.antaurora.apofirstlight.health.HealthPackets;
import com.antaurora.apofirstlight.health.Wounds;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Skin wound marks V1 (docs/gameplay/skin_wound_marks_v1_plan.md): every drawn player with wounds (HealthPackets.Marks#skin)
 * or lightning's fern (#fern) gets a 64 x 64 texture laid out as the skin, drawn over the bare skin and under its second
 * layer, the clothes and the gear, in skin pixels: lightning burns, metal burns in the shape of what burned them, scratches,
 * the swelling of an infection, pus, the gauze patch on a dressed head, chest or belly, the Lichtenberg fern. A dressed limb
 * draws nothing (BandageWrapRenderer's gauze covers the band its marks sit in). Fixed places: each wound's seed. Fading: its
 * healed sixteenths. The texture is redrawn only when the marks change; one unused for 10 s is released.
 * <p>Drawn by the skeleton (PlayerSkeleton#draw), the vanilla body ({@link Layer}), the first-person arms
 * (NativePlayerArmRenderer) and the inventory's body chart (BodySilhouette, the front faces).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class WoundSkin {
    private static final int SIZE = 64;
    private static final long KEEP_MS = 10_000;

    // colours (0xRRGGBB)
    private static final int CHAR = 0x3A2218, BURN_RED = 0xB0382C, BURN_PINK = 0xC8766A, SCAR = 0xC09080;
    private static final int METAL_CORE = 0x48281A, METAL_EDGE = 0xA4362A, METAL_OLD = 0x9A6A52;
    private static final int CUT = 0x92241E, SCAB = 0x5A261C, CUT_OLD = 0xB07468;
    private static final int SWELL = 0xC4544A, PUS = 0xE4D7A0, GAUZE = 0xE9E5DB, GAUZE_WEAVE = 0xD9D2C4, GAUZE_EDGE = 0xC9C1B2;
    private static final int FERN = 0xBC463A;

    private static final class Entry {
        final ResourceLocation id;
        final DynamicTexture texture;
        List<HealthPackets.SkinMark> skin;
        HealthPackets.Fern fern;
        boolean slim;
        long used;

        Entry(UUID player) {
            id = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "wound_skin/" + player);
            texture = new DynamicTexture(SIZE, SIZE, true);
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
    }

    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();

    private WoundSkin() {
    }

    /** This player's wound texture, drawn and uploaded if its marks changed; null when it has none (or is invisible). */
    @Nullable
    public static ResourceLocation texture(Player player, boolean slim) {
        if (player.isInvisible()) return null;
        HealthPackets.Marks marks = ClientHealth.marks(player.getUUID());
        List<HealthPackets.SkinMark> skin = visible(marks.skin());
        if (skin.isEmpty() && marks.fern().strength() == 0) return null;
        Entry e = ENTRIES.get(player.getUUID());
        if (e == null) ENTRIES.put(player.getUUID(), e = new Entry(player.getUUID()));
        if (e.skin == null || e.slim != slim || !e.skin.equals(skin) || !e.fern.equals(marks.fern())) {
            e.skin = skin;
            e.fern = marks.fern();
            e.slim = slim;
            NativeImage image = e.texture.getPixels();
            if (image != null) {
                paint(image, skin, marks.fern(), slim);
                e.texture.upload();
            }
        }
        e.used = System.currentTimeMillis();
        return e.id;
    }

    /** The marks drawn: all but a dressed limb's, which the gauze covers. */
    private static List<HealthPackets.SkinMark> visible(List<HealthPackets.SkinMark> all) {
        List<HealthPackets.SkinMark> out = new ArrayList<>(all.size());
        for (HealthPackets.SkinMark m : all) if (!(m.bandaged() && m.part().limb())) out.add(m);
        return out;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ENTRIES.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Entry>> it = ENTRIES.entrySet().iterator(); it.hasNext(); ) {
            Entry e = it.next().getValue();
            if (now - e.used < KEEP_MS && Minecraft.getInstance().level != null) continue;
            Minecraft.getInstance().getTextureManager().release(e.id);
            it.remove();
        }
    }

    // ---------------------------------------------------------------- the three ways it is drawn on a body

    /** The skeleton's skin, a second time over the bare skin with this texture: the consumer for it, or null. */
    @Nullable
    public static VertexConsumer consumer(Player player, boolean slim, @Nullable MultiBufferSource buffers) {
        if (buffers == null) return null;
        ResourceLocation id = texture(player, slim);
        return id == null ? null : buffers.getBuffer(RenderType.entityTranslucent(id));
    }

    /** The vanilla body (swimming, sleeping, flying with elytra: the skeleton does not draw it). */
    public static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (BandageWrapRenderer.skeletonDrewThisFrame(player)) return;
            VertexConsumer c = consumer(player, "slim".equals(player.getModelName()), buffers);
            if (c == null) return;
            PlayerModel<AbstractClientPlayer> m = getParentModel();
            int overlay = LivingEntityRenderer.getOverlayCoords(player, 0F);
            // the base boxes only: the second layer (hat, jacket, sleeves, trousers) is its own parts and stays on top
            for (ModelPart part : new ModelPart[]{m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg})
                part.render(pose, c, light, overlay);
        }
    }

    // ---------------------------------------------------------------- painting

    /**
     * A part's skin as a band round it: the side faces in a row on the 64 x 64 skin (west, front, east, back: vanilla's
     * box unfolding, so the band wraps round the limb), rows from the top (shoulder, hip, crown) down.
     */
    private record Band(int u, int v, int around, int rows) {}

    private static Band band(BodyPart part, boolean slim) {
        int a = slim ? 3 : 4;
        return switch (part) {
            case HEAD -> new Band(0, 8, 32, 8);
            case CHEST, BELLY -> new Band(16, 20, 24, 12);
            case RIGHT_ARM -> new Band(40, 20, 8 + 2 * a, 12);
            case LEFT_ARM -> new Band(32, 52, 8 + 2 * a, 12);
            case RIGHT_LEG -> new Band(0, 20, 16, 12);
            default -> new Band(16, 52, 16, 12);
        };
    }

    /**
     * The rows a part's marks sit in: an arm's 1.5..5.5 px and a leg's 2..6 px from the hand or foot (where the
     * dressing goes, BandageWrapRenderer ARM_BAND / LEG_BAND), the chest's upper six rows of the body, the belly's lower six.
     */
    private static int[] rows(BodyPart part) {
        return switch (part) {
            case HEAD -> new int[]{0, 7};
            case CHEST -> new int[]{0, 5};
            case BELLY -> new int[]{6, 11};
            case RIGHT_ARM, LEFT_ARM -> new int[]{6, 10};
            default -> new int[]{6, 9};
        };
    }

    /** A canvas of straight-alpha ARGB over the 64 x 64, written to the image at the end. */
    private static final class Canvas {
        final int[] argb = new int[SIZE * SIZE];

        void over(int x, int y, int rgb, int alpha) {
            if (x < 0 || y < 0 || x >= SIZE || y >= SIZE || alpha <= 0) return;
            int i = y * SIZE + x, d = argb[i], da = d >>> 24;
            float a = Math.min(255, alpha) / 255F, b = da / 255F * (1 - a), out = a + b;
            if (out <= 0) return;
            int r = mix((rgb >> 16) & 255, (d >> 16) & 255, a, b, out), g = mix((rgb >> 8) & 255, (d >> 8) & 255, a, b, out),
                    bl = mix(rgb & 255, d & 255, a, b, out);
            argb[i] = Math.round(out * 255) << 24 | r << 16 | g << 8 | bl;
        }

        private static int mix(int s, int d, float a, float b, float out) {
            return Math.round((s * a + d * b) / out);
        }
    }

    /** Where a mark goes on its part's band: column (wrapping) and row. */
    private record Spot(Band band, int col, int row) {
        void put(Canvas c, int dc, int dr, int rgb, int alpha) {
            int col = Math.floorMod(this.col + dc, band.around), row = this.row + dr;
            if (row < 0 || row >= band.rows) return;
            c.over(band.u + col, band.v + row, rgb, alpha);
        }
    }

    static void paint(NativeImage image, List<HealthPackets.SkinMark> skin, HealthPackets.Fern fern, boolean slim) {
        int[] argb = canvas(skin, fern, slim);
        for (int y = 0; y < SIZE; y++)
            for (int x = 0; x < SIZE; x++) {
                int c = argb[y * SIZE + x];
                // NativeImage#setPixelRGBA takes ABGR
                image.setPixelRGBA(x, y, (c & 0xFF000000) | (c & 0xFF) << 16 | (c & 0xFF00) | (c >> 16) & 0xFF);
            }
    }

    /** The marks as straight-alpha ARGB rows (also tools/wound-skin offline check). */
    static int[] canvas(List<HealthPackets.SkinMark> skin, HealthPackets.Fern fern, boolean slim) {
        Canvas c = new Canvas();
        if (fern.strength() > 0 && fern.part() != null) fern(c, fern, slim);
        // the gauze pads first: a pad covers its own wound, not one beside it left open
        for (HealthPackets.SkinMark m : skin) if (m.bandaged()) mark(c, m, slim);
        for (HealthPackets.SkinMark m : skin) if (!m.bandaged()) mark(c, m, slim);
        return c.argb;
    }

    private static void mark(Canvas c, HealthPackets.SkinMark m, boolean slim) {
        Random r = new Random(m.seed());
        Band band = band(m.part(), slim);
        List<int[]> shape = new ArrayList<>();   // {dc, dr, rgb, alpha} round the spot
        Spot spot;
        float h = m.healed() / 15F;
        boolean infected = m.stage() != Wounds.Stage.SCRATCH;
        // an infection's fading follows its recovery once cleaned; the wound under it is drawn fresh
        float wound = infected ? 0F : h;
        if (m.kind() == Wounds.Kind.DEEP_BURN) {
            spot = metal(shape, m.part(), band, r, wound);
        } else if (m.kind() == Wounds.Kind.BURN) {
            spot = place(m.part(), band, r, 1, 1);
            burn(shape, wound);
        } else {
            spot = place(m.part(), band, r, 3, 2);
            scratch(shape, r, wound);
        }
        if (m.part() == BodyPart.HEAD && m.kind() == Wounds.Kind.BURN && r.nextInt(5) < 3) {
            // a lightning burn's way in at the crown: the head's top face (8, 0)
            spot = new Spot(new Band(8, 0, 8, 8), 2 + r.nextInt(4), 2 + r.nextInt(4));
        }
        if (m.bandaged()) {
            gauze(c, spot, shape);   // a dressed head, chest or belly (a dressed limb is left out): the pad, the wound under it
            return;
        }
        if (infected) {
            float fade = 1F - 0.85F * h;
            swell(c, spot, shape, fade);
        }
        for (int[] p : shape) spot.put(c, p[0], p[1], p[2], p[3]);
        if (m.stage() == Wounds.Stage.SUPPURATION) {
            // pus where the wound is deepest: its middle, and beside it on a long one
            int[] mid = centre(shape);
            spot.put(c, mid[0], mid[1], PUS, 235);
            if (shape.size() > 9) spot.put(c, mid[0] + 1, mid[1], PUS, 190);
        }
    }

    /**
     * A spot on the part's band: mostly the front or back (the chest and belly), anywhere round a limb or the head, in
     * the part's rows, leaving room for the mark ({@code across} x {@code down}).
     */
    private static Spot place(BodyPart part, Band band, Random r, int across, int down) {
        int[] rows = rows(part);
        int row = rows[0] + 1 + r.nextInt(Math.max(1, rows[1] - rows[0] - down - 1 + 1));
        row = Math.min(row, rows[1] - down);
        int col;
        if (part == BodyPart.CHEST || part == BodyPart.BELLY) {
            // the front (4..11) or the back (16..23), off the edges
            col = (r.nextInt(5) < 3 ? 5 : 17) + r.nextInt(Math.max(1, 6 - across));
        } else {
            col = r.nextInt(band.around);
        }
        return new Spot(band, col, Math.max(rows[0], row));
    }

    /** A lightning burn: a charred point, red round it, a pink halo; healing: red, pink, a pale scar, gone. */
    private static void burn(List<int[]> shape, float h) {
        int core = h < 0.4F ? CHAR : h < 0.7F ? 0x7A3328 : SCAR;
        int coreA = h < 0.7F ? 255 : Math.round(230 - 160 * (h - 0.7F) / 0.3F);
        int ring = h < 0.4F ? BURN_RED : BURN_PINK;
        int ringA = h < 0.4F ? 235 : h < 0.7F ? 190 : Math.round(150 * (1 - (h - 0.7F) / 0.3F));
        int halo = Math.round((h < 0.4F ? 120 : 80) * (1 - h));
        for (int[] d : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) shape.add(new int[]{d[0], d[1], BURN_PINK, halo});
        shape.add(new int[]{0, -1, ring, ringA});
        shape.add(new int[]{-1, 0, ring, ringA});
        shape.add(new int[]{0, 0, core, coreA});
        shape.add(new int[]{1, 0, ring, ringA});
        shape.add(new int[]{0, 1, ring, ringA});
    }

    /**
     * A metal burn in the shape of what was hot (LightningInjury#hotMetal): a long gun on the back (the chest's back, a
     * diagonal strip along the sling's line), the holstered pistol on the right thigh's outside (an upright oblong), the
     * rig's magazines on the belly's front (one or two small uprights). Dark core, red edge; heals to light brown.
     */
    private static Spot metal(List<int[]> shape, BodyPart part, Band band, Random r, float h) {
        int core = h < 0.5F ? METAL_CORE : METAL_OLD;
        int coreA = h < 0.75F ? 255 : Math.round(255 - 150 * (h - 0.75F) / 0.25F);
        int edge = h < 0.5F ? METAL_EDGE : BURN_PINK;
        int edgeA = h < 0.5F ? 220 : Math.round(170 * (1 - h) / 0.5F);
        List<int[]> cores = new ArrayList<>();
        Spot spot;
        switch (part) {
            case CHEST -> {
                // the back (16..23): from one shoulder blade down toward the other side
                boolean down = r.nextBoolean();
                spot = new Spot(band, 17, 0);
                for (int i = 0; i < 6; i++) cores.add(new int[]{down ? i : 5 - i, i});
            }
            case RIGHT_LEG -> {
                // the outside of the right thigh (the band's west face, 0..3)
                spot = new Spot(band, 1, 6);
                for (int i = 0; i < 4; i++) {
                    cores.add(new int[]{0, i});
                    cores.add(new int[]{1, i});
                }
            }
            case BELLY -> {
                // the front (4..11), one or two magazines side by side
                int n = 1 + r.nextInt(2), first = 5 + r.nextInt(n == 1 ? 6 : 3);
                spot = new Spot(band, first, 7);
                for (int k = 0; k < n; k++) for (int i = 0; i < 3; i++) cores.add(new int[]{k * 3, i});
            }
            default -> {
                spot = place(part, band, r, 2, 2);
                cores.add(new int[]{0, 0});
                cores.add(new int[]{1, 0});
                cores.add(new int[]{0, 1});
                cores.add(new int[]{1, 1});
            }
        }
        for (int[] p : cores)
            for (int dc = -1; dc <= 1; dc++)
                for (int dr = -1; dr <= 1; dr++) {
                    if (Math.abs(dc) + Math.abs(dr) != 1 || contains(cores, p[0] + dc, p[1] + dr) || contains(shape, p[0] + dc, p[1] + dr)) continue;
                    shape.add(new int[]{p[0] + dc, p[1] + dr, edge, edgeA});
                }
        for (int[] p : cores) shape.add(new int[]{p[0], p[1], core, coreA});
        return spot;
    }

    /** Three parallel claw lines, three pixels each, slanting; fresh red, a dark scab, a pale line, gone. */
    private static void scratch(List<int[]> shape, Random r, float h) {
        int rgb = h < 0.3F ? CUT : h < 0.7F ? SCAB : CUT_OLD;
        int a = h < 0.7F ? 245 : Math.round(220 - 150 * (h - 0.7F) / 0.3F);
        boolean right = r.nextBoolean();
        for (int k = 0; k < 3; k++)
            for (int i = 0; i < 3; i++) shape.add(new int[]{2 * k + (right ? i : 2 - i) - 2, i, rgb, i == 1 ? a : Math.round(a * 0.85F)});
    }

    /** An infection's red swelling: a ring one and two pixels out round the wound, under it. */
    private static void swell(Canvas c, Spot spot, List<int[]> shape, float fade) {
        for (int ring = 2; ring >= 1; ring--) {
            int alpha = Math.round((ring == 1 ? 165 : 85) * fade);
            for (int[] p : shape)
                for (int dc = -ring; dc <= ring; dc++)
                    for (int dr = -ring; dr <= ring; dr++) {
                        if (Math.max(Math.abs(dc), Math.abs(dr)) != ring || contains(shape, p[0] + dc, p[1] + dr)) continue;
                        spot.put(c, p[0] + dc, p[1] + dr, SWELL, alpha);
                    }
        }
        for (int[] p : shape) spot.put(c, p[0], p[1], SWELL, Math.round(120 * fade));
    }

    /** The gauze pad over a dressed wound on the head or trunk: the wound's box and a pixel round it, a woven look, a taped edge. */
    private static void gauze(Canvas c, Spot spot, List<int[]> shape) {
        int c0 = Integer.MAX_VALUE, c1 = Integer.MIN_VALUE, r0 = Integer.MAX_VALUE, r1 = Integer.MIN_VALUE;
        for (int[] p : shape) {
            c0 = Math.min(c0, p[0]);
            c1 = Math.max(c1, p[0]);
            r0 = Math.min(r0, p[1]);
            r1 = Math.max(r1, p[1]);
        }
        c0--;
        c1++;
        r0--;
        r1++;
        for (int dc = c0; dc <= c1; dc++)
            for (int dr = r0; dr <= r1; dr++) {
                boolean edge = dc == c0 || dc == c1 || dr == r0 || dr == r1;
                spot.put(c, dc, dr, edge ? GAUZE_EDGE : ((dc + dr) & 1) == 0 ? GAUZE : GAUZE_WEAVE, 255);
            }
    }

    private static int[] centre(List<int[]> cells) {
        float c = 0, r = 0;
        for (int[] p : cells) {
            c += p[0];
            r += p[1];
        }
        return new int[]{Math.round(c / cells.size()), Math.round(r / cells.size())};
    }

    private static boolean contains(List<int[]> cells, int dc, int dr) {
        for (int[] p : cells) if (p[0] == dc && p[1] == dr) return true;
        return false;
    }

    // ---------------------------------------------------------------- the Lichtenberg fern

    /**
     * Lightning's fern-like mark (the plan's 4.3): a one-pixel red trunk from where the current came in, a row a step, with
     * thin shoots slanting off to alternate sides (and shoots off those), fainter the further out; all of it fades as
     * {@code strength} drops. An arm: up the upper arm (above the dressing's band), then from that shoulder down over the
     * chest and the back toward the middle. The head: down from the neck, front and back. A leg: up the belly from that
     * hip. The chest or belly: up and down from a point on it.
     */
    private static void fern(Canvas c, HealthPackets.Fern fern, boolean slim) {
        Random r = new Random(fern.seed());
        int alpha = 45 + Math.round(185 * fern.strength() / 15F);
        Band body = band(BodyPart.CHEST, slim);
        int[] all = {0, 11};
        switch (fern.part()) {
            case RIGHT_ARM, LEFT_ARM -> {
                boolean right = fern.part() == BodyPart.RIGHT_ARM;
                Band arm = band(fern.part(), slim);
                int a = slim ? 3 : 4;
                // the outside (the right arm's west face, the left's east) and the front, from the band's top up
                branch(c, arm, new int[]{0, 5}, right ? 1 : 4 + a + 2, 5, -1, 0, r, alpha, 0, 6);
                branch(c, arm, new int[]{0, 5}, 4 + a / 2, 5, -1, 0, r, alpha, 0, 6);
                // the chest's front (4..11) and the back (16..23) from that shoulder, leaning to the middle
                branch(c, body, all, right ? 5 : 10, 0, 1, right ? 1 : -1, r, alpha, 0, 10);
                branch(c, body, all, right ? 22 : 17, 0, 1, right ? -1 : 1, r, alpha, 0, 9);
            }
            case HEAD -> {
                branch(c, body, all, 7 + r.nextInt(2), 0, 1, 0, r, alpha, 0, 10);
                branch(c, body, all, 19 + r.nextInt(2), 0, 1, 0, r, alpha, 0, 9);
            }
            case RIGHT_LEG, LEFT_LEG -> {
                boolean right = fern.part() == BodyPart.RIGHT_LEG;
                branch(c, body, all, right ? 6 : 9, 11, -1, 0, r, alpha, 0, 10);
                branch(c, body, all, right ? 21 : 18, 11, -1, 0, r, alpha, 0, 8);
            }
            default -> {
                int row = fern.part() == BodyPart.CHEST ? 3 : 8, col = 6 + r.nextInt(4);
                branch(c, body, all, col, row, -1, 0, r, alpha, 0, 5);
                branch(c, body, all, col, row + 1, 1, 0, r, alpha, 0, 6);
            }
        }
    }

    /**
     * One line of the fern, {@code length} pixels along {@code dir} (rows) within {@code rows}. The trunk (depth 0) takes
     * a row each step and now and then a pixel toward {@code lean} (0: either way); a shoot goes a pixel toward
     * {@code lean} each step and a row every other. Shoots grow off alternate sides, shorter and fainter each depth.
     */
    private static void branch(Canvas c, Band band, int[] rows, int col, int row, int dir, int lean, Random r, int alpha, int depth, int length) {
        Spot at = new Spot(band, 0, 0);
        int a = Math.round(alpha * (1F - 0.25F * depth));
        int side = r.nextBoolean() ? 1 : -1;
        for (int i = 0; i < length; i++) {
            if (row < rows[0] || row > rows[1]) return;
            at.put(c, col, row, FERN, a);
            if (depth < 2 && i > 0 && r.nextInt(depth == 0 ? 2 : 3) == 0) {
                branch(c, band, rows, col + side, row, dir, side, r, alpha, depth + 1, Math.max(1, (depth == 0 ? 4 : 2) - r.nextInt(2)));
                side = -side;
            }
            if (depth == 0) {
                row += dir;
                if (r.nextInt(3) == 0) col += lean != 0 ? lean : r.nextBoolean() ? 1 : -1;
            } else {
                col += lean;
                if (r.nextBoolean()) row += dir;
            }
        }
    }
}
