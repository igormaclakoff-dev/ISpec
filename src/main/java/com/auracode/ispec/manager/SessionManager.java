package com.auracode.ispec.manager;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.auracode.ispec.IspecPlugin;
import com.auracode.ispec.model.SessionDuration;
import com.auracode.ispec.model.SessionLogger;
import com.auracode.ispec.model.WatchSession;
import com.auracode.ispec.util.DateFormatUtil;
import com.auracode.ispec.util.MessageUtil;
import com.auracode.ispec.util.WebhookUtil;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks active spectation sessions and manages their lifecycle.
 */
public final class SessionManager {

   private static final String NOTIFY_PERMISSION = "ispec.notify";
   private static final String BYPASS_PERMISSION = "ispec.bypass";
   private static final long START_DELAY_TICKS = 40L;
   private static final int INFINITE_DURATION = -1;
   private static final int INFINITE_AMPLIFIER = 0;

   private final IspecPlugin plugin;
   private final Map<UUID, WatchSession> activeSessions = new ConcurrentHashMap<>();

   public SessionManager(@NotNull IspecPlugin plugin) {
      this.plugin = plugin;
      int interval = plugin.getIspecConfig().getActionBarIntervalTicks();
      Bukkit.getScheduler().runTaskTimer(plugin, this::tickSessions, START_DELAY_TICKS, interval);
   }

   private void tickSessions() {
      var config = this.plugin.getIspecConfig();
      double maxDistanceSq = config.getMaxDistance() * config.getMaxDistance();
      for (WatchSession session : this.activeSessions.values()) {
         Player spectator = Bukkit.getPlayer(session.spectatorId());
         Player suspect = Bukkit.getPlayer(session.suspectId());
         if (spectator == null || suspect == null) {
            continue;
         }
         if (config.isActionBarEnabled()) {
            spectator.sendActionBar(MessageUtil.format(
               config.getMessages().getString("action-bar", "{0} | Ping: {1}ms"),
               suspect.getName(), suspect.getPing()));
         }
         session.logger().logLocation();

         Location from = spectator.getLocation();
         Location to = suspect.getLocation();
         boolean sameWorld = from.getWorld() != null && from.getWorld().equals(to.getWorld());
         if (!sameWorld || from.distanceSquared(to) > maxDistanceSq) {
            MessageUtil.sendPrefixed(spectator, config.getPrefix(), config.getMessages().getString("too-far", ""));
            spectator.teleportAsync(to);
         }
      }
   }

   public boolean isSpectator(@NotNull Player player) {
      return this.activeSessions.containsKey(player.getUniqueId());
   }

   public boolean isSuspect(@NotNull Player player) {
      UUID uniqueId = player.getUniqueId();
      return this.activeSessions.values().stream().anyMatch(session -> session.suspectId().equals(uniqueId));
   }

   public boolean isInSession(@NotNull Player player) {
      return this.isSpectator(player) || this.isSuspect(player);
   }

   public boolean tryStart(@NotNull Player spectator, @NotNull Player suspect, @NotNull String reason) {
      UUID spectatorId = spectator.getUniqueId();
      if (this.activeSessions.containsKey(spectatorId)) {
         return false;
      }
      var config = this.plugin.getIspecConfig();

      var logger = new SessionLogger(this.plugin, spectator, suspect);
      if (!reason.isEmpty()) {
         logger.log("Reason: " + reason);
      }
      BossBar bossBar = BossBar.bossBar(
         MessageUtil.format(config.getLangBossBarName(), suspect.getName()),
         1.0F,
         BossBar.Color.valueOf(config.getBossBarColor()),
         BossBar.Overlay.valueOf(config.getBossBarOverlay())
      );

      var session = new WatchSession(
         spectatorId,
         suspect.getUniqueId(),
         spectator.getLocation().clone(),
         spectator.getGameMode(),
         logger,
         bossBar,
         reason,
         System.currentTimeMillis()
      );

      // Apply game mode BEFORE registering the session, otherwise the
      // gamemode change listener would instantly cancel our own switch.
      spectator.setGameMode(GameMode.SPECTATOR);
      this.activeSessions.put(spectatorId, session);

      if (config.isSuspectGlow()) {
         suspect.setGlowing(true);
      }
      if (config.isNightVision()) {
         PotionEffect nightVision = new PotionEffect(PotionEffectType.NIGHT_VISION, INFINITE_DURATION, INFINITE_AMPLIFIER, true, false, false);
         spectator.addPotionEffect(nightVision);
      }
      spectator.teleportAsync(suspect.getLocation());
      if (config.isBossBarEnabled()) {
         spectator.showBossBar(bossBar);
      }

      if (config.isStartSoundEnabled()) {
         playSound(spectator, config.getStartSoundName());
      }
      this.notifyStaff(config.getMessages().getString("started-notify", ""),
         spectator.getName(), suspect.getName(), reason, DateFormatUtil.now());
      WebhookUtil.sendAsync(this.plugin, config.getWebhookUrl(),
         formatPayload(config.getWebhookStartPayload(), suspect.getName(), DateFormatUtil.now(), reason, "", spectator.getName()));

      return true;
   }

