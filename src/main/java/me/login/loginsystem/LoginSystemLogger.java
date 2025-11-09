package me.login.loginsystem;

import me.login.discord.moderation.DiscordCommandLogger;

public class LoginSystemLogger {

    private final DiscordCommandLogger discordLogger;

    public LoginSystemLogger(DiscordCommandLogger discordLogger) {
        this.discordLogger = discordLogger;
    }

    public void log(String message) {
        if (discordLogger != null) {
            discordLogger.logNormal("[LoginSystem] " + message);
        }
    }

    public void logAdmin(String message) {
        if (discordLogger != null) {
            discordLogger.logStaff("[LoginAdmin] " + message);
        }
    }
}