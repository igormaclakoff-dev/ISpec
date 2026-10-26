package com.auracode.ispec.command;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import com.auracode.ispec.IspecPlugin;
import com.auracode.ispec.model.WatchSession;
import com.auracode.ispec.util.DateFormatUtil;
import com.auracode.ispec.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * /ispec command executor with subcommands: go, off.
 */
public final class IspecCommand implements CommandExecutor, TabCompleter {

   private static final String SUB_GO = "go";
   private static final String SUB_OFF = "off";
   private static final String SUB_RELOAD = "reload";
   private static final String SUB_LANG = "lang";
   private static final String RELOAD_PERMISSION = "ispec.reload";
   private static final String BYPASS_PERMISSION = "ispec.bypass";
   private static final int SUSPECT_ARG_INDEX = 1;
   private static final int REASON_ARG_START = 2;

   private final IspecPlugin plugin;

   public IspecCommand(@NotNull IspecPlugin plugin) {
      this.plugin = plugin;
   }

   @Override
   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
      if (args.length >= 1 && SUB_LANG.equalsIgnoreCase(args[0])) {
         if (!sender.hasPermission(RELOAD_PERMISSION)) {
            MessageUtil.sendPrefixed(sender, "", this.message("no-permission"));
            return true;
         }
         this.handleLang(sender, args);
         return true;
      }

      if (args.length >= 1 && SUB_RELOAD.equalsIgnoreCase(args[0])) {
         this.handleReload(sender);
         return true;
      }

      if (!(sender instanceof Player spectator)) {
         MessageUtil.sendPrefixed(sender, "", this.message("player-only"));
         return true;
      }

      if (args.length < 1) {
         MessageUtil.sendPrefixed(spectator, this.plugin.getIspecConfig().getPrefix(), this.message("usage"));
         return true;
      }