   public void stop(@NotNull WatchSession session) {
      UUID spectatorId = session.spectatorId();
      Player spectator = Bukkit.getPlayer(spectatorId);
      Player suspect = Bukkit.getPlayer(session.suspectId());
      this.activeSessions.remove(spectatorId);
      if (spectator == null) {
         // Spectator went offline: still release the suspect from glow and close the log.
         Player leftoverSuspect = Bukkit.getPlayer(session.suspectId());
         if (leftoverSuspect != null && this.plugin.getIspecConfig().isSuspectGlow()) {
            leftoverSuspect.setGlowing(false);
         }
         session.logger().close();
         return;
      }

      var config = this.plugin.getIspecConfig();
      SessionDuration duration = SessionDuration.of(session.startedAtMillis(), System.currentTimeMillis());

      this.notifyStaff(config.getMessages().getString("stopped-notify", ""),
         spectator.getName(),
         suspect != null ? suspect.getName() : "unknown",
         DateFormatUtil.now(),
         duration.format(config.getDurationFormat()));

      if (config.isTeleportAfterStop()) {
         Location target = config.isReturnToOldLocation()
            ? session.originalLocation()
            : this.resolveReturnLocation(spectator);
         if (target != null) {
            spectator.teleportAsync(target);
         }
      }

      spectator.hideBossBar(session.bossBar());
      spectator.removePotionEffect(PotionEffectType.NIGHT_VISION);
      if (suspect != null && config.isSuspectGlow()) {
         suspect.setGlowing(false);
      }
      spectator.setGameMode(config.isReturnToOldGamemode()
         ? session.originalGameMode()
         : this.resolveGameMode(config.getReturnGamemodeName(), GameMode.SURVIVAL));
      if (config.isStopSoundEnabled()) {
         playSound(spectator, config.getStopSoundName());
      }
      session.logger().close();

      WebhookUtil.sendAsync(this.plugin, config.getWebhookUrl(),
         formatPayload(config.getWebhookStopPayload(),
            suspect != null ? suspect.getName() : "unknown",
            DateFormatUtil.now(),
            duration.format(config.getDurationFormat()),
            "",
            spectator.getName()));
   }

   public void stopAll() {
      this.activeSessions.values().forEach(session -> {
         try {
            this.stop(session);
         } catch (RuntimeException e) {
            this.plugin.getLogger().severe("Failed to stop session: " + e.getMessage());
         }
      });
      this.activeSessions.clear();
   }

   public @NotNull Map<UUID, WatchSession> getActiveSessions() {
      return Collections.unmodifiableMap(this.activeSessions);
   }

   public @NotNull Optional<WatchSession> findSession(@NotNull Player player) {
      UUID uniqueId = player.getUniqueId();
      return this.activeSessions.values().stream()
         .filter(session -> session.spectatorId().equals(uniqueId) || session.suspectId().equals(uniqueId))
         .findFirst();
   }

   public @NotNull Optional<WatchSession> findSpectatorSession(@NotNull Player spectator) {
      return Optional.ofNullable(this.activeSessions.get(spectator.getUniqueId()));
   }

   public @NotNull Optional<WatchSession> findSuspectSession(@NotNull Player suspect) {
      UUID uniqueId = suspect.getUniqueId();
      return this.activeSessions.values().stream()
         .filter(session -> session.suspectId().equals(uniqueId))
         .findFirst();
   }

   private static void playSound(@NotNull Player player, @NotNull String soundName) {
      try {
         player.playSound(player.getLocation(), org.bukkit.Sound.valueOf(soundName.toUpperCase(java.util.Locale.ROOT)), 1.0F, 1.0F);
      } catch (IllegalArgumentException e) {
         // Invalid sound key in config — silently ignore rather than crash session logic.
      }
   }

   private @Nullable Location resolveReturnLocation(@NotNull Player spectator) {
      var config = this.plugin.getIspecConfig();
      World world = Bukkit.getWorld(config.getReturnWorld());
      if (world == null) {
         MessageUtil.sendPrefixed(spectator, config.getPrefix(), config.getMessages().getString("world-not-found", ""));
         return spectator.getWorld().getSpawnLocation();
      }
      return new Location(world, config.getReturnX(), config.getReturnY(), config.getReturnZ());
   }

   private @NotNull GameMode resolveGameMode(@NotNull String name, @NotNull GameMode fallback) {
      return java.util.Arrays.stream(GameMode.values())
         .filter(mode -> mode.name().equalsIgnoreCase(name))
         .findFirst()
         .orElseGet(() -> {
            this.plugin.getLogger().warning("Unknown gamemode '" + name + "', using " + fallback.name());
            return fallback;
         });
   }

   private void notifyStaff(@NotNull String raw, @NotNull Object... args) {
      var config = this.plugin.getIspecConfig();
      if (!config.isNotify() || raw == null || raw.isEmpty()) {
         return;
      }
      String prefix = config.getPrefix();
      Bukkit.getOnlinePlayers().stream()
         .filter(player -> player.hasPermission(NOTIFY_PERMISSION) && !player.hasPermission(BYPASS_PERMISSION))
         .forEach(player -> MessageUtil.sendPrefixed(player, prefix, raw, args));
   }

   private static @NotNull String formatPayload(@NotNull String payload, @NotNull Object... args) {
      String result = payload;
      for (int i = 0; i < args.length; i++) {
         result = result.replace("{" + i + "}", String.valueOf(args[i]));
      }
      return result;
   }
}
