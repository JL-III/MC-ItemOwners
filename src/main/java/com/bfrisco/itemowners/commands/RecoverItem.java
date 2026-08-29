package com.bfrisco.itemowners.commands;

import com.bfrisco.itemowners.ItemOwners;
import com.bfrisco.itemowners.constants.ItemOwnerPermissions;
import com.bfrisco.itemowners.constants.LogMessages;
import com.bfrisco.itemowners.database.Item;
import com.bfrisco.itemowners.database.ItemEventRepository;
import com.bfrisco.itemowners.database.ItemEventType;
import com.bfrisco.itemowners.database.ItemRecovery;
import com.bfrisco.itemowners.database.ItemRecoveryRepository;
import com.bfrisco.itemowners.database.ItemRecoveryStatus;
import com.bfrisco.itemowners.database.ItemRepository;
import com.bfrisco.itemowners.util.ItemSerialization;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class RecoverItem implements CommandExecutor {
    private static final Pattern ITEM_ID_PATTERN = Pattern.compile("^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$");
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private final ItemOwners plugin;

    public RecoverItem(ItemOwners plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only a player can recover an item.");
            return true;
        }

        if (args.length != 1) {
            return false;
        }

        if (!player.hasPermission(ItemOwnerPermissions.RECOVER)) {
            player.sendMessage(ChatColor.RED + "You do not have permission to recover items.");
            return true;
        }

        if (!plugin.getConfig().getBoolean("recovery.enabled")) {
            player.sendMessage(ChatColor.RED + "Item recovery is disabled.");
            return true;
        }

        String itemId = args[0].toUpperCase();
        if (!ITEM_ID_PATTERN.matcher(itemId).matches()) {
            player.sendMessage(ChatColor.RED + "Invalid item ID.");
            return true;
        }

        double fee = plugin.getConfig().getDouble("recovery.fee");
        if (!Double.isFinite(fee) || fee < 0D) {
            player.sendMessage(ChatColor.RED + "Item recovery is unavailable because its fee is misconfigured.");
            plugin.getLogger().severe("recovery.fee must be a finite, non-negative number.");
            return true;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.RED + "Make one empty inventory slot before recovering this item.");
            return true;
        }

        if (fee > 0D && plugin.getEconomy() == null) {
            player.sendMessage(ChatColor.RED + "Item recovery is unavailable because no Vault economy provider is installed.");
            return true;
        }

        UUID playerId = player.getUniqueId();
        int maxAgeDays = plugin.getConfig().getInt("recovery.max-age-days");
        if (maxAgeDays < 0) {
            player.sendMessage(ChatColor.RED + "Item recovery is unavailable because its recovery window is misconfigured.");
            plugin.getLogger().severe("recovery.max-age-days must be zero or greater.");
            return true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> reserveRecovery(
                player,
                playerId,
                itemId,
                fee,
                maxAgeDays
        ));
        return true;
    }

    private void reserveRecovery(
            Player player,
            UUID playerId,
            String itemId,
            double fee,
            int maxAgeDays
    ) {
        try {
            ItemRecovery recovery = ItemRecoveryRepository.findLatestByItemId(itemId);
            if (recovery == null) {
                message(player, ChatColor.RED + "No recoverable despawn was recorded for that item after recovery was enabled.");
                return;
            }

            if (!recovery.getOwnerId().equals(playerId.toString())) {
                message(player, ChatColor.RED + "You do not own that item.");
                return;
            }

            if (maxAgeDays > 0 && System.currentTimeMillis() - recovery.getLostAt() > maxAgeDays * DAY_MILLIS) {
                message(player, ChatColor.RED + "That item's recovery window has expired.");
                return;
            }

            if (recovery.getStatus() != ItemRecoveryStatus.AVAILABLE) {
                message(player, statusMessage(recovery.getStatus()));
                return;
            }

            Item currentItem = ItemRepository.findById(itemId);
            if (currentItem == null || !recovery.getOwnerId().equals(currentItem.getOwnerId())) {
                if (!ItemRecoveryRepository.invalidateAvailable(recovery.getId())) {
                    message(player, ChatColor.RED + "That recovery changed while ownership was being checked. No money was taken.");
                    return;
                }
                message(player, ChatColor.RED + "That item is no longer owned, so its recovery was invalidated.");
                return;
            }

            if (!ItemRecoveryRepository.reserve(recovery.getId(), playerId.toString(), fee)) {
                message(player, ChatColor.RED + "That recovery was already claimed. No money was taken.");
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> withdraw(player, recovery, fee));
        } catch (Exception e) {
            plugin.getLogger().severe("Could not reserve recovery for " + itemId + ": " + e.getMessage());
            message(player, ChatColor.RED + "An internal error occurred. No money was taken.");
        }
    }

    private void withdraw(Player player, ItemRecovery recovery, double fee) {
        if (!player.isOnline() || player.getInventory().firstEmpty() == -1) {
            releaseBeforeCharge(recovery.getId());
            if (player.isOnline()) {
                player.sendMessage(ChatColor.RED + "Make one empty inventory slot before recovering this item.");
            }
            return;
        }

        ItemStack item;
        try {
            item = ItemSerialization.fromBase64(recovery.getItemData());
        } catch (IOException | RuntimeException e) {
            releaseBeforeCharge(recovery.getId());
            plugin.getLogger().severe("Recovery " + recovery.getId() + " has invalid item data: " + e.getMessage());
            player.sendMessage(ChatColor.RED + "The saved item data is invalid. No money was taken; please contact staff.");
            return;
        }

        if (ItemOwners.isNotValid(item) || !recovery.getItemId().equals(ItemOwners.getItemId(item))) {
            releaseBeforeCharge(recovery.getId());
            plugin.getLogger().severe("Recovery " + recovery.getId() + " failed its item identity check.");
            player.sendMessage(ChatColor.RED + "The saved item failed its identity check. No money was taken; please contact staff.");
            return;
        }

        markChargeIntent(player, recovery, item, fee);
    }

    private void markChargeIntent(Player player, ItemRecovery recovery, ItemStack item, double fee) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!ItemRecoveryRepository.markChargeIntent(recovery.getId())) {
                    message(player, ChatColor.RED + "The recovery state changed before payment. No money was taken.");
                    return;
                }
                if (!ItemRecoveryRepository.beginWithdrawal(recovery.getId())) {
                    message(player, ChatColor.RED + "The recovery was locked before payment. No money was taken.");
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> performWithdrawal(player, recovery, item, fee));
            } catch (Exception e) {
                ambiguous(recovery, "Could not record the payment intent: " + e.getMessage());
                message(player, ChatColor.RED + "Payment was not started, but the recovery is locked for staff review.");
            }
        });
    }

    private void performWithdrawal(Player player, ItemRecovery recovery, ItemStack item, double fee) {
        if (!player.isOnline() || player.getInventory().firstEmpty() == -1) {
            cancelKnownUncharged(recovery, player, "You must remain online with one empty inventory slot.");
            return;
        }

        Economy economy = plugin.getEconomy();
        double chargedAmount = 0D;
        if (fee > 0D) {
            if (economy == null) {
                cancelKnownUncharged(
                        recovery,
                        player,
                        "Item recovery is unavailable because no Vault economy provider is installed."
                );
                return;
            }

            EconomyResponse response;
            try {
                response = economy.withdrawPlayer(player, fee);
            } catch (RuntimeException e) {
                // A provider may debit an account and then throw. Reopening the claim here could charge twice.
                ambiguous(recovery, "Vault threw during withdrawal; payment outcome is unknown: " + e.getMessage());
                player.sendMessage(ChatColor.RED + "The payment result is unknown. Contact staff and do not retry.");
                return;
            }

            if (!response.transactionSuccess()) {
                cancelKnownUncharged(
                        recovery,
                        player,
                        "Recovery payment failed: " + safeEconomyError(response.errorMessage)
                );
                return;
            }

            chargedAmount = response.amount;
            if (!Double.isFinite(chargedAmount) || chargedAmount < 0D) {
                ambiguous(recovery, "Vault reported an invalid charged amount after a successful withdrawal.");
                player.sendMessage(ChatColor.RED + "The payment amount is unknown. Contact staff and do not retry.");
                return;
            }
        }

        markCharged(player, recovery, item, chargedAmount);
    }

    private void markCharged(Player player, ItemRecovery recovery, ItemStack item, double chargedAmount) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!ItemRecoveryRepository.markCharged(recovery.getId(), chargedAmount)) {
                    refundAndCancel(player, recovery, chargedAmount, "The recovery state changed before delivery.");
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> prepareIssue(player, recovery, item, chargedAmount));
            } catch (Exception e) {
                plugin.getLogger().severe("Could not record payment for recovery " + recovery.getId() + ": " + e.getMessage());
                refundAndCancel(player, recovery, chargedAmount, "The payment could not be recorded.");
            }
        });
    }

    private void prepareIssue(Player player, ItemRecovery recovery, ItemStack item, double chargedAmount) {
        if (!player.isOnline() || player.getInventory().firstEmpty() == -1) {
            refundAndCancel(player, recovery, chargedAmount, "You must remain online with one empty inventory slot.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!ItemRecoveryRepository.markIssued(recovery.getId())) {
                    refundAndCancel(player, recovery, chargedAmount, "The item could not be reserved for delivery.");
                    return;
                }
                if (!ItemRecoveryRepository.beginDelivery(recovery.getId())) {
                    ambiguous(recovery, "The item was issued but could not enter the delivery phase.");
                    message(player, ChatColor.RED + "Delivery could not be committed safely. Contact staff and do not retry.");
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> issue(player, recovery, item, chargedAmount));
            } catch (Exception e) {
                plugin.getLogger().severe("Could not mark recovery " + recovery.getId() + " for delivery: " + e.getMessage());
                ambiguous(recovery, "The item could not enter the delivery phase: " + e.getMessage());
                message(player, ChatColor.RED + "Delivery could not be committed safely. Contact staff and do not retry.");
            }
        });
    }

    private void issue(Player player, ItemRecovery recovery, ItemStack item, double chargedAmount) {
        if (!player.isOnline()) {
            refundDeliveryAndCancel(player, recovery, chargedAmount, "Player disconnected before item delivery.");
            return;
        }

        Location location = player.getLocation();
        String playerId = player.getUniqueId().toString();
        String world = location.getWorld() == null ? null : location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            refundDeliveryAndCancel(player, recovery, chargedAmount, "Inventory insertion failed before item delivery.");
            return;
        }

        finalizeDelivery(recovery, playerId, world, x, y, z);
        try {
            String formattedFee = formatFee(chargedAmount);
            player.sendMessage(ChatColor.GREEN + "Recovered item " + recovery.getItemId() + " for " + formattedFee + ".");
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Recovery " + recovery.getId() + " was delivered, but its success message failed: " + e.getMessage());
        }
        ItemOwners.getBukkitLogger().info(String.format(LogMessages.RECOVERED, recovery.getItemId(), player.getName()));
    }

    private void finalizeDelivery(
            ItemRecovery recovery,
            String playerId,
            String world,
            int x,
            int y,
            int z
    ) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!ItemRecoveryRepository.complete(recovery.getId())) {
                    ambiguous(recovery, "The delivered recovery could not be marked complete.");
                    return;
                }
                ItemEventRepository.save(ItemEventType.RECOVERED, recovery.getItemId(), playerId, world, x, y, z);
            } catch (Exception e) {
                ambiguous(recovery, "The delivered recovery could not be finalized: " + e.getMessage());
            }
        });
    }

    private void releaseBeforeCharge(long recoveryId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ItemRecoveryRepository.release(recoveryId);
            } catch (Exception e) {
                ambiguous(recoveryId, "An unpaid reservation could not be released: " + e.getMessage());
            }
        });
    }

    private void cancelKnownUncharged(ItemRecovery recovery, Player player, String reason) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!ItemRecoveryRepository.cancelBeforeIssue(recovery.getId())) {
                    ambiguous(recovery, reason + " The unpaid claim could not be reopened.");
                }
            } catch (Exception e) {
                ambiguous(recovery, reason + " The unpaid claim could not be reopened: " + e.getMessage());
            }
            message(player, ChatColor.RED + reason + " No money was taken.");
        });
    }

    private void refundAndCancel(Player player, ItemRecovery recovery, double chargedAmount, String reason) {
        startRefund(player, recovery, chargedAmount, reason, false);
    }

    private void refundDeliveryAndCancel(Player player, ItemRecovery recovery, double chargedAmount, String reason) {
        startRefund(player, recovery, chargedAmount, reason, true);
    }

    private void startRefund(
            Player player,
            ItemRecovery recovery,
            double chargedAmount,
            String reason,
            boolean deliveryClaimed
    ) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean refundStarted = deliveryClaimed
                        ? ItemRecoveryRepository.beginDeliveryRefund(recovery.getId())
                        : ItemRecoveryRepository.beginRefundBeforeDelivery(recovery.getId());
                if (!refundStarted) {
                    ambiguous(recovery, reason + " A refund was not started because the recovery state changed.");
                    message(player, ChatColor.RED + reason + " Contact staff and do not retry.");
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> performRefund(player, recovery, chargedAmount, reason));
            } catch (Exception e) {
                ambiguous(recovery, reason + " A refund intent could not be recorded: " + e.getMessage());
                message(player, ChatColor.RED + reason + " Contact staff and do not retry.");
            }
        });
    }

    private void performRefund(Player player, ItemRecovery recovery, double chargedAmount, String reason) {
        if (chargedAmount > 0D) {
            Economy economy = plugin.getEconomy();
            if (economy == null) {
                ambiguous(recovery, reason + " Vault was unavailable when the refund was attempted.");
                message(player, ChatColor.RED + reason + " The refund could not be verified; contact staff and do not retry.");
                return;
            }

            try {
                EconomyResponse response = economy.depositPlayer(player, chargedAmount);
                if (!response.transactionSuccess()) {
                    ambiguous(recovery, reason + " Vault rejected the refund: " + safeEconomyError(response.errorMessage));
                    message(player, ChatColor.RED + reason + " The refund failed; contact staff and do not retry.");
                    return;
                }
            } catch (RuntimeException e) {
                // A provider may credit the account and then throw. Keep REFUND_INTENT locked to avoid a double refund.
                ambiguous(recovery, reason + " Vault threw during refund; the refund outcome is unknown: " + e.getMessage());
                message(player, ChatColor.RED + reason + " The refund result is unknown; contact staff and do not retry.");
                return;
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!ItemRecoveryRepository.finishRefund(recovery.getId())) {
                    ambiguous(recovery, reason + " The successful refund could not be recorded.");
                    message(player, ChatColor.RED + reason + " The refund was sent, but staff must unlock the recovery.");
                    return;
                }
                message(player, ChatColor.RED + reason
                        + (chargedAmount > 0D ? " Your payment was refunded." : " No money was taken."));
            } catch (Exception e) {
                ambiguous(recovery, reason + " The successful refund could not be recorded: " + e.getMessage());
                message(player, ChatColor.RED + reason + " The refund was sent, but staff must unlock the recovery.");
            }
        });
    }

    private String formatFee(double fee) {
        Economy economy = plugin.getEconomy();
        return economy == null ? String.format("%.2f", fee) : economy.format(fee);
    }

    private String statusMessage(ItemRecoveryStatus status) {
        return switch (status) {
            case COMPLETED -> ChatColor.RED + "That despawned item has already been recovered.";
            case INVALIDATED -> ChatColor.RED + "That recovery was invalidated because the item is no longer owned.";
            case CONFLICTED -> ChatColor.RED + "Duplicate item records were detected. Recovery is locked for staff review.";
            case RESERVED, CHARGE_INTENT, WITHDRAWING, CHARGED, ISSUED, DELIVERING, REFUND_INTENT ->
                    ChatColor.RED + "That recovery is already in progress. If it did not finish, contact staff.";
            case AVAILABLE -> ChatColor.RED + "That recovery changed while it was being requested. Please try again.";
        };
    }

    private String safeEconomyError(String error) {
        return error == null || error.isBlank() ? "insufficient funds or provider rejection" : error;
    }

    private void message(Player player, String text) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage(text);
            }
        });
    }

    private void ambiguous(ItemRecovery recovery, String reason) {
        ambiguous(recovery.getId(), reason);
    }

    private void ambiguous(long recoveryId, String reason) {
        plugin.getLogger().severe("Recovery " + recoveryId + " requires staff review: " + reason);
    }
}
