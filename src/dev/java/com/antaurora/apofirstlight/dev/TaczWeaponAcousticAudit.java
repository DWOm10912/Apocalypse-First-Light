package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.noise.GunshotNoiseResolver;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/** Opt-in, read-only runtime audit. Never included in the published JAR. */
@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TaczWeaponAcousticAudit {
    @GameTest(template = "network_empty", timeoutTicks = 100)
    public static void exportLoadedGuns(GameTestHelper helper) throws Exception {
        String output = System.getProperty("afl.taczAudit.output");
        if (output == null) { helper.succeed(); return; }
        var loaded = TimelessAPI.getAllCommonGunIndex();
        helper.assertTrue(!loaded.isEmpty(), "TaCZ runtime gun definitions not loaded");
        // Read the actual immutable override map, rather than duplicating it in the exporter.
        var field = GunshotNoiseResolver.class.getDeclaredField("REFERENCE_RADII");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<ResourceLocation, Double> explicit = (Map<ResourceLocation, Double>) field.get(null);
        Map<String, Set<String>> aflRecipes = new TreeMap<>();
        Map<String, Set<String>> otherRecipes = new TreeMap<>();
        for (var recipe : helper.getLevel().getRecipeManager().getRecipes()) {
            recordRecipe(recipe.getId(), recipe.getResultItem(helper.getLevel().registryAccess()), aflRecipes, otherRecipes);
        }
        for (var recipe : TimelessAPI.getAllRecipes().values()) {
            recordRecipe(recipe.getId(), recipe.getOutput(), aflRecipes, otherRecipes);
        }
        JsonObject root = new JsonObject();
        root.addProperty("exported_at_utc", Instant.now().toString());
        root.addProperty("tacz_version", ModList.get().getModContainerById("tacz").orElseThrow()
                .getModInfo().getVersion().toString());
        root.addProperty("api", "TimelessAPI.getAllCommonGunIndex -> CommonAssetsManager.get().getAllGuns");
        root.add("selected_data_packs", new GsonBuilder().create().toJsonTree(
                helper.getLevel().getServer().getPackRepository().getSelectedIds()));
        root.addProperty("unknown_definition_fallback", GunshotNoiseResolver.resolveRadius(ItemStack.EMPTY,
                new ResourceLocation("apocalypse_firstlight", "audit_nonexistent_gun")));
        JsonObject overrides = new JsonObject();
        explicit.forEach((id, radius) -> overrides.addProperty(id.toString(), radius));
        root.add("explicit_map", overrides);
        JsonArray rows = new JsonArray();
        var ids = new HashSet<ResourceLocation>();
        var ordered = new ArrayList<>(loaded);
        ordered.sort(Comparator.comparing(entry -> entry.getKey().toString()));
        for (var entry : ordered) {
            ResourceLocation id = entry.getKey();
            var index = entry.getValue();
            helper.assertTrue(ids.add(id), "Duplicate gun: " + id);
            ItemStack bare = GunItemBuilder.create().setId(id).build();
            IGun gun = IGun.getIGunOrNull(bare);
            helper.assertTrue(gun != null && gun.getGunId(bare).equals(id), "Canonical gun stack mismatch: " + id);
            ResourceLocation muzzle = gun.getAttachmentId(bare, AttachmentType.MUZZLE);
            helper.assertTrue(muzzle == null || muzzle.toString().equals("tacz:empty"), "Non-bare gun: " + id);
            double radius = GunshotNoiseResolver.resolveRadius(bare, id);
            helper.assertTrue(Double.isFinite(radius) && radius > 0, "Invalid radius: " + id);
            JsonObject row = new JsonObject();
            row.addProperty("canonical_gun_id", id.toString());
            row.addProperty("translation_key", index.getPojo().getName());
            row.addProperty("weapon_category", index.getType() == null ? "UNKNOWN" : index.getType());
            row.addProperty("effective_noise_radius", radius);
            row.addProperty("radius_source", explicit.containsKey(id) ? "EXPLICIT" : "DEFAULT");
            row.addProperty("uses_default_radius", !explicit.containsKey(id));
            row.addProperty("afl_recipe_present", aflRecipes.containsKey(id.toString()));
            // Resolver consults MUZZLE for every IGun. This is not a claim of attachment compatibility.
            row.addProperty("muzzle_or_suppressor_modifier_supported", true);
            row.addProperty("muzzle_slot_allowed", index.getGunData().getAllowAttachments() != null
                    && index.getGunData().getAllowAttachments().contains(AttachmentType.MUZZLE));
            row.add("afl_recipes", new GsonBuilder().create().toJsonTree(aflRecipes.getOrDefault(id.toString(), Set.of())));
            row.add("other_recipes", new GsonBuilder().create().toJsonTree(otherRecipes.getOrDefault(id.toString(), Set.of())));
            rows.add(row);
        }
        root.add("guns", rows);
        helper.assertTrue(rows.size() == loaded.size(), "Incomplete runtime set");
        Path path = Path.of(output);
        Files.createDirectories(path.getParent());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n", StandardCharsets.UTF_8);
        long count = ordered.stream().filter(entry -> explicit.containsKey(entry.getKey())).count();
        long recipes = ids.stream().filter(id -> aflRecipes.containsKey(id.toString())).count();
        System.out.println("TaCZ guns total: " + rows.size() + "\nExplicit AFL radii: " + count
                + "\nUsing default radius: " + (rows.size() - count)
                + "\nAFL recipe present: " + recipes + "\nAFL recipe absent: " + (rows.size() - recipes)
                + "\nRuntime snapshot: " + path);
        helper.succeed();
    }

    private static void recordRecipe(ResourceLocation recipeId, ItemStack output,
                                     Map<String, Set<String>> afl, Map<String, Set<String>> other) {
        IGun gun = IGun.getIGunOrNull(output);
        if (gun == null) return;
        var destination = recipeId.getNamespace().equals(ApocalypseFirstLight.MOD_ID) ? afl : other;
        destination.computeIfAbsent(gun.getGunId(output).toString(), ignored -> new TreeSet<>()).add(recipeId.toString());
    }
}
