package com.warband.ai;

import com.warband.compat.IllagerKinds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.WeakHashMap;

/** Cheap density guard for outposts and other illager-heavy structures. */
public final class IllagerLoadGuard {

    private static final int DENSE_ILLAGER_LIMIT = 10;
    private static final int CACHE_TICKS = 40;

    private static final WeakHashMap<Mob, CacheEntry> CACHE = new WeakHashMap<>();

    private IllagerLoadGuard() {
    }

    public static boolean tooDenseForHeavyDoctrine(Mob mob) {
        if (!IllagerKinds.isIllagerLike(mob) || !(mob.level() instanceof ServerLevel level)) return false;
        long now = level.getGameTime();
        CacheEntry cached = CACHE.get(mob);
        if (cached != null && now - cached.tick < CACHE_TICKS) {
            return cached.dense;
        }
        AABB box = AABB.ofSize(mob.position(), 48.0, 24.0, 48.0);
        int count = level.getEntitiesOfClass(Mob.class, box, candidate ->
                candidate.isAlive() && IllagerKinds.isIllagerLike(candidate)).size();
        boolean dense = count > DENSE_ILLAGER_LIMIT;
        CACHE.put(mob, new CacheEntry(now, dense));
        return dense;
    }

    private record CacheEntry(long tick, boolean dense) {
    }
}