      switch (args[0].toLowerCase(Locale.ROOT)) {
         case SUB_GO -> this.handleGo(spectator, args);
         case SUB_OFF -> this.handleOff(spectator);
         case SUB_RELOAD -> this.handleReload(sender);
         default -> MessageUtil.sendPrefixed(spectator, this.plugin.getIspecConfig().getPrefix(), this.message("unknown-subcommand"));
      }
      return true;
   }

   private void handleGo(@NotNull Player spectator, @NotNull String[] args) {
      var config = this.plugin.getIspecConfig();
      if (args.length < SUSPECT_ARG_INDEX + 1) {
         MessageUtil.sendPrefixed(spectator, config.getPrefix(), this.message("specify-suspect"));
         return;
      }

      boolean needReason = config.getReasons().required();
      if (needReason && args.length < REASON_ARG_START + 1) {
         MessageUtil.sendPrefixed(spectator, config.getPrefix(), this.message("specify-reason"));
         return;
      }

      Player suspect = Bukkit.getPlayerExact(args[SUSPECT_ARG_INDEX]);
      if (suspect == null) {
         MessageUtil.sendPrefixed(spectator, config.getPrefix(), this.message("suspect-not-found"));
         return;
      }
      if (suspect.equals(spectator)) {
         MessageUtil.sendPrefixed(spectator, config.getPrefix(), this.message("cannot-spectate-self"));
         return;
      }
      if (suspect.hasPermission(BYPASS_PERMISSION)) {
         MessageUtil.sendPrefixed(spectator, config.getPrefix(), this.message("suspect-bypassed"));
         return;
      }
      if (this.plugin.getSessionManager().isSuspect(suspect)) {
         MessageUtil.sendPrefixed(spectator, config.getPrefix(), this.message("target-is-spectating"));
         return;
      }

      String reason = needReason
         ? String.join(" ", Arrays.copyOfRange(args, REASON_ARG_START, args.length)).trim()
         : "";
      var reasonSettings = config.getReasons();
      if (needReason && !reasonSettings.any() && reasonSettings.strict()
         && reasonSettings.presets().stream().noneMatch(preset -> preset.equalsIgnoreCase(reason))) {
         MessageUtil.sendPrefixed(spectator, config.getPrefix(), this.message("invalid-reason"));
         return;
      }

      if (!this.plugin.getSessionManager().tryStart(spectator, suspect, reason)) {
         MessageUtil.sendPrefixed(spectator, config.getPrefix(), this.message("already-spectating"));
         return;
      }
      MessageUtil.sendPrefixed(spectator, config.getPrefix(), this.message("started"), suspect.getName(), DateFormatUtil.now());
   }

   private void handleOff(@NotNull Player spectator) {
      var config = this.plugin.getIspecConfig();
      var manager = this.plugin.getSessionManager();
      Optional<WatchSession> sessionOpt = manager.findSpectatorSession(spectator);
      if (sessionOpt.isEmpty()) {
         MessageUtil.sendPrefixed(spectator, config.getPrefix(), this.message("not-spectating"));
         return;
      }
      WatchSession session = sessionOpt.get();
      Player suspect = Bukkit.getPlayer(session.suspectId());
      manager.stop(session);
      MessageUtil.sendPrefixed(spectator, config.getPrefix(), this.message("stopped"), DateFormatUtil.now(),
         suspect != null ? suspect.getName() : "unknown");
   }

   private void handleLang(@NotNull CommandSender sender, @NotNull String[] args) {
      var config = this.plugin.getIspecConfig();
      if (args.length < 2) {
         MessageUtil.sendPrefixed(sender, config.getPrefix(),
            config.getMessages().getString("lang-list", ""), config.getLanguage());
         return;
      }
      String language = args[1].toLowerCase(java.util.Locale.ROOT);
      if (!config.getSupportedLanguages().contains(language)) {
         MessageUtil.sendPrefixed(sender, config.getPrefix(),
            config.getMessages().getString("lang-list", ""), config.getLanguage());
         return;
      }
      config.setLanguage(language);
      MessageUtil.sendPrefixed(sender, config.getPrefix(),
         config.getMessages().getString("lang-set", ""), language);
   }

   private void handleReload(@NotNull CommandSender sender) {
      if (!sender.hasPermission(RELOAD_PERMISSION)) {
         MessageUtil.sendPrefixed(sender, "", this.message("no-permission"));
         return;
      }
      this.plugin.getIspecConfig().load();
      MessageUtil.sendPrefixed(sender, this.plugin.getIspecConfig().getPrefix(), this.message("reload-done"));
   }

   private @Nullable String message(@NotNull String key) {
      return this.plugin.getIspecConfig().getMessages().getString(key, "");
   }

   @Override
   public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
      if (!(sender instanceof Player) || args.length == 0) {
         return List.of();
      }
      var config = this.plugin.getIspecConfig();
      if (args.length == 1) {
         return Stream.of(SUB_GO, SUB_OFF, SUB_LANG, SUB_RELOAD)
            .filter(value -> sender.hasPermission(RELOAD_PERMISSION) || !SUB_RELOAD.equals(value))
            .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
            .toList();
      }
      if (SUB_LANG.equalsIgnoreCase(args[0]) && args.length == 2) {
         return this.plugin.getIspecConfig().getSupportedLanguages().stream()
            .filter(language -> language.startsWith(args[1].toLowerCase(Locale.ROOT)))
            .toList();
      }
      if (SUB_GO.equalsIgnoreCase(args[0]) && args.length == SUSPECT_ARG_INDEX + 1) {
         String input = args[SUSPECT_ARG_INDEX].toLowerCase(Locale.ROOT);
         return Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
            .toList();
      }
      if (SUB_GO.equalsIgnoreCase(args[0]) && args.length > REASON_ARG_START
         && config.getReasons().required() && !config.getReasons().any()) {
         String input = String.join(" ", Arrays.copyOfRange(args, REASON_ARG_START, args.length)).toLowerCase(Locale.ROOT);
         return config.getReasons().presets().stream()
            .filter(reason -> reason.toLowerCase(Locale.ROOT).startsWith(input))
            .toList();
      }
      return List.of();
   }
}
