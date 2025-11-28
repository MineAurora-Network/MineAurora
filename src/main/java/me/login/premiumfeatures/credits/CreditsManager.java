package me.login.premiumfeatures.credits;

import me.login.Login;
import org.bukkit.OfflinePlayer;

public class CreditsManager {

    private final CreditsDatabase database;
    private final CreditsLogger logger;

    public CreditsManager(Login plugin, CreditsDatabase database) {
        this.database = database;
        this.logger = new CreditsLogger(plugin);
    }

    public int getBalance(OfflinePlayer player) {
        return database.getCredits(player.getUniqueId());
    }

    public void addCredits(String adminName, OfflinePlayer target, int amount) {
        database.addCredits(target.getUniqueId(), amount);
        int newBalance = getBalance(target);
        logger.logTransaction(adminName, target.getName(), "ADD", amount, newBalance);
    }

    public void removeCredits(String adminName, OfflinePlayer target, int amount) {
        database.removeCredits(target.getUniqueId(), amount);
        int newBalance = getBalance(target);
        logger.logTransaction(adminName, target.getName(), "REMOVE", amount, newBalance);
    }

    public void setCredits(String adminName, OfflinePlayer target, int amount) {
        database.setCredits(target.getUniqueId(), amount);
        logger.logTransaction(adminName, target.getName(), "SET", amount, amount);
    }
}