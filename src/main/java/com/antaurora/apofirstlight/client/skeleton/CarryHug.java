package com.antaurora.apofirstlight.client.skeleton;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.skeleton.action.PlayerActions;
import com.antaurora.apofirstlight.skeleton.action.PlayerProps;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

/**
 * Held carry V1 (weight/HeldCarry, user 2026-10-27): an item carried in both arms (8 kg or more, or bulky) is held in front
 * of the belly by an action made for it, {@code item/carry_hug#<item path>}, with a prop made for it, {@code carry#<item
 * path>} (PlayerActions#made, PlayerProps#made): the item drawn at real size (it is held against the body, not the fist:
 * not the 1.5 x of things in the hand), its bounds the block's shape (a placed block's, the item model's units) or a half
 * block for other items.
 * <ul>
 *   <li>no wider than {@link #GRIP_HALF} either side: the hands on its sides, halfway up, at most {@link #SIDE_BACK} back
 *   from its near face (the arms reach no further), its near face {@link #CLEAR} px in front of the chest;</li>
 *   <li>wider: the hands on its near edge {@link #GRIP_HALF} either side of the middle, its near face {@link #CLEAR_WIDE} px
 *   in front of the chest (the forearms clear of the body);</li>
 *   <li>the grip at the belly ({@link #BELLY} px under the shoulders), raised up the item when its top would come within
 *   {@link #TOP_GAP} px of the shoulders (a tall load covers the face otherwise: a desk is held by its near face, not under it).</li>
 * </ul>
 */
public final class CarryHug {
    /** Half the hands' span on a wide load (item units = m), the grip's height under the shoulder pivots and the gap to the chest (model px). */
    static final float GRIP_HALF = 0.3F, BELLY = 8.5F, CLEAR = 0.8F, CLEAR_WIDE = 2F, TOP_GAP = 1F;
    /** How far back from the near face the hands go on a narrow load (item units = m). */
    static final float SIDE_BACK = 0.12F;
    /** The chest box's half depth (model px). */
    private static final float CHEST_HALF = 2F;

    private CarryHug() {
    }

    /** The action for carrying this stack in both arms, or null (only AFL items: the skeleton draws only their props). */
    public static String action(ItemStack stack) {
        install();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !ApocalypseFirstLight.MOD_ID.equals(id.getNamespace())) return null;
        return PlayerActions.get("item/carry_hug#" + id.getPath()) == null ? null : "item/carry_hug#" + id.getPath();
    }

    private static void install() {
        if (PlayerProps.made != null) return;
        PlayerProps.made = CarryHug::prop;
        PlayerActions.made = CarryHug::actionJson;
    }

    private record Grip(AABB box, boolean sides, Vector3f hold, Vector3f right, Vector3f left) {}

    /** The item's box: the block's shape for a block item, else a half block. */
    private static AABB box(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(ApocalypseFirstLight.MOD_ID, path));
        AABB box = null;
        if (item instanceof BlockItem block) {
            try {
                var shape = block.getBlock().defaultBlockState().getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                if (!shape.isEmpty()) box = shape.bounds();
            } catch (RuntimeException ignored) {
                // a shape that needs its level: the default box
            }
        }
        return box == null ? new AABB(0.2, 0, 0.2, 0.8, 0.6, 0.8) : box;
    }

    private static Grip grip(AABB box) {
        float cx = (float) (box.minX + box.maxX) / 2, cz = (float) (box.minZ + box.maxZ) / 2;
        boolean sides = (box.maxX - box.minX) / 2 <= GRIP_HALF;
        float y = Math.max(sides ? (float) (box.minY + box.maxY) / 2 : (float) box.minY + 0.04F, (float) box.maxY - (BELLY - TOP_GAP) / 16);
        float z = sides ? Math.min(cz, (float) box.minZ + SIDE_BACK) : (float) box.minZ + 0.04F;   // item -z: the face toward the body (model +z)
        float half = sides ? (float) (box.maxX - box.minX) / 2 : GRIP_HALF;
        return new Grip(box, sides, new Vector3f(cx, y, z), new Vector3f(cx - half, y, z), new Vector3f(cx + half, y, z));
    }

    private static PlayerProps.Prop prop(String id) {
        return prop(id, box(id.substring(id.indexOf('#') + 1)));
    }

    /** The prop for an item of this box (pure: the offline checks call it). */
    public static PlayerProps.Prop prop(String id, AABB box) {
        String path = id.substring(id.indexOf('#') + 1);
        Grip g = grip(box);
        AABB b = g.box;
        return new PlayerProps.Prop(id, List.of(path), 1F, new Vector3f((float) b.minX, (float) b.minY, (float) b.minZ),
                new Vector3f((float) b.maxX, (float) b.maxY, (float) b.maxZ), 0, null,
                Map.of("hold", g.hold, "hold_r", g.right, "hold_l", g.left), null, Map.of(), null, null, null);
    }

    private static JsonObject actionJson(String id) {
        return actionJson(id, box(id.substring(id.indexOf('#') + 1)));
    }

    /** The action for an item of this box (pure: the offline checks call it). */
    public static JsonObject actionJson(String id, AABB box) {
        String path = id.substring(id.indexOf('#') + 1);
        Grip g = grip(box);
        float ahead = CHEST_HALF + (g.sides ? CLEAR : CLEAR_WIDE) + (float) (g.hold.z - g.box.minZ) * 16;   // the near face just in front of the chest
        JsonObject json = new JsonObject();
        json.addProperty("format_version", 1);
        json.addProperty("about", "Carried in both arms (held carry V1, client/skeleton/CarryHug): " + path);
        json.addProperty("layer", "action");
        json.addProperty("mask", "arms");
        json.addProperty("priority", 30);
        json.addProperty("first_person", "body");
        JsonObject blend = new JsonObject();
        blend.addProperty("in", 0.25);
        blend.addProperty("out", 0.25);
        json.add("blend", blend);
        JsonObject key = new JsonObject();
        key.addProperty("at", 0);
        key.addProperty("frame", "shoulder_r");
        key.addProperty("point", "hold");
        key.add("pos", arr(5F, BELLY, -ahead));   // the body's middle: 5 px in from the right shoulder
        key.add("rot", arr(0, 0, 0));
        key.addProperty("blend", 0);
        JsonArray keys = new JsonArray();
        keys.add(key);
        JsonObject prop = new JsonObject();
        prop.addProperty("id", "carry#" + path);
        prop.add("keys", keys);
        json.add("prop", prop);
        JsonObject hands = new JsonObject();
        hands.add("right", hand("prop:hold_r"));
        hands.add("left", hand("prop:hold_l"));
        json.add("hands", hands);
        json.add("events", new JsonArray());
        return json;
    }

    private static JsonArray hand(String target) {
        JsonObject k = new JsonObject();
        k.addProperty("at", 0);
        k.addProperty("target", target);
        k.addProperty("blend", 0);
        k.addProperty("roll", 0);
        JsonArray a = new JsonArray();
        a.add(k);
        return a;
    }

    private static JsonArray arr(float x, float y, float z) {
        JsonArray a = new JsonArray();
        a.add(x);
        a.add(y);
        a.add(z);
        return a;
    }
}
