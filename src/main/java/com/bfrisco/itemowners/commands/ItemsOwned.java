package com.bfrisco.itemowners.commands;

import com.bfrisco.itemowners.constants.ItemOwnerPermissions;
import com.bfrisco.itemowners.database.ItemPage;
import com.bfrisco.itemowners.database.ItemRepository;
import com.bfrisco.itemowners.exceptions.ChatMessageGeneratorException;
import com.bfrisco.itemowners.util.ChatMessageGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.logging.Level;

public class ItemsOwned implements CommandExecutor {
    private final Plugin plugin;

    public ItemsOwned(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return false;
        if (args == null || args.length == 0 || args.length > 2) return false;

        if (!player.hasPermission(ItemOwnerPermissions.LIST_OWN_TOOLS)
                && !player.hasPermission(ItemOwnerPermissions.LIST_ALL_TOOLS)) {
            sendError(player, "Permission not granted.");
            return true;
        }

        int pageNum = 1;
        if (args.length == 2) {
            try {
                pageNum = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sendError(player, "Page must be a number.");
                return true;
            }
        }

        if (pageNum < 1) {
            sendError(player, "Page must be at least 1.");
            return true;
        }

        String requestedPlayerName = args[0];
        OfflinePlayer owner = Bukkit.getOfflinePlayer(requestedPlayerName);
        if (!owner.hasPlayedBefore()) {
            sendError(player, "Player not found.");
            return true;
        }

        String ownerId = owner.getUniqueId().toString();
        if (!ownerId.equals(player.getUniqueId().toString())
                && !player.hasPermission(ItemOwnerPermissions.LIST_ALL_TOOLS)) {
            sendError(player, "Permission not granted.");
            return true;
        }

        String ownerName = owner.getName() == null ? requestedPlayerName : owner.getName();
        int finalPageNum = pageNum;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ItemPage page = ItemRepository.findByPlayerId(ownerId, finalPageNum);
                Bukkit.getScheduler().runTask(plugin, () -> sendItemsOwned(player, page, ownerName));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load items owned by " + ownerName, e);
                Bukkit.getScheduler().runTask(plugin, () -> sendInternalError(player));
            }
        });

        return true;
    }

    private void sendItemsOwned(Player player, ItemPage page, String ownerName) {
        if (!player.isOnline()) {
            return;
        }

        Component message = ChatMessageGenerator.generateHeader(page, ownerName);
        try {
            message = message.append(ChatMessageGenerator.generate(page, ownerName));
        } catch (ChatMessageGeneratorException e) {
            message = message.append(Component.text(e.getMessage(), NamedTextColor.RED));
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not display items owned by " + ownerName, e);
            sendInternalError(player);
            return;
        }

        player.sendMessage(message);
    }

    private void sendInternalError(Player player) {
        if (player.isOnline()) {
            sendError(player, "An internal error occurred while executing that command.");
        }
    }

    private static void sendError(Player player, String message) {
        player.sendMessage(Component.text(message, NamedTextColor.RED));
    }
}
