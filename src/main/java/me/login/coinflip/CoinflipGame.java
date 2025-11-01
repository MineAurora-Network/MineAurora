package me.login.coinflip;

import org.bukkit.entity.Player;
import java.util.UUID;

public class CoinflipGame {

    public enum CoinSide { HEADS, TAILS }

    private final long gameId;
    private final UUID creatorUUID;
    private final String creatorName;
    private final CoinSide chosenSide;
    private final double amount;
    private final long creationTime;

    private UUID challengerUUID;
    private String challengerName;

    public CoinflipGame(long gameId, UUID creatorUUID, String creatorName, CoinSide chosenSide, double amount, long creationTime) {
        this.gameId = gameId;
        this.creatorUUID = creatorUUID;
        this.creatorName = creatorName;
        this.chosenSide = chosenSide;
        this.amount = amount;
        this.creationTime = creationTime;
    }

    // Getters
    public long getGameId() { return gameId; }
    public UUID getCreatorUUID() { return creatorUUID; }
    public String getCreatorName() { return creatorName; }
    public CoinSide getChosenSide() { return chosenSide; }
    public double getAmount() { return amount; }
    public long getCreationTime() { return creationTime; }


    public void setChallenger(Player challenger) {
        this.challengerUUID = challenger.getUniqueId();
        this.challengerName = challenger.getName();
    }

    public UUID getChallengerUUID() {
        return this.challengerUUID;
    }
}