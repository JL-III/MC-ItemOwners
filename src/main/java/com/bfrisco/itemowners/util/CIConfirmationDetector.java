package com.bfrisco.itemowners.util;

import java.util.HashMap;
import java.util.UUID;

public class CIConfirmationDetector {
    private final HashMap<UUID, Boolean> confirmations = new HashMap<>();

    public boolean hasConfirmed(UUID uuid) {
        if (!confirmations.containsKey(uuid)) {
            confirmations.put(uuid, true);
            return false;
        }

        confirmations.remove(uuid);
        return true;
    }
}
