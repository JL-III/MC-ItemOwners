package com.bfrisco.itemowners.util;

import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

public class RateLimiter {
    private final long LIMIT;
    private final HashMap<UUID, Date> map = new HashMap<>();

    public RateLimiter(long limit) {
        this.LIMIT = limit;
    }

    public boolean isLimited(UUID uuid) {
        if (!map.containsKey(uuid)) {
            map.put(uuid, new Date(System.currentTimeMillis()));
            return false;
        }

        Date lastExecuted = map.get(uuid);
        if (System.currentTimeMillis() - lastExecuted.getTime() < LIMIT) {
            return true;
        }

        map.put(uuid, new Date(System.currentTimeMillis()));
        return false;
    }

    public long secondsLeft(UUID uuid) {
        if (!map.containsKey(uuid)) return 0;
        Date lastExecuted = map.get(uuid);
        return 60 - ((System.currentTimeMillis() - lastExecuted.getTime()) / 1000);
    }
}
