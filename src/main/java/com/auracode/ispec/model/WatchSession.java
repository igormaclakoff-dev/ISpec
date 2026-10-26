package com.auracode.ispec.model;

import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable spectation session representation.
 */
public record WatchSession(
   @NotNull UUID spectatorId,
   @NotNull UUID suspectId,
   @NotNull Location originalLocation,
   @NotNull GameMode originalGameMode,
   @NotNull SessionLogger logger,
   @NotNull BossBar bossBar,
   @NotNull String reason,
   long startedAtMillis
) {
}
