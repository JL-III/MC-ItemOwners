package com.bfrisco.itemowners.commands;

import com.bfrisco.itemowners.constants.ItemOwnerPermissions;
import com.bfrisco.itemowners.database.Item;
import com.bfrisco.itemowners.database.ItemEventPage;
import com.bfrisco.itemowners.database.ItemEventRepository;
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
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class ItemHistory implements CommandExecutor {
    private static final Pattern PATTERN = Pattern.compile("^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$");

    private final Plugin plugin;

    public ItemHistory(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return false;
        if (args == null || args.length == 0 || args.length > 2) return false;

        if (!player.hasPermission(ItemOwnerPermissions.VIEW_OWN_TOOL_EVENTS)
                && !player.hasPermission(ItemOwnerPermissions.VIEW_ALL_TOOL_EVENTS)) {
            sendError(player, "Permission not granted.");
            return true;
        }

        String itemId = args[0];
        if (!PATTERN.matcher(itemId).matches()) {
            sendError(player, "Invalid item ID.");
            return true;
        }

        int pageNum = 1;
        if (args.length == 2) {
            try {
                pageNum = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                return true;
            }
        }

        if (pageNum < 1) {
            sendError(player, "Page must be at least 1.");
            return true;
        }

        int finalPageNum = pageNum;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Item item = ItemRepository.findById(itemId);
                ItemEventPage page = item == null ? null : ItemEventRepository.findByItemId(itemId, finalPageNum);
                Bukkit.getScheduler().runTask(plugin, () -> sendHistory(player, itemId, item, page));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Could not load history for item " + itemId, e);
                Bukkit.getScheduler().runTask(plugin, () -> sendInternalError(player));
            }
        });

        return true;
    }

    private void sendHistory(Player player, String itemId, Item item, ItemEventPage page) {
        if (!player.isOnline()) {
            return;
        }

        if (item == null) {
            sendError(player, "That item ID was not found.");
            return;
        }

        boolean isOwner = item.getOwnerId().equals(player.getUniqueId().toString());
        if (isOwner && !player.hasPermission(ItemOwnerPermissions.VIEW_OWN_TOOL_EVENTS)) {
            sendError(player, "You don't have permission to view your own item events.");
            return;
        }

        if (!isOwner && !player.hasPermission(ItemOwnerPermissions.VIEW_ALL_TOOL_EVENTS)) {
            sendError(player, "You don't have permission to view other's tool events.");
            return;
        }

        String ownerName = resolveOwnerName(item.getOwnerId());
        try {
            Component message = ChatMessageGenerator.generateHeader(page, itemId, item.getData(), ownerName);
            try {
                message = message.append(ChatMessageGenerator.generate(page));
            } catch (ChatMessageGeneratorException e) {
                message = message.append(Component.text(e.getMessage(), NamedTextColor.RED));
            }

            player.sendMessage(message);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not display history for item " + itemId, e);
            sendInternalError(player);
        }
    }

    private String resolveOwnerName(String ownerId) {
        try {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(UUID.fromString(ownerId));
            return owner.getName() == null ? ownerId : owner.getName();
        } catch (IllegalArgumentException ignored) {
            return ownerId;
        }
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
