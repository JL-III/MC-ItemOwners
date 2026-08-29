package com.bfrisco.itemowners.listeners;

import com.bfrisco.itemowners.ItemOwners;
import com.bfrisco.itemowners.constants.LogMessages;
import com.bfrisco.itemowners.database.ItemEventRepository;
import com.bfrisco.itemowners.database.ItemEventType;
import com.bfrisco.itemowners.database.ItemRecoveryRepository;
import com.bfrisco.itemowners.database.ItemRepository;
import com.bfrisco.itemowners.util.CIConfirmationDetector;
import com.bfrisco.itemowners.util.ItemSerialization;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class OwnedItemListener implements Listener {
    private final Plugin plugin;
    private static final List<String> CLEAR_INVENTORY = Arrays.asList("/ci", "/eci", "/clean", "/eclean", "/clear", "/eclear", "/clearinvent", "/eclearinvent", "/eclearinventory");
    private final CIConfirmationDetector ciConfirmation = new CIConfirmationDetector();

    public OwnedItemListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryCloseEvent(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player))  return;
        if (!(event.getView().title() instanceof TextComponent tc)) return;

        if (tc.content().equalsIgnoreCase("disposal")) {
            for (ItemStack item : event.getInventory().getContents()) {
                String itemId = ItemOwners.getItemId(item);
                if (itemId == null) continue;
                log(String.format(LogMessages.DISPOSED, itemId, player.getName()));
                runAsync(() -> ItemEventRepository.save(ItemEventType.DISPOSED, itemId, player));
            }
        }

        else if (tc.content().equalsIgnoreCase("ender chest")) {
            for (ItemStack item : event.getInventory().getContents()) {
                String itemId = ItemOwners.getItemId(item);
                if (itemId == null) continue;
                log(String.format(LogMessages.TO_ENDER_CHEST, itemId, player.getName()));
                runAsync(() -> ItemEventRepository.save(ItemEventType.TO_ENDER_CHEST, itemId, player));
            }
        }

        else if (tc.content().contains("Vault #")) {
            String vaultNumber = tc.content().split("#")[1];
            for (ItemStack item : event.getInventory().getContents()) {
                String itemId = ItemOwners.getItemId(item);
                if (itemId == null) continue;
                log(String.format(LogMessages.TO_PLAYER_VAULT, itemId, vaultNumber, player.getName()));
                runAsync(() -> ItemEventRepository.save(ItemEventType.TO_PLAYER_VAULT, itemId, player));
            }
        }
    }

    @EventHandler
    public void onPlayerCommandPreprocessEvent(PlayerCommandPreprocessEvent event) {
        if (!event.getPlayer().hasPermission("essentials.clearinventory")) return;
        if (CLEAR_INVENTORY.contains(event.getMessage())) {
            for (ItemStack item : event.getPlayer().getInventory().getContents()) {
                String itemId = ItemOwners.getItemId(item);
                if (itemId == null) continue;

                if (ItemOwners.getBukkitConfig().getBoolean("clear-inventory-confirmation")) {
                    if (ciConfirmation.hasConfirmed(event.getPlayer().getUniqueId())) {
                        log(String.format(LogMessages.CLEARED_INVENTORY, itemId, event.getPlayer().getName()));
                        runAsync(() -> ItemEventRepository.save(ItemEventType.CLEARED_INVENTORY, itemId, event.getPlayer()));
                    }
                    return;
                }

                log(String.format(LogMessages.CLEARED_INVENTORY, itemId, event.getPlayer().getName()));
                runAsync(() -> ItemEventRepository.save(ItemEventType.CLEARED_INVENTORY, itemId, event.getPlayer()));
            }
        }
    }

    @EventHandler
    public void onEntityDeathEvent(EntityDeathEvent event) {
        if (event.getEntity().getType() != EntityType.PLAYER) return;

        for (ItemStack item : event.getDrops()) {
            String itemId = ItemOwners.getItemId(item);
            if (itemId == null) continue; // Item dropped is not owned.

            Player player = (Player) event.getEntity();

            log(String.format(LogMessages.DROPPED_ON_DEATH_BY_PLAYER, itemId, player.getName()));
            runAsync(() -> ItemEventRepository.save(ItemEventType.DROPPED_ON_DEATH_BY_PLAYER, itemId, player));
        }

    }

    @EventHandler
    public void onInventoryMoveItemEvent(InventoryMoveItemEvent event) {
        String itemId = ItemOwners.getItemId(event.getItem());
        if (itemId == null) return; // Inventory move item event does not involve owned items.

        if (event.getInitiator().getHolder() instanceof Player) {
            Player player = (Player) event.getInitiator().getHolder();

            if (!isStorageInventory(event.getDestination().getType())) {
                log(String.format(LogMessages.TO_INVENTORY, itemId, player.getName()));
                runAsync(() -> ItemEventRepository.save(ItemEventType.TO_INVENTORY, itemId, player.getUniqueId().toString(), event.getDestination().getLocation()));
            } else {
                log(String.format(LogMessages.TO_STORAGE, itemId, player.getName()));
                runAsync(() -> ItemEventRepository.save(ItemEventType.TO_STORAGE, itemId, player.getUniqueId().toString(), event.getDestination().getLocation()));
            }

            return;
        }

        if (!isStorageInventory(event.getDestination().getType())) {
            log(String.format(LogMessages.TO_INVENTORY_BY_UNKNOWN, itemId));
            runAsync(() -> ItemEventRepository.save(ItemEventType.TO_INVENTORY_BY_UNKNOWN, itemId, event.getDestination().getLocation()));
        } else {
            log(String.format(LogMessages.TO_STORAGE_BY_UNKNOWN, itemId));
            runAsync(() -> ItemEventRepository.save(ItemEventType.TO_STORAGE_BY_UNKNOWN, itemId, event.getDestination().getLocation()));
        }
    }

    @EventHandler
    public void onPlayerItemBreakEvent(PlayerItemBreakEvent event) {
        String itemId = ItemOwners.getItemId(event.getBrokenItem());
        if (itemId == null) return; // Item break event does not involve owned items.
        log(String.format(LogMessages.BROKE, itemId));
        runAsync(() -> ItemEventRepository.save(ItemEventType.BROKE, itemId, event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDespawnEvent(ItemDespawnEvent event) {
        ItemStack snapshot = event.getEntity().getItemStack().clone();
        String itemId = ItemOwners.getItemId(snapshot);
        if (itemId == null) return; // Item despawn event does not involve owned items.
        if (ItemOwners.isNotValid(snapshot)) {
            ItemOwners.getBukkitLogger().warning("Owned item " + itemId + " despawned with an invalid material or stack size; recovery was not opened.");
            return;
        }

        String itemData;
        try {
            itemData = ItemSerialization.toBase64(snapshot);
        } catch (IllegalStateException e) {
            ItemOwners.getBukkitLogger().warning("Could not snapshot despawning item " + itemId + ": " + e.getMessage());
            return;
        }

        UUID sourceEntityId = event.getEntity().getUniqueId();
        Location location = event.getLocation();
        String world = location.getWorld() == null ? null : location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        long lostAt = System.currentTimeMillis();

        // ItemDespawnEvent is cancellable. Verify on the next tick that the entity was actually removed,
        // including cancellations made by another MONITOR-priority listener.
        Bukkit.getScheduler().runTask(plugin, () -> {
            Entity entity = Bukkit.getEntity(sourceEntityId);
            if (entity != null && entity.isValid()) {
                return;
            }

            runAsync(() -> {
                try {
                    com.bfrisco.itemowners.database.Item item = ItemRepository.findById(itemId);
                    if (item == null) {
                        ItemOwners.getBukkitLogger().warning("Despawning item " + itemId + " is not in the database.");
                        return;
                    }

                    boolean recorded = ItemRecoveryRepository.recordLoss(
                            itemId,
                            item.getOwnerId(),
                            sourceEntityId.toString(),
                            itemData,
                            lostAt,
                            world,
                            x,
                            y,
                            z
                    );
                    if (recorded) {
                        ItemRepository.updateData(itemId, itemData);
                    }

                    log(String.format(LogMessages.DESPAWNED, itemId));
                    ItemEventRepository.save(ItemEventType.DESPAWNED, itemId, null, world, x, y, z);
                } catch (Exception e) {
                    ItemOwners.getBukkitLogger().warning("Could not record despawn recovery for " + itemId + ": " + e.getMessage());
                }
            });
        });
    }

    @EventHandler
    public void onEntityDamageEvent(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item item)) return;

        String itemId = ItemOwners.getItemId(item.getItemStack());
        if (itemId == null) return;
        log(String.format(LogMessages.DAMAGED, itemId));
        runAsync(() -> ItemEventRepository.save(ItemEventType.DAMAGED, itemId, event.getEntity().getLocation()));
    }

    @EventHandler
    public void onEntityPickUpItemEvent(EntityPickupItemEvent event) {
        String itemId = ItemOwners.getItemId(event.getItem().getItemStack());
        if (itemId == null) return; // Pickup event does not involve owned items.

        if (event.getEntity().getType() == EntityType.PLAYER) {
            Player player = (Player) event.getEntity();
            log(String.format(LogMessages.PICKED_UP_BY_PLAYER, itemId, player.getName()));
            runAsync(() -> ItemEventRepository.save(ItemEventType.PICKED_UP_BY_PLAYER, itemId, player));
        } else {
            log(String.format(LogMessages.PICKED_UP_BY_ENTITY, itemId));
            runAsync(() -> ItemEventRepository.save(ItemEventType.PICKED_UP_BY_ENTITY, itemId, event.getEntity().getLocation()));
        }
    }

    @EventHandler
    public void onPlayerDropItemEvent(PlayerDropItemEvent event) {
        String itemId = ItemOwners.getItemId(event.getItemDrop().getItemStack());
        if (itemId == null) return; // Drop event does not involve owned items.

        log(String.format(LogMessages.DROPPED_BY_PLAYER, itemId, event.getPlayer().getName()));
        runAsync(() -> ItemEventRepository.save(ItemEventType.DROPPED_BY_PLAYER, itemId, event.getPlayer()));
    }

    @EventHandler
    public void onInventoryDragEvent(InventoryDragEvent event) {
        String itemId = ItemOwners.getItemId(event.getOldCursor());
        if (itemId == null) return; // Drag event does not involve owned items.
        if (event.getWhoClicked().getType() == EntityType.PLAYER) return;
        Player player = (Player) event.getWhoClicked();
        UUID playerId = event.getWhoClicked().getUniqueId();

        InventoryType inventory = event.getInventory().getType();
        if (!isStorageInventory(inventory)) {
            log(String.format(LogMessages.TO_INVENTORY, itemId, player.getName()));
            runAsync(() -> ItemEventRepository.save(ItemEventType.TO_INVENTORY, itemId, playerId.toString(), event.getInventory().getLocation()));
        } else {
            log(String.format(LogMessages.TO_STORAGE, itemId, player.getName()));
            runAsync(() -> ItemEventRepository.save(ItemEventType.TO_STORAGE, itemId, playerId.toString(), event.getInventory().getLocation()));
        }
    }

    @EventHandler
    public void onAnvilUpdateEvent(PrepareAnvilEvent event) {
        String itemId = ItemOwners.getItemId(event.getInventory().getSecondItem());
        if (itemId == null) return;
        event.setResult(null);
    }

    @EventHandler
    public void onInventoryClickEvent(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;

        InventoryAction action = event.getAction();
        if (action == InventoryAction.NOTHING) return;
        if (action == InventoryAction.UNKNOWN) return;
        if (event.getWhoClicked().getType() != EntityType.PLAYER) return;

        InventoryType inventory = event.getClickedInventory().getType();
        Player player = (Player) event.getWhoClicked();
        UUID playerId = event.getWhoClicked().getUniqueId();

        /* ======================
         * Grindstone
         * ====================== */
        if (event.getClickedInventory().getType() == InventoryType.GRINDSTONE && event.getSlotType() == InventoryType.SlotType.RESULT) {
            for (ItemStack item : event.getClickedInventory().getContents()) {
                String itemId = ItemOwners.getItemId(item);
                if (itemId == null) continue;

                log(String.format(LogMessages.GRINDED, itemId, player.getName()));
                runAsync(() -> ItemEventRepository.save(ItemEventType.GRINDED, itemId, player));
            }

            return;
        }

        /* ======================
         * Hotbar swaps
         * ====================== */
        if (action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD) {
            ItemStack item1; // Item going into container

            if (event.getHotbarButton() == -1) {
                item1 = event.getWhoClicked().getInventory().getItemInOffHand();
            } else {
                item1 = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
            }

            ItemStack item2 = event.getCurrentItem(); // Item which is going to the hotbar

            String itemId1 = ItemOwners.getItemId(item1);
            String itemId2 = ItemOwners.getItemId(item2);

            if (itemId1 != null) {
                if (!isStorageInventory(event.getClickedInventory().getType())) {
                    log(String.format(LogMessages.TO_INVENTORY, itemId1, player.getName()));
                    runAsync(() -> ItemEventRepository.save(ItemEventType.TO_INVENTORY, itemId1, playerId.toString(), event.getClickedInventory().getLocation()));
                } else {
                    log(String.format(LogMessages.TO_STORAGE, itemId1, player.getName()));
                    runAsync(() -> ItemEventRepository.save(ItemEventType.TO_STORAGE, itemId1, playerId.toString(), event.getClickedInventory().getLocation()));
                }
            }

            if (itemId2 != null) {
                log(String.format(LogMessages.TO_INVENTORY, itemId2, player.getName()));
                runAsync(() -> ItemEventRepository.save(ItemEventType.TO_INVENTORY, itemId2, player));
            }

            return;
        }

        String itemId = ItemOwners.getItemId(event.getCurrentItem(), event.getCursor());
        if (itemId == null) return; // Click event does not involve owned items.

        /* ======================
         * Item is picked up and placed into cursor
         * ====================== */
        if (
                action == InventoryAction.PICKUP_ALL || action == InventoryAction.PICKUP_HALF ||
                action == InventoryAction.PICKUP_SOME || action == InventoryAction.PICKUP_ONE ||
                action == InventoryAction.COLLECT_TO_CURSOR || action == InventoryAction.CLONE_STACK
        ) {
            if (!isStorageInventory(inventory)) {
                log(String.format(LogMessages.INVENTORY_TO_CURSOR, itemId, player.getName()));
                runAsync(() -> ItemEventRepository.save(ItemEventType.INVENTORY_TO_CURSOR, itemId, player));
            } else {
                log(String.format(LogMessages.STORAGE_TO_CURSOR, itemId, player.getName()));
                runAsync(() -> ItemEventRepository.save(ItemEventType.STORAGE_TO_CURSOR, itemId, player));
            }

            return;
        }

        /* ======================
         * Item is placed
         * ====================== */
        if (action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_SOME || action == InventoryAction.PLACE_ONE) {
            if (!isStorageInventory(inventory)) {
                log(String.format(LogMessages.TO_INVENTORY, itemId, player.getName()));
                runAsync(() -> ItemEventRepository.save(ItemEventType.TO_INVENTORY, itemId, player));
            } else {
                log(String.format(LogMessages.TO_STORAGE, itemId, player.getName()));
                runAsync(() -> ItemEventRepository.save(ItemEventType.TO_STORAGE, itemId, playerId.toString(), event.getClickedInventory().getLocation()));
            }

            return;
        }

        /* ======================
         * Item switches inventories
         * ====================== */
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (!canStoreByShiftClick(event.getInventory().getType())) {
                log(String.format(LogMessages.TO_INVENTORY, itemId, player.getName()));
                runAsync(() -> ItemEventRepository.save(ItemEventType.TO_INVENTORY, itemId, player));
                return;
            }

            if (!isStorageInventory(inventory)) {
                log(String.format(LogMessages.TO_STORAGE, itemId, player.getName()));
                runAsync(() -> ItemEventRepository.save(ItemEventType.TO_STORAGE, itemId, playerId.toString(), event.getInventory().getLocation()));
            } else {
                log(String.format(LogMessages.TO_INVENTORY, itemId, player.getName()));
                runAsync(() -> ItemEventRepository.save(ItemEventType.TO_INVENTORY, itemId, player));
            }

            return;
        }

        /* ======================
         * Swapped with cursor
         * ====================== */
        if (action == InventoryAction.SWAP_WITH_CURSOR) {
            String itemId1 = ItemOwners.getItemId(event.getCurrentItem()); // current item --> currently on cursor
            String itemId2 = ItemOwners.getItemId(event.getCursor()); // cursor --> item in slot

            if (itemId1 != null) {
                if (!isStorageInventory(inventory)) {
                    log(String.format(LogMessages.INVENTORY_TO_CURSOR, itemId1, player.getName()));
                    runAsync(() -> ItemEventRepository.save(ItemEventType.INVENTORY_TO_CURSOR, itemId1, player));
                } else {
                    log(String.format(LogMessages.STORAGE_TO_CURSOR, itemId1, player.getName()));
                    runAsync(() -> ItemEventRepository.save(ItemEventType.STORAGE_TO_CURSOR, itemId1, player));
                }
            }

            if (itemId2 != null) {
                if (!isStorageInventory(inventory)) {
                    log(String.format(LogMessages.TO_INVENTORY, itemId2, player.getName()));
                    runAsync(() -> ItemEventRepository.save(ItemEventType.TO_INVENTORY, itemId2, player));
                } else {
                    log(String.format(LogMessages.TO_STORAGE, itemId2, player.getName()));
                    runAsync(() -> ItemEventRepository.save(ItemEventType.TO_STORAGE, itemId2, playerId.toString(), event.getClickedInventory().getLocation()));
                }
            }
        }
    }

    private boolean isStorageInventory(InventoryType type) {
        if (type == InventoryType.CHEST) return true;
        if (type == InventoryType.DISPENSER) return true;
        if (type == InventoryType.DROPPER) return true;
        if (type == InventoryType.FURNACE) return true;
        if (type == InventoryType.ENDER_CHEST) return true;
        if (type == InventoryType.SHULKER_BOX) return true;
        if (type == InventoryType.BARREL) return true;
        if (type == InventoryType.BLAST_FURNACE) return true;
        if (type == InventoryType.SMOKER) return true;
        return type == InventoryType.HOPPER;
    }

    private boolean canStoreByShiftClick(InventoryType type) {
        if (type == InventoryType.BARREL) return true;
        if (type == InventoryType.SHULKER_BOX) return true;
        if (type == InventoryType.HOPPER) return true;
        if (type == InventoryType.DROPPER) return true;
        if (type == InventoryType.DISPENSER) return true;
        if (type == InventoryType.CHEST) return true;
        return type == InventoryType.ENDER_CHEST;
    }

    private void log(String str) {
        ItemOwners.getBukkitLogger().info(str);
    }

    private void runAsync(Runnable runnable) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }

}
