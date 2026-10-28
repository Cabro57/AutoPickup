package us.thezircon.play.autopickup.listeners;

import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import us.thezircon.play.autopickup.AutoPickup;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Picks up the loot of ItemsAdder crops.
 * <p>
 * ItemsAdder crops are not broken like blocks: the player hits an invisible hitbox or right-clicks it and ItemsAdder
 * spawns the loot itself, so there is no BlockBreakEvent/BlockDropItemEvent for AutoPickup to work with. Instead
 * this remembers when a player last clicked, swung their arm or broke a block, and an ItemsAdder item that appears
 * next to such a player right after goes into their inventory (if they have AutoPickup enabled).
 * Only registered when ItemsAdder is installed; can be switched off with {@code itemsAdderCropDrops.enabled}.
 */
public class ItemsAdderCropListener implements Listener {

    private static final AutoPickup PLUGIN = AutoPickup.getPlugin(AutoPickup.class);

    /** Time of the last click, swing or block break of each player in milliseconds. */
    private final Map<UUID, Long> acted = new HashMap<>();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        mark(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerAnimationEvent e) {
        mark(e.getPlayer());
    }

    // Also when cancelled: ItemsAdder cancels the vanilla break of its crops and spawns the loot on its own.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent e) {
        mark(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        acted.remove(e.getPlayer().getUniqueId());
    }

    private void mark(Player player) {
        if (PLUGIN.autopickup_list.contains(player)) {
            acted.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent e) {
        // Nobody with AutoPickup clicked lately, or a plain item: nothing to do (this runs for every dropped item).
        if (acted.isEmpty() || !e.getEntity().getItemStack().hasItemMeta()) {
            return;
        }
        if (!PLUGIN.getConfig().getBoolean("itemsAdderCropDrops.enabled", true)) {
            return;
        }
        if (AutoPickup.worldsBlacklist != null && AutoPickup.worldsBlacklist.contains(e.getLocation().getWorld().getName())) {
            return;
        }
        // Items dropped by players or marked to be ignored are left alone, like in ItemSpawnEventListener
        Item entity = e.getEntity();
        if (AutoPickup.droppedItems.contains(entity.getUniqueId()) || entity.hasMetadata("ap-ignore")) {
            return;
        }
        // Drops of a block the player broke are already handled by ItemSpawnEventListener (which runs before this
        // and removes the item); doing it again here would give the item twice.
        Location at = e.getLocation();
        String key = at.getBlockX() + ";" + at.getBlockY() + ";" + at.getBlockZ() + ";" + at.getWorld();
        if (entity.isDead() || AutoPickup.customItemPatch.containsKey(key)) {
            return;
        }

        Player player = nearestActor(e.getLocation());
        if (player == null || CustomStack.byItemStack(entity.getItemStack()) == null) {
            return;
        }

        HashMap<Integer, ItemStack> leftOver = player.getInventory().addItem(entity.getItemStack());
        if (leftOver.isEmpty()) {
            entity.remove();
        } else {
            entity.setItemStack(leftOver.values().iterator().next());
        }
    }

    private Player nearestActor(Location at) {
        long now = System.currentTimeMillis();
        long window = PLUGIN.getConfig().getLong("itemsAdderCropDrops.clickWindowMillis", 250);
        double radius = PLUGIN.getConfig().getDouble("itemsAdderCropDrops.radius", 6);
        double best = radius * radius;
        Player found = null;
        for (Map.Entry<UUID, Long> entry : acted.entrySet()) {
            if (now - entry.getValue() > window) {
                continue;
            }
            Player player = PLUGIN.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || !at.getWorld().equals(player.getWorld())) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(at);
            if (distance <= best) {
                best = distance;
                found = player;
            }
        }
        return found;
    }

}
