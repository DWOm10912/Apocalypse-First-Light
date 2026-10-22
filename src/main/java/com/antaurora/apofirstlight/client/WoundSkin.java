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
 * Skin wound marks (docs/gameplay/skin_wound_marks_v1_plan.md): every drawn player with wounds (HealthPackets.Marks#skin)
 * or lightning's fern (#fern) gets a texture laid out as the skin, drawn over the bare skin and under its second layer, the
 * clothes and the gear: lightning burns, metal burns in the shape of what burned them, scratches, the swelling of an
 * infection, pus, the gauze pad on a dressed head, chest or belly, the Lichtenberg fern. A dressed limb draws nothing
 * (BandageWrapRenderer's gauze covers the band its marks sit in). Fixed places: each wound's seed. Fading: its healed
 * sixteenths. The texture is redrawn only when the marks change; one unused for 10 s is released.
 * <p>V2 detail (user 2026-10-21, "像素太低了……做更精致，更真实的效果", then 4 x 4 after seeing 16 x 16 look like a sticker
 * on the 64 x 64 skins): {@link #K} texels a skin pixel (256 x 256), nearest filtering and hard edges like the skin's own
 * pixels, painted from distance fields and noise in skin-pixel units (a charred, cracked burn with a raw ring and peeling
 * edge, a leathery contact burn, tapered claw lines with blood beads and a broken scab, a woven gauze pad with tape, a
 * Lichtenberg fern fanning out from where the current came in and forking into feathery tips).
 * <p>Drawn by the skeleton (PlayerSkeleton#draw), the vanilla body ({@link Layer}), the first-person arms
 * (NativePlayerArmRenderer) and the inventory's body chart (BodySilhouette, the front faces); all of them take the skin's
 * UVs as fractions, so the size does not matter to them.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class WoundSkin {
    /** Texels a skin pixel. */
    static final int K = 4;
    static final int SIZE = 64 * K;
    private static final long KEEP_MS = 10_000;

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

    // ---------------------------------------------------------------- bands and places

    /**
     * A part's skin as a band round it, in skin pixels: the side faces in a row on the skin (west, front, east, back:
     * vanilla's box unfolding, so the band wraps round the limb), rows from the top (shoulder, hip, crown) down. A face on
     * its own (the head's top) does not wrap.
     */
    private record Band(int u, int v, int around, int rows, boolean wrap) {}

    private static final Band CROWN = new Band(8, 0, 8, 8, false);

    private static Band band(BodyPart part, boolean slim) {
        int a = slim ? 3 : 4;
        return switch (part) {
            case HEAD -> new Band(0, 8, 32, 8, true);
            case CHEST, BELLY -> new Band(16, 20, 24, 12, true);
            case RIGHT_ARM -> new Band(40, 20, 8 + 2 * a, 12, true);
            case LEFT_ARM -> new Band(32, 52, 8 + 2 * a, 12, true);
            case RIGHT_LEG -> new Band(0, 20, 16, 12, true);
            default -> new Band(16, 52, 16, 12, true);
        };
    }

    /**
     * The rows a part's marks sit in: an arm's 1.5..5.5 px and a leg's 2..6 px from the hand or foot (where the
     * dressing goes, BandageWrapRenderer ARM_BAND / LEG_BAND), the head's top four (the brow and the scalp, under the
     * wrap round the head), the chest's upper six rows of the body, the belly's lower six.
     */
    private static int[] rows(BodyPart part) {
        return switch (part) {
            case HEAD -> new int[]{0, 3};
            case CHEST -> new int[]{0, 5};
            case BELLY -> new int[]{6, 11};
            case RIGHT_ARM, LEFT_ARM -> new int[]{6, 10};
            default -> new int[]{6, 9};
        };
    }

    /** A skin pixel on a band (column, row) where a mark starts. */
    private record Spot(int col, int row) {}

    /**
     * A spot on the part's band: the front and the flanks on the chest and belly (where the player's own hands reach to
     * dress it, user 2026-10-21; the back is left to the slung gun's burn), anywhere round a limb or the head, in the
     * part's rows, leaving room for the mark ({@code across} x {@code down} pixels).
     */
    private static Spot place(BodyPart part, Band band, Random r, int across, int down) {
        int[] rows = rows(part);
        int row = rows[0] + 1 + r.nextInt(Math.max(1, rows[1] - rows[0] - down - 1 + 1));
        row = Math.min(row, rows[1] - down);
        int col;
        if (part == BodyPart.CHEST || part == BodyPart.BELLY) {
            // one flank (0..3), the front (4..11), the other flank (12..15): a burn's 3..12, the claw lines' 4..9 (the
            // lines reach 1.8 px left and 3.3 px right of the spot), so an infection's swelling stays off the back (16..23)
            col = across > 1 ? 4 + r.nextInt(6) : 3 + r.nextInt(10);
        } else {
            col = r.nextInt(band.around);
        }
        return new Spot(col, Math.max(rows[0], row));
    }

    // ---------------------------------------------------------------- the canvas

    /** A canvas of straight-alpha ARGB over the whole texture, written to the image at the end. */
    private static final class Canvas {
        final int[] argb = new int[SIZE * SIZE];

        void over(int i, int c) {
            if ((c >>> 24) != 0) argb[i] = WoundSkin.over(c, argb[i]);
        }
    }

    /** A texel visited on a band: its index on the canvas and its centre in band pixels (columns not wrapped). */
    private interface Texel {
        void at(int index, float x, float y);
    }

    /** Colour (straight ARGB) at a point, in pixels from a mark's centre. */
    private interface Paint {
        int at(float x, float y);
    }

    /** Signed distance (pixels, negative inside) to a wound's shape, from its centre. */
    private interface Sdf {
        float d(float x, float y);
    }

    /** Every texel of a band in a box (band pixels): columns wrap round the band (or stop at a single face), rows stop at its ends. */
    private static void each(Band b, float x0, float y0, float x1, float y1, Texel t) {
        int w = b.around * K;
        int ty0 = Math.max(0, (int) Math.floor(y0 * K)), ty1 = Math.min(b.rows * K - 1, (int) Math.ceil(y1 * K));
        int tx0 = (int) Math.floor(x0 * K), tx1 = Math.min((int) Math.ceil(x1 * K), tx0 + w - 1);
        for (int ty = ty0; ty <= ty1; ty++) {
            int rowAt = (b.v * K + ty) * SIZE + b.u * K;
            for (int tx = tx0; tx <= tx1; tx++) {
                int col = b.wrap ? Math.floorMod(tx, w) : tx;
                if (col < 0 || col >= w) continue;
                t.at(rowAt + col, (tx + 0.5F) / K, (ty + 0.5F) / K);
            }
        }
    }

    /** A paint over a box round (cx, cy) on a band. */
    private static void shade(Canvas c, Band b, float cx, float cy, float rx, float ry, Paint p) {
        each(b, cx - rx, cy - ry, cx + rx, cy + ry, (i, x, y) -> c.over(i, p.at(x - cx, y - cy)));
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

    /** The marks as straight-alpha ARGB rows, SIZE x SIZE (also the offline check, docs/gameplay/skin_wound_marks_v1_plan.md). */
    static int[] canvas(List<HealthPackets.SkinMark> skin, HealthPackets.Fern fern, boolean slim) {
        Canvas c = new Canvas();
        if (fern.strength() > 0 && fern.part() != null) fern(c, fern, slim);
        // the gauze pads first: a pad covers its own wound, not one beside it left open
        for (HealthPackets.SkinMark m : skin) if (m.bandaged()) mark(c, m, slim);
        for (HealthPackets.SkinMark m : skin) if (!m.bandaged()) mark(c, m, slim);
        return c.argb;
    }

    // ---------------------------------------------------------------- the marks

    /**
     * One wound's mark: its band, its centre and half size (band pixels, the wound alone, without the reddening round
     * it), its shape, its paint, and where pus gathers when it suppurates ({x, y, radius} from the centre).
     */
    private record Wound(Band band, float cx, float cy, float rx, float ry, Sdf sd, Paint paint, float[] pus) {}

    /** How far round a wound its own paint reaches (the reddening, the abrasion). */
    private static final float MARGIN = 1.5F;

    private static void mark(Canvas c, HealthPackets.SkinMark m, boolean slim) {
        Random r = new Random(m.seed());
        Band band = band(m.part(), slim);
        float h = m.healed() / 15F;
        boolean infected = m.stage() != Wounds.Stage.SCRATCH;
        // an infection's fading follows its recovery once cleaned; the wound under it is drawn fresh
        float wound = infected ? 0F : h;
        Wound w;
        if (m.kind() == Wounds.Kind.DEEP_BURN) {
            w = metal(m.part(), band, r, wound, m.seed());
        } else if (m.kind() == Wounds.Kind.BURN) {
            Spot spot = place(m.part(), band, r, 1, 1);
            Band on = band;
            if (m.part() == BodyPart.HEAD && r.nextInt(5) < 3) {
                // a lightning burn's way in at the crown: the head's top face (8, 0)
                on = CROWN;
                spot = new Spot(2 + r.nextInt(4), 2 + r.nextInt(4));
            }
            w = burn(on, spot.col() + 0.5F, spot.row() + 0.5F, r, wound, m.seed());
        } else {
            w = scratch(band, place(m.part(), band, r, 3, 2), r, wound, m.seed());
        }
        if (m.bandaged()) {
            pad(c, w, h, infected, m.seed());   // a dressed head, chest or belly (a dressed limb is left out): the pad over it
            return;
        }
        if (infected) shade(c, w.band, w.cx, w.cy, w.rx + 1.9F, w.ry + 1.9F, swell(w.sd, 1F - 0.85F * h, m.seed()));
        shade(c, w.band, w.cx, w.cy, w.rx + MARGIN, w.ry + MARGIN, w.paint);
        if (m.stage() == Wounds.Stage.SUPPURATION) shade(c, w.band, w.cx, w.cy, w.rx + 0.6F, w.ry + 0.6F, pus(w.pus, m.seed()));
    }

    /**
     * A lightning burn (the current's way in or out): a charred, cracked core flecked with ash, a wet raw ring with a
     * peeling pale edge, reddened skin round it. Healing: a dark dry eschar, then shiny pink new skin, a pale scar, gone.
     */
    private static Wound burn(Band band, float cx, float cy, Random r, float h, int seed) {
        float ang = r.nextFloat() * 6.2832F, sq = 0.82F + 0.3F * r.nextFloat();
        float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
        float e = smooth(0.15F, 0.45F, h), nw = smooth(0.5F, 0.8F, h), gone = smooth(0.8F, 1F, h);
        Sdf sd = (x, y) -> {
            float u = (x * cs + y * sn) / sq, v = (y * cs - x * sn) * sq;
            return (float) Math.sqrt(u * u + v * v) + (fbm(x * 1.4F, y * 1.4F, seed) - 0.5F) * 0.6F - 1F;
        };
        float core = 0.6F * (1F - 0.35F * nw), outer = 1F - 0.12F * nw;
        Paint p = (x, y) -> {
            float s = sd.d(x, y) + 1F;
            float n = fbm(x * 3.1F, y * 3.1F, seed + 1), n2 = fbm(x * 2.3F, y * 2.3F, seed + 4);
            // the char: dark, cracked to the raw flesh under it, ash on it
            int ch = mix(0x1E1310, 0x4A2C20, n);
            float crack = 1F - smooth(0.05F, 0.1F, Math.abs(noise(x * 2.5F, y * 2.5F, seed + 2) - 0.5F));
            ch = mix(ch, 0x7E2A20, crack * (1F - e) * 0.8F);
            ch = mix(ch, 0x6E625A, smooth(0.62F, 0.8F, noise(x * 3F, y * 3F, seed + 3)) * 0.5F * (1F - e));
            int coreC = mix(mix(ch, mix(0x2E1A12, 0x5A3222, n), e), mix(0xC89A8C, 0xD8AEA0, n), nw);
            // the raw ring: wet red with glints, drying darker, then new pink skin
            int raw = mix(0x8E2420, 0xC4483C, n2);
            raw = mix(raw, 0xE89082, smooth(0.72F, 0.85F, noise(x * 3.5F, y * 3.5F, seed + 5)) * 0.6F * (1F - e));
            int ringC = mix(mix(raw, mix(0x8A3A2E, 0xB0584A, n2), e), mix(0xD08C84, 0xE0A49A, n2), nw);
            int col = mix(coreC, ringC, smooth(core - 0.08F, core + 0.08F, s));
            // the peeling edge just inside the outer edge
            float rim = (1F - smooth(0.04F, 0.14F, Math.abs(s - outer + 0.05F))) * (1F - e);
            col = mix(col, mix(0xE8D4BE, 0xD2B49A, n2), rim * 0.85F);
            float ery = (1F - smooth(outer, outer + 1.15F, s)) * 0.55F * (1F - 0.5F * e) * (1F - nw) * (0.7F + 0.6F * n2);
            return fade(over(argb(cover(s - outer), col), argb(ery, 0xC2564A)), (1F - gone) * (1F - 0.3F * nw));
        };
        float px = (r.nextFloat() - 0.5F) * 0.5F, py = (r.nextFloat() - 0.5F) * 0.5F;
        return new Wound(band, cx, cy, 1F, 1F, sd, p, new float[]{px, py, 0.3F, -px * 1.4F, -py * 1.4F, 0.16F});
    }

    /**
     * A metal burn in the shape of what was hot (LightningInjury#hotMetal): a long gun on the back (the chest's back, a
     * strip along the sling's line), the holstered pistol on the right thigh's outside (an upright oblong), the rig's
     * magazines on the belly's front (one or two narrow uprights). A crisp edge: a leathery waxy core with a dark margin, a
     * narrow raw band, blistered skin, reddening. Healing: dark eschar, then a scar that keeps the shape, gone.
     */
    private static Wound metal(BodyPart part, Band band, Random r, float h, int seed) {
        float cx, cy, rx, ry;
        Sdf shape;
        float[] pus;
        switch (part) {
            case CHEST -> {
                // the back (16..23): from one shoulder blade down toward the other side
                boolean down = r.nextBoolean();
                cx = 20F;
                cy = 3F;
                float ax = down ? -2.5F : 2.5F, ay = -2.5F, bx = -ax, by = 2.5F;
                shape = (x, y) -> segment(x, y, ax, ay, bx, by) - 0.5F - 0.08F * (float) Math.sin((x - ax) * 2.1F);
                rx = ry = 3.1F;
                pus = new float[]{ax * 0.5F, -1.25F, 0.3F, 0F, 0F, 0.34F, bx * 0.45F, 1.1F, 0.26F};
            }
            case RIGHT_LEG -> {
                // the outside of the right thigh (the band's west face, 0..3)
                cx = 2F;
                cy = 8F;
                shape = (x, y) -> roundBox(x, y, 1F, 2F, 0.35F);
                rx = 1F;
                ry = 2F;
                pus = new float[]{-0.1F, -0.7F, 0.34F, 0.15F, 0.7F, 0.28F};
            }
            case BELLY -> {
                // the front (4..11), one or two magazines side by side
                int n = 1 + r.nextInt(2), first = 5 + r.nextInt(n == 1 ? 6 : 3);
                float off = n == 1 ? 0F : 1.5F;
                cx = first + 0.5F + off;
                cy = 8.5F;
                shape = (x, y) -> {
                    float d = roundBox(x + off, y, 0.55F, 1.5F, 0.22F);
                    return n == 1 ? d : Math.min(d, roundBox(x - off, y, 0.55F, 1.5F, 0.22F));
                };
                rx = 0.55F + off;
                ry = 1.5F;
                pus = n == 1 ? new float[]{0F, -0.3F, 0.3F} : new float[]{-off, -0.3F, 0.28F, off, 0.4F, 0.26F};
            }
            default -> {
                Spot spot = place(part, band, r, 2, 2);
                cx = spot.col() + 1F;
                cy = spot.row() + 1F;
                shape = (x, y) -> roundBox(x, y, 1F, 1F, 0.45F);
                rx = ry = 1F;
                pus = new float[]{0F, 0F, 0.38F};
            }
        }
        float e = smooth(0.15F, 0.5F, h), nw = smooth(0.5F, 0.8F, h), gone = smooth(0.8F, 1F, h);
        float rawW = 0.3F * (1F - 0.6F * nw);
        Sdf sd = shape;
        Paint p = (x, y) -> {
            float s = sd.d(x, y) + (noise(x * 3F, y * 3F, seed) - 0.5F) * 0.15F;
            float n = fbm(x * 2.6F, y * 2.6F, seed + 1), n2 = fbm(x * 2F, y * 2F, seed + 6);
            // leathery, waxy, darker at the margin where the metal's edge pressed
            int leather = mix(0x5A3626, 0x8A6A54, n);
            leather = mix(leather, 0xA89484, smooth(0.66F, 0.82F, noise(x * 3.5F, y * 3.5F, seed + 7)) * 0.4F);
            leather = mix(leather, 0x34190F, smooth(-0.25F, -0.04F, s) * 0.8F);
            int coreC = mix(mix(leather, mix(0x2E180F, 0x4E2C1E, n), e), mix(0xB88070, 0xC89A8A, n), nw);
            int raw = mix(mix(0x962A22, 0xBC4036, n2), mix(0x8A3A2E, 0xA8544A, n2), e);
            raw = mix(raw, mix(0xD08C84, 0xDCA096, n2), nw);
            int col = mix(coreC, raw, smooth(-0.03F, 0.03F, s));
            float rim = (1F - smooth(0.03F, 0.11F, Math.abs(s - rawW))) * (1F - e);
            col = mix(col, mix(0xE6CEB2, 0xCDAE92, n2), rim * 0.8F);
            float ery = (1F - smooth(rawW, rawW + 1.0F, s)) * 0.5F * (1F - 0.5F * e) * (1F - nw) * (0.7F + 0.6F * n2);
            return fade(over(argb(cover(s - rawW), col), argb(ery, 0xC0584A)), (1F - gone) * (1F - 0.25F * nw));
        };
        return new Wound(band, cx, cy, rx, ry, sd, p, pus);
    }

    /**
     * Three claw lines, slanting, a little bent like the fingers' arc, tapered at both ends: a deep red gouge, blood beads,
     * scuffed skin round it. Healing: a dark scab that breaks up, then a thin pale line, gone.
     */
    private static Wound scratch(Band band, Spot spot, Random r, float h, int seed) {
        boolean right = r.nextBoolean();
        float e = smooth(0.15F, 0.45F, h), nw = smooth(0.5F, 0.8F, h), gone = smooth(0.8F, 1F, h);
        float bend = (r.nextFloat() - 0.5F) * 0.5F;
        float[][] lines = new float[3][];
        List<float[]> beads = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            float t0 = r.nextFloat() * (k == 1 ? 0.1F : 0.3F), t1 = 1F - r.nextFloat() * (k == 1 ? 0.1F : 0.3F);
            float xa = 2 * k - 3.3F, ya = -1.35F, xb = 2 * k - 0.7F, yb = 1.35F;
            if (!right) {
                xa = 2 * k - 0.7F;
                xb = 2 * k - 3.3F;
            }
            float ax = xa + (xb - xa) * t0, ay = ya + (yb - ya) * t0, bx = xa + (xb - xa) * t1, by = ya + (yb - ya) * t1;
            float wmax = 0.16F + 0.05F * r.nextFloat() + (k == 1 ? 0.03F : 0F);
            lines[k] = bezier(ax, ay, bx, by, bend, wmax);
            for (int b = 1 + r.nextInt(2); b > 0; b--) {
                float t = 0.25F + 0.5F * r.nextFloat();
                beads.add(new float[]{ax + (bx - ax) * t + (r.nextFloat() - 0.5F) * 0.15F, ay + (by - ay) * t + 0.05F + r.nextFloat() * 0.12F,
                        0.07F + 0.07F * r.nextFloat()});
            }
        }
        Sdf sd = (x, y) -> {
            float s = Float.MAX_VALUE;
            for (float[] l : lines) s = Math.min(s, stroke(l, x, y, 1F, null));
            return s;
        };
        float ws = 1F + 0.2F * e - 0.5F * nw;
        float[] q = new float[1];
        Paint p = (x, y) -> {
            float s = Float.MAX_VALUE, depth = 1F;
            for (float[] l : lines) {
                float d = stroke(l, x, y, ws, q);
                if (d < s) {
                    s = d;
                    depth = q[0];
                }
            }
            float n = fbm(x * 3F, y * 3F, seed + 1);
            // the gouge: darkest in the middle; drying to a crust that flakes; a pale line
            int gouge = mix(0x5A0E0B, 0xA8302A, depth * depth);
            int scab = mix(0x3A160F, 0x6A2A1C, n);
            int col = mix(mix(gouge, scab, e), mix(0xD8A89E, 0xE4B8AE, n), nw);
            float a = cover(s);
            a *= 1F - e * (1F - nw) * (1F - smooth(0.28F, 0.42F, noise(x * 3.4F, y * 3.4F, seed + 3))) * 0.75F;
            a *= 1F - 0.35F * nw;
            // scuffed skin round the lines
            float scuff = (1F - smooth(0F, 0.38F, s)) * (0.3F + 0.5F * fbm(x * 3F, y * 3F, seed + 2)) * (1F - 0.6F * e) * (1F - nw);
            int out = over(argb(a, col), argb(scuff, mix(0xC4625A, 0xB04C46, n)));
            // blood beads on a fresh one
            if (e < 1F)
                for (float[] b : beads) {
                    float dx = x - b[0], dy = y - b[1], bd = (float) Math.sqrt(dx * dx + dy * dy) - b[2];
                    if (bd > 0.1F) continue;
                    float hl = 1F - smooth(0F, 0.05F, (float) Math.sqrt((dx + b[2] * 0.35F) * (dx + b[2] * 0.35F) + (dy + b[2] * 0.35F) * (dy + b[2] * 0.35F)));
                    out = over(argb(cover(bd) * (1F - e), mix(0x6A100C, 0xD07068, hl * 0.7F)), out);
                }
            return fade(out, 1F - gone);
        };
        float[] mid0 = lines[1], mid1 = lines[right ? 2 : 0];
        float[] pus = {at(mid0, 0.5F, 0), at(mid0, 0.5F, 1), 0.2F, at(mid1, 0.45F, 0), at(mid1, 0.45F, 1), 0.16F};
        return new Wound(band, spot.col() + 1.5F, spot.row() + 1.5F, 3.3F, 1.4F, sd, p, pus);
    }

    /** An infection's swelling under the wound: hot red near it, duskier further out, taut and mottled. */
    private static Paint swell(Sdf sd, float fade, int seed) {
        return (x, y) -> {
            float s = Math.max(0F, sd.d(x, y));
            float n = fbm(x * 1.8F, y * 1.8F, seed + 20);
            float a = (1F - smooth(0F, 1.75F, s + (n - 0.5F) * 0.5F)) * (0.45F + 0.3F * n) * fade;
            int col = mix(0xC8483E, 0x9A3844, smooth(0.3F, 1.5F, s));
            col = mix(col, 0xE0847A, smooth(0.62F, 0.8F, noise(x * 4F, y * 4F, seed + 21)) * 0.3F);
            return argb(a, col);
        };
    }

    /** Pus where the wound is deepest: soft creamy beads with a yellow-green rim, a wet highlight, an inflamed edge. */
    private static Paint pus(float[] at, int seed) {
        return (x, y) -> {
            float s = Float.MAX_VALUE, hx = 0, hy = 0, rad = 1F;
            for (int i = 0; i < at.length; i += 3) {
                float dx = x - at[i], dy = y - at[i + 1];
                float d = (float) Math.sqrt(dx * dx + dy * dy) - at[i + 2] * (0.85F + 0.3F * noise(x * 3F, y * 3F, seed + 30 + i));
                if (d < s) {
                    s = d;
                    hx = dx / at[i + 2];
                    hy = dy / at[i + 2];
                    rad = at[i + 2];
                }
            }
            int col = mix(mix(0xD6C48E, 0xC8B47A, noise(x * 4F, y * 4F, seed + 29)), 0x9C8A50, smooth(-0.1F * rad / 0.3F, 0F, s));
            float hl = 1F - smooth(0.05F, 0.35F, (float) Math.sqrt((hx + 0.35F) * (hx + 0.35F) + (hy + 0.35F) * (hy + 0.35F)));
            col = mix(col, 0xF4EAD0, hl * 0.45F);
            float ring = (1F - smooth(0F, 0.1F, s)) * 0.4F;
            return over(argb(cover(s) * 0.88F, col), argb(s > 0 ? ring : 0F, 0x7A2A1E));
        };
    }

    /**
     * The gauze pad over a dressed wound on the head or trunk: the wound and a little round it, woven threads (two
     * texels a thread), a folded edge, a shadow on the skin, two strips of tape over its sides, what has soaked through.
     */
    private static void pad(Canvas c, Wound w, float h, boolean infected, int seed) {
        float hx = w.rx + 0.55F, hy = w.ry + 0.55F;
        float stain = infected ? 0.32F : 0.42F * (1F - h);
        int stainC = infected ? 0xC2A458 : 0x8E3228;
        Paint p = (x, y) -> {
            float s = roundBox(x, y, hx, hy, 0.18F);
            int out = argb(s > 0 ? (1F - smooth(0F, 0.22F, s)) * 0.3F : 0F, 0x4A3A32);
            float fx = (x + 50F) * K / 2F, fy = (y + 50F) * K / 2F;
            float gx = smooth(0.28F, 0.5F, Math.abs(fx - (float) Math.floor(fx) - 0.5F)), gy = smooth(0.28F, 0.5F, Math.abs(fy - (float) Math.floor(fy) - 0.5F));
            float thread = hash((int) Math.floor(fx), 0, seed + 40) * 0.5F + hash(0, (int) Math.floor(fy), seed + 41) * 0.5F;
            int col = mix(0xF0EDE5, 0xE2DDD2, thread);
            col = mix(col, 0xCDC5B6, gx * gy * 0.9F);
            col = mix(col, 0xD0C8BA, smooth(-0.22F, -0.02F, s) * 0.7F);
            float st = (1F - smooth(0.1F, 0.9F, w.sd.d(x, y) + (fbm(x * 2.5F, y * 2.5F, seed + 42) - 0.5F) * 0.8F)) * stain;
            col = mix(col, stainC, st);
            out = over(argb(cover(s), col), out);
            for (int side = -1; side <= 1; side += 2) {
                float tx = x - side * (hx - 0.1F), ts = roundBox(tx, y, 0.28F, hy + 0.35F, 0.05F);
                if (ts > 0.2F) continue;
                out = over(argb(ts > 0 ? (1F - smooth(0F, 0.14F, ts)) * 0.25F : 0F, 0x4A3A32), out);
                int tape = 0xE6E0D2;
                tape = mix(tape, 0xCFC7B8, noise(tx * 4F, y * 2F, seed + 43 + side) * 0.35F);
                out = over(argb(cover(ts) * 0.95F, tape), out);
            }
            return out;
        };
        shade(c, w.band, w.cx, w.cy, hx + 0.4F, hy + 0.6F, p);
    }

    // ---------------------------------------------------------------- the Lichtenberg fern

    /**
     * Lightning's fern (the plan's 4.3), drawn like a real Lichtenberg figure (user 2026-10-21: a straight trunk with side
     * shoots "看起来有点像肺的血管"): from where the current came in several main branches fan out; each forks into two of
     * nearly its own width again and again, bending as it goes, and ends in dense feathery tips. Pinkish red, darker near
     * the way in, over a soft blush (the capillaries under the skin, not a line drawn on it); all of it fades as
     * {@code strength} drops. An arm: up the upper arm from above the dressing's band, and from that shoulder down over
     * the chest and the back. The head: from the neck down the chest and the back. A leg: up the belly from that hip. The
     * chest or belly: all round a point on it.
     */
    private static void fern(Canvas c, HealthPackets.Fern fern, boolean slim) {
        Random r = new Random(fern.seed());
        float alpha = (45 + 185 * fern.strength() / 15F) / 255F;
        Pen pen = new Pen(r);
        Band body = band(BodyPart.CHEST, slim);
        float[] all = {0F, 12F};
        float up = -1.5708F, down = 1.5708F;
        switch (fern.part()) {
            case RIGHT_ARM, LEFT_ARM -> {
                boolean right = fern.part() == BodyPart.RIGHT_ARM;
                Band arm = band(fern.part(), slim);
                int a = slim ? 3 : 4;
                // up the outside (the right arm's west face, the left's east) and the front of the upper arm
                pen.fan(arm, new float[]{0F, 6F}, right ? 2F : 4 + a + 2F, 5.8F, up, 1.4F, 3, 1.5F, 0.18F);
                // from that shoulder over the chest's front (4..11) and the back (16..23), toward the middle
                pen.fan(body, all, right ? 4.5F : 11.5F, 0.2F, down + (right ? -0.5F : 0.5F), 1.6F, 4, 2.4F, 0.22F);
                pen.fan(body, all, right ? 23F : 17F, 0.2F, down + (right ? 0.5F : -0.5F), 1.5F, 3, 2.2F, 0.2F);
            }
            case HEAD -> {
                pen.fan(body, all, 8F, 0.2F, down, 2.2F, 5, 2.4F, 0.22F);
                pen.fan(body, all, 20F, 0.2F, down, 2F, 4, 2.2F, 0.2F);
            }
            case RIGHT_LEG, LEFT_LEG -> {
                boolean right = fern.part() == BodyPart.RIGHT_LEG;
                pen.fan(body, all, right ? 6F : 10F, 11.8F, up, 1.6F, 4, 2.4F, 0.22F);
                pen.fan(body, all, right ? 22F : 18F, 11.8F, up, 1.4F, 3, 2F, 0.2F);
            }
            default -> {
                float row = fern.part() == BodyPart.CHEST ? 3.5F : 8.5F, col = 6.5F + r.nextInt(4);
                pen.fan(body, all, col, row, r.nextFloat() * 6.2832F, 5F, 5, 1.8F, 0.2F);
            }
        }
        for (int i = 0; i < SIZE * SIZE; i++) {
            if (pen.halo[i] <= 0 && pen.line[i] <= 0) continue;
            c.over(i, argb(pen.halo[i] * 0.25F * alpha, 0xD8857A));
            c.over(i, argb(pen.line[i] * 0.85F * alpha, mix(0xD27466, 0xA8382F, pen.line[i])));
        }
    }

    /** The fern's lines and blush as coverage (the strongest wins where lines meet, so joints do not darken). */
    private static final class Pen {
        /** Forks from a main branch to its tips. */
        private static final int FORKS = 5;
        final float[] line = new float[SIZE * SIZE], halo = new float[SIZE * SIZE];
        final Random r;

        Pen(Random r) {
            this.r = r;
        }

        /** {@code n} main branches from (x, y), spread over {@code spread} radians round {@code dir} (rows down). */
        void fan(Band b, float[] rows, float x, float y, float dir, float spread, int n, float len, float w) {
            for (int i = 0; i < n; i++) {
                float t = n == 1 ? 0.5F : i / (n - 1F);
                float ang = dir + (t - 0.5F) * spread + (r.nextFloat() - 0.5F) * spread / n * 0.6F;
                grow(b, rows, x, y, ang, len * (0.8F + 0.35F * r.nextFloat()), w * (0.85F + 0.3F * r.nextFloat()), 0);
            }
        }

        /**
         * One branch from (x, y) heading {@code ang}: {@code len} pixels, bending, {@code w} half wide narrowing a fifth,
         * within {@code rows}; then it forks in two a little narrower and shorter (now and then with a short side twig),
         * {@link #FORKS} times, so it ends in fine feathery tips.
         */
        void grow(Band b, float[] rows, float x, float y, float ang, float len, float w, int depth) {
            float step = 0.25F, dir = ang, bend = (r.nextFloat() - 0.5F) * 0.16F, k = 1F - 0.07F * depth, wEnd = w * 0.8F;
            for (float done = 0F; done < len; done += step) {
                dir += bend + (r.nextFloat() - 0.5F) * 0.2F;
                float nx = x + (float) Math.cos(dir) * step, ny = y + (float) Math.sin(dir) * step;
                if (ny < rows[0] || ny > rows[1]) return;
                seg(b, x, y, nx, ny, w + (wEnd - w) * done / len, w + (wEnd - w) * Math.min(1F, (done + step) / len), k);
                x = nx;
                y = ny;
            }
            if (depth >= FORKS) return;
            float split = 0.28F + 0.3F * r.nextFloat(), lean = (r.nextFloat() - 0.5F) * 0.25F, next = len * (0.74F + 0.14F * r.nextFloat());
            grow(b, rows, x, y, dir - split + lean, next, wEnd * 0.82F, depth + 1);
            grow(b, rows, x, y, dir + split + lean, next * (0.8F + 0.3F * r.nextFloat()), wEnd * 0.82F, depth + 1);
            if (depth >= 2 && r.nextInt(3) == 0)
                grow(b, rows, x, y, dir + (r.nextBoolean() ? 1 : -1) * (0.9F + 0.3F * r.nextFloat()), next * 0.45F, wEnd * 0.6F, FORKS);
        }

        private void seg(Band b, float ax, float ay, float bx, float by, float wa, float wb, float k) {
            float reach = Math.max(wa, wb) * 2F + 0.4F;
            float dx = bx - ax, dy = by - ay, ll = dx * dx + dy * dy;
            each(b, Math.min(ax, bx) - reach, Math.min(ay, by) - reach, Math.max(ax, bx) + reach, Math.max(ay, by) + reach, (i, x, y) -> {
                float t = clamp01(((x - ax) * dx + (y - ay) * dy) / ll);
                float px = ax + dx * t - x, py = ay + dy * t - y, d = (float) Math.sqrt(px * px + py * py), w = wa + (wb - wa) * t;
                // under a texel wide: a texel-wide line, fainter (but still there: the feathery tips)
                float we = Math.max(w, 0.6F / K);
                float lc = (d < we ? 1F : 0F) * Math.max(0.5F, w / we) * k;
                if (lc > line[i]) line[i] = lc;
                float hc = (1F - smooth(0F, reach, d)) * k;
                if (hc > halo[i]) halo[i] = hc;
            });
        }
    }

    // ---------------------------------------------------------------- shapes, colours, noise

    /** A quadratic curve from a to b bowed {@code bend} pixels sideways, as 9 points {x, y, half width}: tapered at both ends. */
    private static float[] bezier(float ax, float ay, float bx, float by, float bend, float wmax) {
        float mx = (ax + bx) / 2F, my = (ay + by) / 2F, dx = bx - ax, dy = by - ay, l = (float) Math.sqrt(dx * dx + dy * dy);
        float qx = mx - dy / l * bend * 2F, qy = my + dx / l * bend * 2F;
        float[] out = new float[27];
        for (int i = 0; i <= 8; i++) {
            float t = i / 8F, u = 1F - t;
            out[i * 3] = u * u * ax + 2 * u * t * qx + t * t * bx;
            out[i * 3 + 1] = u * u * ay + 2 * u * t * qy + t * t * by;
            out[i * 3 + 2] = wmax * (float) Math.pow(Math.sin(Math.PI * t), 0.55);
        }
        return out;
    }

    /** Signed distance to a tapered stroke (half widths times {@code ws}); {@code depth}[0]: how far from its middle line (0..1). */
    private static float stroke(float[] l, float x, float y, float ws, @Nullable float[] depth) {
        float best = Float.MAX_VALUE, q = 1F;
        for (int i = 0; i < l.length - 3; i += 3) {
            float ax = l[i], ay = l[i + 1], dx = l[i + 3] - ax, dy = l[i + 4] - ay;
            float t = clamp01(((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy));
            float px = ax + dx * t - x, py = ay + dy * t - y, d = (float) Math.sqrt(px * px + py * py);
            float w = (l[i + 2] + (l[i + 5] - l[i + 2]) * t) * ws;
            if (d - w < best) {
                best = d - w;
                q = w > 0 ? Math.min(1F, d / w) : 1F;
            }
        }
        if (depth != null) depth[0] = q;
        return best;
    }

    /** A point along a stroke (0..1 of its points): coordinate {@code axis} (0 x, 1 y). */
    private static float at(float[] l, float t, int axis) {
        return l[Math.round(t * 8F) * 3 + axis];
    }

    private static float segment(float x, float y, float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay, t = clamp01(((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy));
        float px = ax + dx * t - x, py = ay + dy * t - y;
        return (float) Math.sqrt(px * px + py * py);
    }

    private static float roundBox(float x, float y, float hx, float hy, float round) {
        float qx = Math.abs(x) - hx + round, qy = Math.abs(y) - hy + round;
        float ox = Math.max(qx, 0F), oy = Math.max(qy, 0F);
        return (float) Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0F) - round;
    }

    /** Whether a texel's centre is in a shape, from its signed distance (pixels): hard edges like the skin's own pixels. */
    private static float cover(float d) {
        return d < 0F ? 1F : 0F;
    }

    private static float clamp01(float v) {
        return v < 0F ? 0F : Math.min(v, 1F);
    }

    private static float smooth(float e0, float e1, float v) {
        float t = clamp01((v - e0) / (e1 - e0));
        return t * t * (3F - 2F * t);
    }

    private static int argb(float a, int rgb) {
        return Math.round(clamp01(a) * 255F) << 24 | rgb & 0xFFFFFF;
    }

    private static int fade(int c, float f) {
        return Math.round((c >>> 24) * clamp01(f)) << 24 | c & 0xFFFFFF;
    }

    /** a to b by t, per channel (0xRRGGBB). */
    private static int mix(int a, int b, float t) {
        t = clamp01(t);
        int r = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int g = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }

    /** Straight-alpha {@code top} over {@code bottom}. */
    private static int over(int top, int bottom) {
        float ta = (top >>> 24) / 255F, ba = (bottom >>> 24) / 255F * (1F - ta), oa = ta + ba;
        if (oa <= 0F) return 0;
        int r = Math.round(((top >> 16 & 255) * ta + (bottom >> 16 & 255) * ba) / oa);
        int g = Math.round(((top >> 8 & 255) * ta + (bottom >> 8 & 255) * ba) / oa);
        int b = Math.round(((top & 255) * ta + (bottom & 255) * ba) / oa);
        return Math.round(oa * 255F) << 24 | r << 16 | g << 8 | b;
    }

    private static float hash(int x, int y, int s) {
        int h = s * 0x632BE5AB + x * 0x9E3779B1 + y * 0x85EBCA77;
        h = (h ^ (h >>> 15)) * 0x2C1B3C6D;
        h = (h ^ (h >>> 12)) * 0x297A2D39;
        h ^= h >>> 15;
        return (h >>> 8) / 16777216F;
    }

    /** Value noise (0..1), one cell a unit. */
    private static float noise(float x, float y, int s) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float fx = x - ix, fy = y - iy;
        fx = fx * fx * (3F - 2F * fx);
        fy = fy * fy * (3F - 2F * fy);
        float a = hash(ix, iy, s), b = hash(ix + 1, iy, s), c = hash(ix, iy + 1, s), d = hash(ix + 1, iy + 1, s);
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
    }

    /** Three octaves of {@link #noise} (0..1). */
    private static float fbm(float x, float y, int s) {
        return (noise(x, y, s) + 0.5F * noise(x * 2F + 5.3F, y * 2F + 1.7F, s + 101) + 0.25F * noise(x * 4F + 2.9F, y * 4F + 7.1F, s + 202)) / 1.75F;
    }
}
