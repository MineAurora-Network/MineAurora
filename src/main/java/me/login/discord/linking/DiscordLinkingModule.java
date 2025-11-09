package me.login.discord.linking; // <-- CHANGED

import me.login.Login;
import me.login.clearlag.LagClearLogger;
import me.login.discord.moderation.DiscordCommandLogger; // <-- CHANGED
import me.login.discord.moderation.DiscordCommandRegistrar; // <-- CHANGED
import me.login.discord.moderation.DiscordModConfig; // <-- CHANGED
import net.dv8tion.jda.api.JDA;
import org.bukkit.Bukkit;

public class DiscordLinkingModule {

    private final Login plugin;
    private DiscordLinking discordLinking;
    private DiscordLinkDatabase discordLinkDatabase;
    private DiscordLinkLogger discordLinkLogger;
    private JDA mainBotJda;
    private DiscordCommandLogger discordCommandLogger;

    public DiscordLinkingModule(Login plugin) {
        this.plugin = plugin;
    }

    public boolean init(LagClearLogger lagClearLogger, DiscordModConfig discordModConfig) {
        plugin.getLogger().info("Initializing DiscordLinking components...");

        this.discordLinkDatabase = new DiscordLinkDatabase(plugin);
        discordLinkDatabase.connect();
        if (discordLinkDatabase.getConnection() == null) {
            plugin.getLogger().severe("Failed to connect to DiscordLink database.");
            return false;
        }

        JDA sharedJda = (lagClearLogger != null) ? lagClearLogger.getJDA() : null;

        if (lagClearLogger == null || sharedJda == null) {
            plugin.getLogger().warning("LagClearLogger or its JDA is null. DiscordLinkLogger will be disabled.");
        }
        this.discordLinkLogger = new DiscordLinkLogger(plugin, sharedJda);

        if (lagClearLogger == null || sharedJda == null) {
            plugin.getLogger().warning("LagClearLogger or its JDA is null. DiscordCommandLogger will be disabled.");
        }
        this.discordCommandLogger = new DiscordCommandLogger(plugin, sharedJda);

        this.discordLinking = new DiscordLinking(plugin, discordModConfig, this.discordLinkLogger);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String botToken = plugin.getConfig().getString("bot-token");
                if (botToken == null || botToken.isEmpty() || botToken.equals("YOUR_MAIN_BOT_TOKEN_HERE")) {
                    plugin.getLogger().severe("Main bot-token is not set in config.yml! Disabling plugin.");
                    plugin.getServer().getPluginManager().disablePlugin(plugin);
                    return;
                }

                this.mainBotJda = discordLinking.startBot(botToken, this.discordCommandLogger);

                if (this.mainBotJda == null) {
                    plugin.getLogger().severe("Main Discord Bot (DiscordLinking) failed to start. Check token and intents. Disabling plugin.");
                    plugin.getServer().getPluginManager().disablePlugin(plugin);
                    return;
                }

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    DiscordCommandRegistrar.register(this.mainBotJda, plugin, this.discordCommandLogger);
                    plugin.getLogger().info("Discord slash command listeners registered (from DiscordLinkingModule).");
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to start Main Discord Bot (DiscordLinking): " + e.getMessage());
                e.printStackTrace();
                plugin.getServer().getPluginManager().disablePlugin(plugin);
            }
        });

        DiscordLinkCmd discordCmd = new DiscordLinkCmd(plugin, this);
        plugin.getCommand("discord").setExecutor(discordCmd);
        plugin.getCommand("unlink").setExecutor(discordCmd);
        plugin.getCommand("adminunlink").setExecutor(discordCmd);

        plugin.getLogger().info("DiscordLinking components enabled.");
        return true;
    }

    public void shutdown() {
        if (discordLinking != null) {
            discordLinking.shutdown();
        }
        if (discordLinkDatabase != null) {
            discordLinkDatabase.disconnect();
        }
    }

    public DiscordLinking getDiscordLinking() {
        return discordLinking;
    }

    public DiscordLinkDatabase getDiscordLinkDatabase() {
        return discordLinkDatabase;
    }

    public JDA getMainBotJda() {
        return mainBotJda;
    }

    public DiscordLinkLogger getDiscordLinkLogger() {
        return discordLinkLogger;
    }

    public DiscordCommandLogger getDiscordCommandLogger() {
        return discordCommandLogger;
    }
}