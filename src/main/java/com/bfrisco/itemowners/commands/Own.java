package com.bfrisco.itemowners.commands;
import com.bfrisco.itemowners.ItemOwners;
import com.bfrisco.itemowners.constants.ItemOwnerPermissions;
import com.bfrisco.itemowners.database.ItemRepository;
import com.bfrisco.itemowners.util.ItemIDGenerator;
import com.bfrisco.itemowners.util.ItemSerialization;
import com.bfrisco.itemowners.util.RateLimiter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Own implements CommandExecutor {
    private static final String OWNER_FORMAT = "Owner: %s";
    private static final String ITEM_ID_FORMAT = "Item ID: %s";
    private final RateLimiter limiter = new RateLimiter(60 * 1000);
    private final Plugin plugin;

    public Own(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player player = (Player) sender;

        if (!player.hasPermission(ItemOwnerPermissions.OWN)) {
            player.sendMessage(ChatColor.RED + "You do not have permission to own items.");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().equals(Material.AIR)) {
            player.sendMessage(ChatColor.RED + "Please hold the item you would like to own.");
            return true;
        }

        if (ItemOwners.isNotValid(item)) {
            player.sendMessage(ChatColor.RED + "That item cannot be owned.");
            return true;
        }

        if (ItemOwners.isOwned(item)) {
            player.sendMessage(ChatColor.RED + "That item already has an owner.");
            return true;
        }

        if (ItemOwners.getBukkitConfig().getStringList("disabled-worlds").contains(player.getWorld().getName())) {
            player.sendMessage(ChatColor.RED + "You cannot own items in this world " + player.getWorld().getName() + ".");
            return true;
        }

        if (limiter.isLimited(player.getUniqueId())) {
           long secondsLeft = limiter.secondsLeft(player.getUniqueId());
           player.sendMessage(ChatColor.RED + "Please wait " + secondsLeft + " seconds and try again.");
           return true;
        }

        ItemStack originalItem = item.clone();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String itemId = generateItemId();
                Bukkit.getScheduler().runTask(plugin, () -> prepareAndStoreItem(player, originalItem, itemId));
            } catch (Exception e) {
                ItemOwners.getBukkitLogger().warning("Error occurred while generating item ID: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(
                        ChatColor.RED + "Unexpected error occurred while generating and storing item ID."
                ));
            }
        });

        return true;
    }

    private String generateItemId() throws SQLException {
        String itemId;

        do {
            itemId = ItemIDGenerator.generate();
        } while (ItemRepository.exists(itemId));

        return itemId;
    }

    private void prepareAndStoreItem(Player player, ItemStack originalItem, String itemId) {
        if (!player.isOnline()) {
            return;
        }

        ItemStack currentItem = player.getInventory().getItemInMainHand();
        if (!currentItem.isSimilar(originalItem) || currentItem.getAmount() != originalItem.getAmount()) {
            player.sendMessage(ChatColor.RED + "The item in your main hand changed. Please try again.");
            return;
        }

        ItemStack ownedItem = currentItem.clone();
        ItemMeta meta = ownedItem.getItemMeta();
        List<Component> lore = meta.lore();
        lore = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
        lore.add(Component.text(String.format(OWNER_FORMAT, player.getName()), NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(String.format(ITEM_ID_FORMAT, itemId), NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        ownedItem.setItemMeta(meta);

        try {
            String data = ItemSerialization.toBase64(ownedItem);
            ItemRepository.save(itemId, player.getUniqueId().toString(), data);
        } catch (Exception e) {
            ItemOwners.getBukkitLogger().warning("Error occurred while storing item " + itemId + ": " + e.getMessage());
            player.sendMessage(ChatColor.RED + "Unexpected error occurred while storing the item ID.");
            return;
        }

        try {
            player.getInventory().setItemInMainHand(ownedItem);
        } catch (RuntimeException e) {
            try {
                ItemRepository.delete(itemId);
            } catch (SQLException rollbackError) {
                ItemOwners.getBukkitLogger().warning("Could not roll back unused item ID " + itemId + ": " + rollbackError.getMessage());
            }
            ItemOwners.getBukkitLogger().warning("Could not place ownership metadata on item " + itemId + ": " + e.getMessage());
            player.sendMessage(ChatColor.RED + "Unexpected error occurred while updating the owned item.");
            return;
        }

        ItemOwners.getBukkitLogger().info("Player " + player.getName() + " has owned an item with generated ID: " + itemId);
        player.sendMessage(ChatColor.GREEN + "Successfully owned item with generated ID: " + itemId + ". Please take a screenshot of your " +
                "owned tool with F2, keep the ID for your records");
    }
}
