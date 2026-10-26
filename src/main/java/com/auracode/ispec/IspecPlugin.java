package com.auracode.ispec;

import com.auracode.ispec.command.IspecCommand;
import com.auracode.ispec.listener.SessionListener;
import com.auracode.ispec.manager.SessionManager;
import com.auracode.ispec.util.DateFormatUtil;
import com.auracode.ispec.util.WebhookUtil;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Main plugin class. Only wires modules together.
 */
public final class IspecPlugin extends JavaPlugin {

   private IspecConfig ispecConfig;
   private SessionManager sessionManager;

   @Override
   public void onEnable() {
      this.ispecConfig = new IspecConfig(this);
      this.ispecConfig.load();

      DateFormatUtil.initialize(this, this.ispecConfig.getDatePattern(), this.ispecConfig.getTimeZone());
      WebhookUtil.initialize();

      this.sessionManager = new SessionManager(this);
      PluginCommand command = this.getCommand("ispec");
      if (command != null) {
         IspecCommand executor = new IspecCommand(this);
         command.setExecutor(executor);
         command.setTabCompleter(executor);
      }
      this.getServer().getPluginManager().registerEvents(new SessionListener(this), this);

      this.getLogger().info("ISpec enabled");
   }

   @Override
   public void onDisable() {
      if (this.sessionManager != null) {
         this.sessionManager.stopAll();
      }
      this.getLogger().info("ISpec disabled");
   }

   public @NotNull IspecConfig getIspecConfig() {
      return this.ispecConfig;
   }

   public @NotNull SessionManager getSessionManager() {
      return this.sessionManager;
   }
}
