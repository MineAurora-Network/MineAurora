package me.login.coinflip;

import java.util.UUID;

// This file remains unchanged from the original.
public class CoinflipStats {
    private final UUID playerUUID;
    private final int wins;
    private final int losses;

    public CoinflipStats(UUID playerUUID, int wins, int losses) {
        this.playerUUID = playerUUID;
        this.wins = wins;
        this.losses = losses;
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public int getWins() { return wins; }
    public int getLosses() { return losses; }
}