package com.dragonegg;

import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DragonEggListener implements Listener {

    private final DragonEggPlugin plugin;
    // Track who currently has the glow so we can remove it cleanly
    private final Set<UUID> glowingPlayers = new HashSet<>();

    // The exact custom name text used in your command (the ★ THE DRAGON EGG ★ part, plain string match)
    // We'll match by checking if the display name contains the plain-text star + "THE DRAGON EGG"
    private static final String EGG_NAME_CONTAINS = "THE DRAGON EGG";

    public DragonEggListener(DragonEggPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Prevent the special dragon egg from being placed.
     * We check BOTH hands explicitly because getHand() can be unreliable
     * for offhand placements in some Paper versions.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        PlayerInventory inv = event.getPlayer().getInventory();
        ItemStack mainHand = inv.getItemInMainHand();
        ItemStack offHand  = inv.getItemInOffHand();

        boolean specialInMain = mainHand.getType() == Material.DRAGON_EGG && isSpecialEgg(mainHand);
        boolean specialInOff  = offHand.getType()  == Material.DRAGON_EGG && isSpecialEgg(offHand);

        if (!specialInMain && !specialInOff) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage(
            Component.text("✦ This relic cannot be placed — it holds power beyond this world. ✦")
                .color(TextColor.color(0xac0cff))
        );
    }

    /**
     * Block the egg from being placed into any external inventory via clicking.
     *
     * Cases we need to catch:
     *  1. Player clicks directly into the top (external) inventory with the egg on cursor.
     *  2. Player shift-clicks the egg from their own inventory → it moves to the top inventory.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // Only care when there IS a top inventory that isn't the player's own inventory
        Inventory topInv = event.getView().getTopInventory();
        if (topInv.getType() == InventoryType.PLAYER || topInv.getType() == InventoryType.CRAFTING) return;

        boolean shouldCancel = false;

        // Case 1: egg is on cursor and player clicks a slot in the top inventory
        ItemStack cursor = event.getCursor();
        Inventory clicked = event.getClickedInventory();
        if (cursor != null && cursor.getType() == Material.DRAGON_EGG && isSpecialEgg(cursor)
                && clicked != null && clicked.equals(topInv)) {
            shouldCancel = true;
        }

        // Case 2: egg is in a bottom-inventory slot and player shift-clicks it
        if (!shouldCancel) {
            ItemStack current = event.getCurrentItem();
            if (current != null && current.getType() == Material.DRAGON_EGG && isSpecialEgg(current)
                    && (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY)) {
                // MOVE_TO_OTHER_INVENTORY from the bottom inventory means it would go to the top
                if (clicked != null && clicked.equals(event.getView().getBottomInventory())) {
                    shouldCancel = true;
                }
            }
        }

        if (shouldCancel) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player p) {
                p.sendMessage(Component.text("✦ This relic cannot be stored away. ✦")
                        .color(TextColor.color(0xac0cff)));
            }
        }
    }

    /**
     * Block the egg from being dragged into any external inventory.
     * InventoryDragEvent fires when the player holds a click and drags across slots.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        if (topInv.getType() == InventoryType.PLAYER || topInv.getType() == InventoryType.CRAFTING) return;

        ItemStack dragged = event.getOldCursor();
        if (dragged.getType() != Material.DRAGON_EGG || !isSpecialEgg(dragged)) return;

        // If any of the raw slots being dragged into belong to the top inventory, block it
        int topSize = topInv.getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player p) {
                    p.sendMessage(Component.text("✦ This relic cannot be stored away. ✦")
                            .color(TextColor.color(0xac0cff)));
                }
                return;
            }
        }
    }

    /**
     * Block hoppers, droppers, and other block automation from moving the egg
     * into any inventory.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        ItemStack item = event.getItem();
        if (item.getType() == Material.DRAGON_EGG && isSpecialEgg(item)) {
            event.setCancelled(true);
        }
    }

    /**
     * Block the egg from being placed into an item frame.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemFrameChange(PlayerItemFrameChangeEvent event) {
        if (event.getAction() != PlayerItemFrameChangeEvent.ItemFrameChangeAction.PLACE) return;
        ItemStack item = event.getItemStack();
        if (item.getType() == Material.DRAGON_EGG && isSpecialEgg(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("✦ This relic cannot be stored away. ✦")
                    .color(TextColor.color(0xac0cff)));
        }
    }

    /**
     * Prevent the player from dropping the egg (Q key or drag-out-of-inventory).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (item.getType() == Material.DRAGON_EGG && isSpecialEgg(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("✦ This relic refuses to leave your grasp. ✦")
                    .color(TextColor.color(0xac0cff)));
        }
    }

 to players who have the
     * special egg anywhere in their inventory, removes it when they don't.
     */
    public void tickGlowCheck() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (hasSpecialEggInInventory(player)) {
                if (!glowingPlayers.contains(player.getUniqueId())) {
                    glowingPlayers.add(player.getUniqueId());
                }
                // Re-apply with a short duration so it never expires while held (20 ticks = 1s, refreshed every 0.5s)
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.GLOWING,
                    30,      // duration in ticks (a bit longer than the tick interval)
                    0,       // amplifier
                    false,   // ambient (reduces particles)
                    false,   // show particles
                    true     // show icon
                ));
            } else {
                if (glowingPlayers.remove(player.getUniqueId())) {
                    player.removePotionEffect(PotionEffectType.GLOWING);
                }
            }
        }
    }

    /**
     * Remove glow from all tracked players (called on plugin disable).
     */
    public void removeAllGlow() {
        for (UUID uuid : glowingPlayers) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                player.removePotionEffect(PotionEffectType.GLOWING);
            }
        }
        glowingPlayers.clear();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean hasSpecialEggInInventory(Player player) {
        PlayerInventory inv = player.getInventory();

        // getContents() covers slots 0-35 (hotbar + main inventory) but NOT the offhand.
        // We must check getItemInOffHand() explicitly.
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == Material.DRAGON_EGG && isSpecialEgg(item)) {
                return true;
            }
        }

        // Explicit offhand check
        ItemStack offHand = inv.getItemInOffHand();
        if (offHand.getType() == Material.DRAGON_EGG && isSpecialEgg(offHand)) {
            return true;
        }

        return false;
    }

    /**
     * Checks whether an ItemStack is the special dragon egg by reading its
     * display name as a plain string and looking for the unique title text.
     */
    private boolean isSpecialEgg(ItemStack item) {
        if (!item.hasItemMeta()) return false;
        Component displayName = item.getItemMeta().displayName();
        if (displayName == null) return false;
        // Serialize to plain text so we don't have to deal with Component trees
        String plain = PlainTextComponentSerializer.plainText().serialize(displayName);
        return plain.contains(EGG_NAME_CONTAINS);
    }
}
