package com.auracode.ispec.listener;

import java.util.Locale;
import com.auracode.ispec.IspecPlugin;
import com.auracode.ispec.util.DateFormatUtil;
import com.auracode.ispec.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Guards spectator behaviour and cleans up sessions on logout.
 */
public final class SessionListener implements Listener {

   private static final String NOTIFY_PERMISSION = "ispec.notify";
   private static final String ALL_PERMISSION = "*";

   private final IspecPlugin plugin;

   public SessionListener(@NotNull IspecPlugin plugin) {
      this.plugin = plugin;
   }

   @EventHandler
   public void onQuit(@NotNull PlayerQuitEvent event) {
      this.onLogout(event.getPlayer());
   }

   @EventHandler
   public void onKick(@NotNull PlayerKickEvent event) {
      this.onLogout(event.getPlayer());
   }

   @EventHandler
   public void onInteract(@NotNull PlayerInteractEvent event) {
      if (this.plugin.getSessionManager().isSpectator(event.getPlayer())) {
         event.setCancelled(true);
      }
   }

   @EventHandler
   public void onCommand(@NotNull PlayerCommandPreprocessEvent event) {
      Player spectator = event.getPlayer();
      this.plugin.getSessionManager().findSpectatorSession(spectator).ifPresent(session -> {
         String message = event.getMessage();
         session.logger().logCommand(message);
         String root = message.split(" ")[0].toLowerCase(Locale.ROOT);
         if (this.plugin.getIspecConfig().getBlockedCommands().contains(root)) {
            event.setCancelled(true);
            MessageUtil.sendPrefixed(spectator, this.plugin.getIspecConfig().getPrefix(),
               this.plugin.getIspecConfig().getMessages().getString("command-blocked", ""));
         }
      });
   }

   @EventHandler
   public void onGamemodeChange(@NotNull PlayerGameModeChangeEvent event) {
      Player spectator = event.getPlayer();
      this.plugin.getSessionManager().findSpectatorSession(spectator).ifPresent(session -> {
         var config = this.plugin.getIspecConfig();
         event.setCancelled(true);
         if (config.isStopOnGamemodeChange()) {
            Player suspect = this.plugin.getServer().getPlayer(session.suspectId());
            this.plugin.getSessionManager().stop(session);
            MessageUtil.sendPrefixed(spectator, config.getPrefix(),
               config.getMessages().getString("stopped-by-gamemode-change", ""),
               DateFormatUtil.now(), suspect != null ? suspect.getName() : "unknown");
         } else {
            MessageUtil.sendPrefixed(spectator, config.getPrefix(),
               config.getMessages().getString("gamemode-locked", ""));
         }
      });
   }

   private void onLogout(@NotNull Player player) {
      this.plugin.getSessionManager().findSession(player).ifPresent(session -> {
         Player suspect = this.plugin.getServer().getPlayer(session.suspectId());
         this.plugin.getSessionManager().stop(session);
         Player spectator = this.plugin.getServer().getPlayer(session.spectatorId());
         if (spectator != null && !spectator.equals(player)) {
            var config = this.plugin.getIspecConfig();
            MessageUtil.sendPrefixed(spectator, config.getPrefix(),
               config.getMessages().getString("suspect-left", ""),
               suspect != null ? suspect.getName() : player.getName(), DateFormatUtil.now());
         }
      });
   }
}
