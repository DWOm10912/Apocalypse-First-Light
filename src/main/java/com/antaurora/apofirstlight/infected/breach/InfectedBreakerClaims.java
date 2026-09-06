package com.antaurora.apofirstlight.infected.breach;

import com.antaurora.apofirstlight.infected.ai.InfectedAiDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** One short-lived active breaker per block, scoped to a server level. */
public final class InfectedBreakerClaims {
    public static final int MAX_BREAKERS_PER_BLOCK = 1;
    public static final long CLAIM_TTL_TICKS = 60L;

    private static final Map<ServerLevel, Map<BlockPos, Claim>> CLAIMS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private InfectedBreakerClaims() {
    }

    public static boolean tryClaim(Zombie zombie, BlockPos position) {
        if (!(zombie.level() instanceof ServerLevel level)) {
            return false;
        }
        long now = level.getGameTime();
        Map<BlockPos, Claim> claims = claims(level);
        removeInvalid(level, claims, now);
        BlockPos immutablePosition = position.immutable();
        Claim current = claims.get(immutablePosition);
        if (current != null && !current.owner().equals(zombie.getUUID())) {
            return false;
        }
        if (current == null) {
            claims.put(immutablePosition, new Claim(zombie.getUUID(), now + CLAIM_TTL_TICKS));
            InfectedAiDiagnostics.breakTargetAcquired(level);
        } else {
            claims.put(immutablePosition, new Claim(current.owner(), now + CLAIM_TTL_TICKS));
        }
        return true;
    }

    public static boolean ownsAndRefreshes(Zombie zombie, BlockPos position) {
        if (!(zombie.level() instanceof ServerLevel level)) {
            return false;
        }
        Map<BlockPos, Claim> claims = claims(level);
        long now = level.getGameTime();
        removeInvalid(level, claims, now);
        Claim current = claims.get(position);
        if (current == null || !current.owner().equals(zombie.getUUID())) {
            return false;
        }
        claims.put(position.immutable(), new Claim(current.owner(), now + CLAIM_TTL_TICKS));
        return true;
    }

    public static Set<BlockPos> claimedByOthers(Zombie zombie) {
        if (!(zombie.level() instanceof ServerLevel level)) {
            return Set.of();
        }
        Map<BlockPos, Claim> claims = claims(level);
        removeInvalid(level, claims, level.getGameTime());
        Set<BlockPos> positions = new HashSet<>();
        claims.forEach((position, claim) -> {
            if (!claim.owner().equals(zombie.getUUID())) {
                positions.add(position);
            }
        });
        return positions;
    }

    public static void release(Zombie zombie, BlockPos position) {
        if (!(zombie.level() instanceof ServerLevel level)) {
            return;
        }
        Map<BlockPos, Claim> claims = CLAIMS.get(level);
        if (claims != null) {
            claims.computeIfPresent(position, (ignored, claim) ->
                    claim.owner().equals(zombie.getUUID()) ? null : claim);
        }
    }

    public static void releaseAll(Zombie zombie) {
        if (!(zombie.level() instanceof ServerLevel level)) {
            return;
        }
        Map<BlockPos, Claim> claims = CLAIMS.get(level);
        if (claims != null) {
            claims.entrySet().removeIf(entry -> entry.getValue().owner().equals(zombie.getUUID()));
        }
    }

    public static int activeClaimCount(ServerLevel level) {
        Map<BlockPos, Claim> claims = CLAIMS.get(level);
        if (claims == null) {
            return 0;
        }
        removeInvalid(level, claims, level.getGameTime());
        return claims.size();
    }

    public static int claimCountAt(ServerLevel level, BlockPos position) {
        Map<BlockPos, Claim> claims = CLAIMS.get(level);
        if (claims == null) {
            return 0;
        }
        removeInvalid(level, claims, level.getGameTime());
        return claims.containsKey(position) ? 1 : 0;
    }

    public static void clear(ServerLevel level) {
        CLAIMS.remove(level);
    }

    private static Map<BlockPos, Claim> claims(ServerLevel level) {
        return CLAIMS.computeIfAbsent(level, ignored -> new HashMap<>());
    }

    private static void removeInvalid(ServerLevel level, Map<BlockPos, Claim> claims, long now) {
        claims.entrySet().removeIf(entry -> {
            Claim claim = entry.getValue();
            Entity owner = level.getEntity(claim.owner());
            return claim.expiresAt() < now || !(owner instanceof Zombie zombie) || !zombie.isAlive()
                    || !InfectedBreachRules.canBreak(level.getBlockState(entry.getKey()));
        });
    }

    private record Claim(UUID owner, long expiresAt) {
    }
}
