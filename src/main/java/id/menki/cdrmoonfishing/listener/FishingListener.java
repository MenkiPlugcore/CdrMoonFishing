package id.menki.cdrmoonfishing.listener;

import id.menki.cdrmoonfishing.bait.BaitManager;
import id.menki.cdrmoonfishing.fishing.FishingManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class FishingListener implements Listener {
    private final FishingManager fishingManager;
    private final BaitManager baitManager;

    public FishingListener(FishingManager fishingManager, BaitManager baitManager) {
        this.fishingManager = fishingManager;
        this.baitManager = baitManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (fishingManager.isActive(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }

        switch (event.getState()) {
            case BITE -> fishingManager.prepareEncounter(event.getPlayer(), event.getHook().getLocation());
            case CAUGHT_FISH -> {
                event.setCancelled(true);
                event.setExpToDrop(0);
                Location hookLocation = event.getHook().getLocation().clone();
                if (event.getCaught() != null) event.getCaught().remove();
                event.getHook().remove();
                fishingManager.startPrepared(event.getPlayer(), hookLocation);
            }
            case FAILED_ATTEMPT, REEL_IN, IN_GROUND -> fishingManager.clearPrepared(event.getPlayer());
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean leftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        if (!rightClick && !leftClick) return;

        // Bait selection stays on use/right-click so attack/left-click can always be used as reel input.
        if (rightClick && baitManager.trySelect(event.getPlayer(), event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            return;
        }

        if (!fishingManager.isActive(event.getPlayer())) return;
        if (event.getPlayer().getInventory().getItemInMainHand().getType() != Material.FISHING_ROD) return;

        // During the custom minigame both Java use/right-click and Bedrock-friendly attack/left-click
        // become the same reel pulse. Cancelling prevents vanilla rod/block interactions from interfering.
        event.setCancelled(true);
        fishingManager.reelPulse(event.getPlayer());
    }

    /**
     * Geyser/mobile clients can sometimes surface the attack button primarily as an arm swing.
     * This fallback makes that swing a reel pulse too. FishingManager already has a reel cooldown,
     * so if a client fires both PlayerInteractEvent and PlayerAnimationEvent for one tap, it cannot
     * double-count the input.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onArmSwing(PlayerAnimationEvent event) {
        if (!fishingManager.isActive(event.getPlayer())) return;
        if (event.getPlayer().getInventory().getItemInMainHand().getType() != Material.FISHING_ROD) return;
        fishingManager.reelPulse(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        fishingManager.cancel(event.getPlayer(), false);
        baitManager.clearSelection(event.getPlayer());
    }
}
