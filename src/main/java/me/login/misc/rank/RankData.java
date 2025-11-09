package me.login.misc.rank;

import java.util.Objects;
import java.util.UUID;

/**
 * A data-holder class for rank data.
 * (Replaced record with class for compatibility)
 */
public final class RankData {
    private final UUID playerUuid;
    private final String playerName;
    private final String rankName;
    private final UUID setterUuid;
    private final String setterName;
    private final String previousRank;
    private final long expiryTime;

    public RankData(
            UUID playerUuid,
            String playerName,
            String rankName,
            UUID setterUuid,
            String setterName,
            String previousRank,
            long expiryTime
    ) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.rankName = rankName;
        this.setterUuid = setterUuid;
        this.setterName = setterName;
        this.previousRank = previousRank;
        this.expiryTime = expiryTime;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public String playerName() {
        return playerName;
    }

    public String rankName() {
        return rankName;
    }

    public UUID setterUuid() {
        return setterUuid;
    }

    public String setterName() {
        return setterName;
    }

    public String previousRank() {
        return previousRank;
    }

    public long expiryTime() {
        return expiryTime;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (RankData) obj;
        return Objects.equals(this.playerUuid, that.playerUuid) &&
                Objects.equals(this.playerName, that.playerName) &&
                Objects.equals(this.rankName, that.rankName) &&
                Objects.equals(this.setterUuid, that.setterUuid) &&
                Objects.equals(this.setterName, that.setterName) &&
                Objects.equals(this.previousRank, that.previousRank) &&
                this.expiryTime == that.expiryTime;
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerUuid, playerName, rankName, setterUuid, setterName, previousRank, expiryTime);
    }

    @Override
    public String toString() {
        return "RankData[" +
                "playerUuid=" + playerUuid + ", " +
                "playerName=" + playerName + ", " +
                "rankName=" + rankName + ", " +
                "setterUuid=" + setterUuid + ", " +
                "setterName=" + setterName + ", " +
                "previousRank=" + previousRank + ", " +
                "expiryTime=" + expiryTime + ']';
    }
}