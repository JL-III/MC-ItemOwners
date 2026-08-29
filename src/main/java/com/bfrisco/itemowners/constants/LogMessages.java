package com.bfrisco.itemowners.constants;

public interface LogMessages {
    String INVENTORY_TO_CURSOR = "%s was moved from inventory and to cursor of player %s";
    String STORAGE_TO_CURSOR = "%s was moved from storage and to cursor of player %s";
    String TO_INVENTORY = "%s was moved to the inventory of player %s";
    String TO_STORAGE = "%s was moved to storage by player %s";
    String TO_ENDER_CHEST = "%s was moved to an ender chest by player %s";
    String TO_PLAYER_VAULT = "%s was moved to player vault #%s by player %s";

    String TO_INVENTORY_BY_UNKNOWN = "%s was moved to the inventory of an unknown entity";
    String TO_STORAGE_BY_UNKNOWN = "%s was moved to storage by an unknown entity";

    String DROPPED_ON_DEATH_BY_PLAYER = "%s was dropped after death by player %s";
    String DROPPED_BY_PLAYER = "%s was dropped by player %s";
    String PICKED_UP_BY_PLAYER = "%s was picked up by player %s";
    String PICKED_UP_BY_ENTITY = "%s was picked up by an entity";

    String DESPAWNED = "%s was despawned after being dropped for 5 minutes";
    String DAMAGED = "%s was damaged";
    String BROKE = "%s broke";
    String DISPOSED = "%s was disposed by player %s";
    String CLEARED_INVENTORY = "%s was cleared through clearinventory command by %s";
    String GRINDED = "%s was used in a grindstone by player %s";
    String RECOVERED = "%s was recovered by player %s";

    String TRADED = "%s was traded by player %s to player %s";
}
