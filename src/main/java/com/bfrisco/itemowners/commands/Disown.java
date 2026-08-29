package com.bfrisco.itemowners.commands;

import com.bfrisco.itemowners.ItemOwners;
import com.bfrisco.itemowners.constants.ItemOwnerPermissions;
import com.bfrisco.itemowners.database.Item;
import com.bfrisco.itemowners.database.ItemEventRepository;
import com.bfrisco.itemowners.database.ItemRecoveryRepository;
import com.bfrisco.itemowners.database.ItemRepository;
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
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public class Disown implements CommandExecutor {
    private final Plugin plugin;

    public Disown(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) return false;

        if (!player.hasPermission(ItemOwnerPermissions.DISOWN)) {
            player.sendMessage(ChatColor.RED + "You do not have permission to disown items.");
            return true;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType().equals(Material.AIR)) {
            player.sendMessage(ChatColor.RED + "Please hold the item you would like to own.");
            return true;
        }

        String itemId = ItemOwners.getItemId(mainHand);
        if (itemId == null) {
            player.sendMessage(ChatColor.RED + "That item is not owned.");
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ItemMeta meta = mainHand.getItemMeta();
                Item item = ItemRepository.findById(itemId);

                if (item != null) { // Bug resolution: Items aren't in DB
                    if (!item.getOwnerId().equals(player.getUniqueId().toString()) && !player.hasPermission(ItemOwnerPermissions.DISOWN_ALL_TOOLS)) {
                        player.sendMessage(ChatColor.RED + "You do not own this item.");
                        return;
                    }
                }

                List<String> loreAfter = mainHand.getItemMeta().getLore().stream()
                        .filter(lore -> !lore.startsWith(ChatColor.RED + "Item ID: ") && !lore.startsWith(ChatColor.RED + "Owner: "))
                        .collect(Collectors.toList());
                meta.setLore(loreAfter);
                mainHand.setItemMeta(meta);
                player.sendMessage("Successfully disowned item.");

                ItemOwners.getBukkitLogger().info("Player " + player.getName() + " successfully disowned item " + itemId);

                ItemEventRepository.delete(itemId);
                ItemRepository.delete(itemId);
                ItemRecoveryRepository.invalidateOpenBeforePayment(itemId);
            } catch (Exception e) {
                e.printStackTrace();
                player.sendMessage("An internal error occurred while executing this command.");
            }
        });

        return true;
    }
}
