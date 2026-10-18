package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollDefinition;
import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollSnapshot;
import com.antaurora.apofirstlight.survivor.SurvivorCorpse;
import com.antaurora.apofirstlight.survivor.SurvivorRagdolls;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Locale;

/**
 * Ragdoll V1 R-B (docs/dev/ragdoll_v1_plan.md 8): a corpse falls on the server, comes to rest on the blocks by itself,
 * its record keeps the place and the pose through a save and load, and a body removed in the air lies on the ground
 * without a pose. Uses the empty 16 x 10 x 16 template with its own stone floor at y 1 (top at y 2).
 */
@GameTestHolder(ApocalypseFirstLight.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RagdollCorpseGameTests {
    private static final int BOXES = RagdollDefinition.get().bodies.length;

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }

    @GameTest(template = "network_empty", timeoutTicks = 260, batch = "ragdoll")
    public static void ragdollCorpseFlat(GameTestHelper h) {
        floor(h);
        settles(h, "flat", new Vec3(8, 2, 5), new Vec3(0, 0, 30), 2.0, 2.6);
    }

    /** Off the edge of a one-block step: it ends on the lower floor or hanging over the edge, never inside the step. */
    @GameTest(template = "network_empty", timeoutTicks = 260, batch = "ragdoll")
    public static void ragdollCorpseStep(GameTestHelper h) {
        floor(h);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 8; z++) h.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
        settles(h, "step", new Vec3(8, 3, 7.4), new Vec3(0, 0, 35), 2.0, 3.6);
    }

    /**
     * Removed while still in the air (as when its chunk unloads mid-fall): the record keeps no pose and waits; once the
     * entity is put back (the reconcile, at most a second later) it lies on the floor below.
     */
    @GameTest(template = "network_empty", timeoutTicks = 80, batch = "ragdoll")
    public static void ragdollCorpseRemovedInAir(GameTestHelper h) {
        floor(h);
        SurvivorCorpse corpse = SurvivorRagdolls.testFall(h.getLevel(), h.absoluteVec(new Vec3(8, 7, 8)), 0F, Vec3.ZERO);
        h.assertTrue(corpse != null && corpse.falling(), "the corpse starts falling");
        java.util.UUID id = corpse.getUUID();
        double floorTop = h.absoluteVec(new Vec3(0, 2, 0)).y;
        boolean[] removed = {false};
        h.runAfterDelay(3, () -> {
            corpse.discard();
            double[] saved = SurvivorRagdolls.testSavedRecord(h.getLevel().getServer(), id);
            h.assertTrue(saved != null, "the record is still there");
            h.assertTrue(saved.length == 3, "no pose for a body stopped in the air (" + (saved.length - 3) + " floats)");
            h.assertTrue(saved[1] > floorTop + 1, "it stopped where it hung: y " + saved[1]);
            h.assertTrue(SurvivorRagdolls.testUnsettled(h.getLevel().getServer(), id), "the record waits for the ground");
            removed[0] = true;
        });
        String[] verdict = {null};
        h.succeedWhen(() -> {
            h.assertTrue(removed[0], "not removed yet");
            if (verdict[0] == null) {
                h.assertTrue(h.getLevel().getEntity(id) instanceof SurvivorCorpse back && back != corpse, "not put back yet");
                // judged once (the test record goes away right after)
                double[] saved = SurvivorRagdolls.testSavedRecord(h.getLevel().getServer(), id);
                boolean unsettled = SurvivorRagdolls.testUnsettled(h.getLevel().getServer(), id);
                SurvivorRagdolls.testForget(h.getLevel(), id);
                if (saved == null || saved.length != 3) verdict[0] = "no pose after it was put back";
                else if (unsettled) verdict[0] = "the record is still unsettled";
                else if (Math.abs(saved[1] - floorTop) >= 1e-6) verdict[0] = "it lies at y " + saved[1] + ", floor " + floorTop;
                else verdict[0] = "";
            }
            h.assertTrue(verdict[0].isEmpty(), verdict[0]);
        });
    }

    /**
     * Eight bodies at once (MAX_ACTIVE): the five stand-in kinds of {@code /afl survivor ragdoll} from a mock viewer, and
     * three plain falls. All come to rest above the floor; a blow or a blast throws the stand-in away from the viewer
     * (it faced the viewer and fell backwards); the server cost of the whole crowd is logged. Its own batch, so no other
     * test's bodies are counted.
     */
    @GameTest(template = "network_empty", timeoutTicks = 400, batch = "ragdoll_crowd")
    public static void ragdollCorpseCrowd(GameTestHelper h) {
        floor(h);
        // a one-block step behind the stand-ins: the blast throws them against it
        for (int x = 0; x < 16; x++) for (int z = 8; z < 10; z++) h.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
        SurvivorRagdolls.testResetCost();
        net.minecraft.world.entity.player.Player viewer = h.makeMockPlayer();
        SurvivorRagdolls.StandIn[] kinds = SurvivorRagdolls.StandIn.values();
        java.util.List<SurvivorCorpse> bodies = new java.util.ArrayList<>();
        java.util.Map<SurvivorCorpse, Vec3> viewers = new java.util.HashMap<>();
        java.util.Map<SurvivorCorpse, SurvivorRagdolls.StandIn> kindOf = new java.util.HashMap<>();
        for (int i = 0; i < kinds.length; i++) {
            Vec3 eye = h.absoluteVec(new Vec3(2 + 3 * i, 2, 2));
            viewer.moveTo(eye.x, eye.y, eye.z, 0F, 0F);   // looking south (+z)
            SurvivorCorpse body = SurvivorRagdolls.standIn(h.getLevel(), viewer, kinds[i], null);
            h.assertTrue(body != null && body.falling(), "stand-in " + kinds[i] + " falls");
            bodies.add(body);
            viewers.put(body, eye);
            kindOf.put(body, kinds[i]);
        }
        double[][] plain = {{3, 4, 12, 12, 0, 0}, {8, 3, 12, -15, 0, 0}, {13, 5, 12, 0, 0, -20}};
        for (double[] p : plain) {
            SurvivorCorpse body = SurvivorRagdolls.testFall(h.getLevel(), h.absoluteVec(new Vec3(p[0], p[1], p[2])), 90F,
                    new Vec3(p[3], p[4], p[5]));
            h.assertTrue(body != null && body.falling(), "plain body falls");
            bodies.add(body);
        }
        h.assertTrue(bodies.size() == 8, "eight bodies");
        long start = h.getTick();
        double floorTop = h.absoluteVec(new Vec3(0, 2, 0)).y;
        String[] verdict = {null};
        h.succeedWhen(() -> {
            for (SurvivorCorpse body : bodies) h.assertFalse(body.falling(), "still falling");
            if (verdict[0] == null) {
                StringBuilder wrong = new StringBuilder();
                for (SurvivorCorpse body : bodies) {
                    double[] saved = SurvivorRagdolls.testSavedRecord(h.getLevel().getServer(), body.getUUID());
                    if (saved == null) { wrong.append(" a record is gone;"); continue; }
                    if (saved[1] < floorTop - 0.03) wrong.append(String.format(Locale.ROOT, " lowest point %.3f below the floor;", saved[1]));
                    SurvivorRagdolls.StandIn kind = kindOf.get(body);
                    if (kind == SurvivorRagdolls.StandIn.HIT || kind == SurvivorRagdolls.StandIn.BLAST) {
                        Vec3 eye = viewers.get(body);
                        double dz = saved[2] - eye.z;
                        if (dz < 2.5) wrong.append(String.format(Locale.ROOT, " %s did not fall away (dz %.2f);", kind, dz));
                    }
                }
                double[] cost = SurvivorRagdolls.testCost();
                ApocalypseFirstLight.LOGGER.info(String.format(Locale.ROOT,
                        "[AFL RAGDOLL TEST] crowd of 8 still after %d ticks; server cost %.3f ms per tick (mean over %d ticks), worst %.3f ms",
                        h.getTick() - start, cost[1], (int) cost[0], cost[2]));
                int removed = SurvivorRagdolls.clearStandIns(h.getLevel().getServer());
                if (removed != kinds.length) wrong.append(" clear removed ").append(removed).append(';');
                for (SurvivorCorpse body : bodies) SurvivorRagdolls.testForget(h.getLevel(), body.getUUID());
                verdict[0] = wrong.toString().trim();
            }
            h.assertTrue(verdict[0].isEmpty(), verdict[0]);
        });
    }

    /** Pushes a standing body at {@code rel} and waits until it is still; its lowest point must lie between the heights. */
    private static void settles(GameTestHelper h, String name, Vec3 rel, Vec3 push, double lowRel, double highRel) {
        SurvivorCorpse corpse = SurvivorRagdolls.testFall(h.getLevel(), h.absoluteVec(rel), 0F, push);
        h.assertTrue(corpse != null && corpse.falling(), "the corpse starts falling");
        long start = h.getTick();
        double low = h.absoluteVec(new Vec3(0, lowRel, 0)).y, high = h.absoluteVec(new Vec3(0, highRel, 0)).y;
        String[] verdict = {null};
        h.succeedWhen(() -> {
            h.assertFalse(corpse.falling(), "still falling");
            if (verdict[0] == null) {
                // judged once, the moment it is still (the test record goes away right after)
                verdict[0] = judge(h, corpse, low, high);
                SurvivorRagdolls.testForget(h.getLevel(), corpse.getUUID());
                ApocalypseFirstLight.LOGGER.info("[AFL RAGDOLL TEST] {} still after {} ticks at {}{}", name,
                        h.getTick() - start, corpse.position(), verdict[0].isEmpty() ? "" : " FAIL " + verdict[0]);
            }
            h.assertTrue(verdict[0].isEmpty(), verdict[0]);
        });
    }

    /** "" when the still body and its record are right, else what is wrong. */
    private static String judge(GameTestHelper h, SurvivorCorpse corpse, double low, double high) {
        double[] saved = SurvivorRagdolls.testSavedRecord(h.getLevel().getServer(), corpse.getUUID());
        if (saved == null) return "the record is gone";
        float[] live = corpse.pose();
        if (live == null || !RagdollSnapshot.valid(live, BOXES)) return "the entity has no final pose";
        if (saved.length != 3 + live.length) return "the record lost the pose in save and load";
        for (int i = 0; i < live.length; i++) if (saved[3 + i] != live[i]) return "saved pose float " + i + " differs";
        Vec3 at = corpse.position();
        if (at.distanceTo(new Vec3(saved[0], saved[1], saved[2])) > 1e-6) return "the entity is not at the record's place";
        if (saved[1] < low - 0.03 || saved[1] > high)
            return String.format(Locale.ROOT, "lowest point %.3f outside %.2f..%.2f", saved[1], low, high);
        for (int i = 0; i < BOXES; i++) {
            double y = saved[1] + live[i * RagdollSnapshot.FLOATS + 1];
            if (y < low) return "box " + i + " centre below the floor: " + y;
        }
        return "";
    }
}
