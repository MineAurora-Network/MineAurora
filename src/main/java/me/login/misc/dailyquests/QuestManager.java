package me.login.misc.dailyquests;

import me.login.Login;
import me.login.misc.tokens.TokenManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class QuestManager {

    private final QuestsModule module;
    private final Login plugin;
    private final QuestsDatabase database;
    private final Map<QuestType, List<Quest>> allQuests = new EnumMap<>(QuestType.class);
    private final Map<UUID, PlayerQuestData> playerQuestDataCache = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private Economy economy;
    private TokenManager tokenManager;
    private Component serverPrefix;
    private final ZoneId serverZoneId = ZoneId.systemDefault(); // For 24:00 reset

    public QuestManager(QuestsModule module) {
        this.module = module;
        this.plugin = module.getPlugin();
        this.database = module.getQuestsDatabase();
        setupDependencies();
    }

    private void setupDependencies() {
        // Vault
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            this.economy = plugin.getVaultEconomy();
            if (this.economy == null) {
                plugin.getLogger().severe("Vault is installed, but Economy provider not found! Quest cash rewards will not work.");
            }
        } else {
            plugin.getLogger().severe("Vault not found! Quest cash rewards will not work.");
        }

        // Tokens
        this.tokenManager = plugin.getTokenManager();
        if (this.tokenManager == null) {
            plugin.getLogger().severe("TokenManager not found! Quest token rewards will not work.");
        }

        // Prefix
        String prefixString = plugin.getConfig().getString("server_prefix", "<gray>[<gold>Server<gray>] ");
        this.serverPrefix = MiniMessage.miniMessage().deserialize(prefixString);
    }

    public void loadQuests() {
        for (QuestType type : QuestType.values()) {
            String sectionName = type.name().toLowerCase();
            List<Map<?, ?>> questMaps = module.getQuestsConfig().getMapList(sectionName);
            List<Quest> quests = new ArrayList<>();

            for (int i = 0; i < questMaps.size(); i++) {
                Map<?, ?> questMap = questMaps.get(i);
                String id = sectionName + "_" + i;
                String objectiveDesc = (String) questMap.get("objective");
                List<String> rewardList = (List<String>) questMap.get("reward");

                Quest quest = parseQuest(id, type, objectiveDesc, rewardList);
                if (quest != null) {
                    quests.add(quest);
                }
            }
            allQuests.put(type, quests);
        }
        plugin.getLogger().info("Loaded " + allQuests.get(QuestType.EASY).size() + " easy quests.");
        plugin.getLogger().info("Loaded " + allQuests.get(QuestType.HARD).size() + " hard quests.");
        plugin.getLogger().info("Loaded " + allQuests.get(QuestType.EXTREME).size() + " extreme quests.");
    }

    private Quest parseQuest(String id, QuestType type, String objectiveDesc, List<String> rewardList) {
        try {
            String[] parts = objectiveDesc.split(" ");
            String action = parts[0].toUpperCase();
            int amount = Integer.parseInt(parts[1]);
            String target = parts[2].toUpperCase();

            QuestObjective objective = null;
            Material material = null;
            EntityType entityType = null;

            switch (action) {
                case "BREAK":
                    objective = QuestObjective.BREAK_BLOCK;
                    material = Material.matchMaterial(target);
                    break;
                case "MINE":
                    objective = QuestObjective.MINE;
                    material = Material.matchMaterial(target);
                    break;
                case "KILL":
                    if (target.equals("PLAYER")) {
                        objective = QuestObjective.PLAYER_KILL;
                        entityType = EntityType.PLAYER;
                    } else {
                        objective = QuestObjective.KILL_MOB;
                        entityType = EntityType.valueOf(target);
                    }
                    break;
                case "PLANT":
                    objective = QuestObjective.PLANT;
                    material = Material.matchMaterial(target);
                    break;
                case "HARVEST":
                    objective = QuestObjective.HARVEST;
                    material = Material.matchMaterial(target);
                    break;
                default:
                    plugin.getLogger().warning("Unknown quest objective action: " + action + " in quest " + id);
                    return null;
            }

            if (material == null && entityType == null) {
                plugin.getLogger().warning("Invalid target: " + target + " in quest " + id);
                return null;
            }

            double cash = 0;
            int tokens = 0;
            for (String reward : rewardList) {
                String[] rewardParts = reward.split(" ");
                int rewardAmount = Integer.parseInt(rewardParts[0]);
                String rewardType = rewardParts[1].toLowerCase();
                if (rewardType.equals("cash")) {
                    cash = rewardAmount;
                } else if (rewardType.equals("token")) {
                    tokens = rewardAmount;
                }
            }

            return new Quest(id, type, objectiveDesc, objective, material, entityType, amount, cash, tokens);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to parse quest: " + objectiveDesc);
            e.printStackTrace();
            return null;
        }
    }

    public PlayerQuestData getPlayerQuestData(Player p) {
        return playerQuestDataCache.get(p.getUniqueId());
    }

    public void loadPlayerData(Player p) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerQuestData data = database.loadPlayerQuests(p.getUniqueId());
            playerQuestDataCache.put(p.getUniqueId(), data);
            // We now check for reset when GUI is opened, not on login.
        });
    }

    public void unloadPlayerData(Player p) {
        PlayerQuestData data = playerQuestDataCache.remove(p.getUniqueId());
        if (data != null && data.getActiveQuest() != null) {
            // Save progress on quit
            database.savePlayerQuests(data);
        }
    }

    /**
     * This is the new reset logic. It's called when the GUI is opened.
     * It checks if the last reset was on a previous day (global 24:00 reset).
     */
    public void checkAndResetDailies(PlayerQuestData data) {
        long lastReset = data.getLastQuestResetTimestamp();
        LocalDate today = LocalDate.now(serverZoneId);
        LocalDate lastResetDate = LocalDate.ofInstant(Instant.ofEpochMilli(lastReset), serverZoneId);

        if (lastReset == 0 || lastResetDate.isBefore(today)) {
            assignNewDailyQuests(data);
        }
    }

    private void assignNewDailyQuests(PlayerQuestData data) {
        Quest easy = getRandomQuest(QuestType.EASY, data.getDailyEasyQuest());
        Quest hard = getRandomQuest(QuestType.HARD, data.getDailyHardQuest());
        Quest extreme = getRandomQuest(QuestType.EXTREME, data.getDailyExtremeQuest());

        data.setDailyEasyQuest(easy);
        data.setDailyHardQuest(hard);
        data.setDailyExtremeQuest(extreme);
        data.resetDailyData(); // Clears active quest, progress, and completed list

        database.savePlayerQuests(data); // Save the new daily state

        Player p = Bukkit.getPlayer(data.getPlayerUUID());
        if (p != null && p.isOnline()) {
            p.sendMessage(serverPrefix.append(MiniMessage.miniMessage().deserialize("<green>Your daily quests have been reset! Type /quests.")));
        }
    }

    private Quest getRandomQuest(QuestType type, Quest currentQuest) {
        List<Quest> questList = allQuests.get(type);
        if (questList == null || questList.isEmpty()) {
            return null;
        }
        if (questList.size() == 1) {
            return questList.get(0);
        }

        Quest newQuest;
        do {
            newQuest = questList.get(random.nextInt(questList.size()));
        } while (currentQuest != null && newQuest.getId().equals(currentQuest.getId()));

        return newQuest;
    }

    /**
     * Called by GUI listener when a player shift-clicks a quest.
     */
    public void acceptQuest(Player p, QuestType type) {
        PlayerQuestData data = getPlayerQuestData(p);
        if (data == null) return;

        if (data.getActiveQuest() != null) {
            p.sendMessage(serverPrefix.append(MiniMessage.miniMessage().deserialize("<red>You already have an active quest!</red>")));
            return;
        }

        if (data.getCompletedQuestTypes().contains(type)) {
            p.sendMessage(serverPrefix.append(MiniMessage.miniMessage().deserialize("<red>You have already completed a quest of this type today.</red>")));
            return;
        }

        Quest questToAccept = null;
        switch (type) {
            case EASY: questToAccept = data.getDailyEasyQuest(); break;
            case HARD: questToAccept = data.getDailyHardQuest(); break;
            case EXTREME: questToAccept = data.getDailyExtremeQuest(); break;
        }

        if (questToAccept == null) {
            p.sendMessage(serverPrefix.append(MiniMessage.miniMessage().deserialize("<red>Error: This quest is not available. Try re-opening the menu.</red>")));
            return;
        }

        data.setActiveQuest(questToAccept);
        data.setActiveQuestProgress(0);
        database.savePlayerQuests(data); // Save the newly accepted quest

        p.sendMessage(serverPrefix.append(MiniMessage.miniMessage().deserialize(
                "<green>Quest Accepted: <white>" + questToAccept.getObjectiveDescription() + "</white></green>"
        )));
        p.closeInventory();
    }

    /**
     * Called by the /quests reset command.
     * Cancels the player's active quest and prevents them from taking another quest of that type today.
     */
    public void resetActiveQuest(Player p) {
        PlayerQuestData data = getPlayerQuestData(p);
        if (data == null) return;

        Quest activeQuest = data.getActiveQuest();

        if (activeQuest == null) {
            p.sendMessage(serverPrefix.append(MiniMessage.miniMessage().deserialize("<red>You do not have an active quest to reset.</red>")));
            return;
        }

        QuestType activeType = activeQuest.getType();

        // "Burn" the quest type by marking it as completed, even though it was reset.
        data.getCompletedQuestTypes().add(activeType);
        data.setActiveQuest(null);
        data.setActiveQuestProgress(0);

        // Save the updated state to the DB
        database.savePlayerQuests(data);

        p.sendMessage(serverPrefix.append(MiniMessage.miniMessage().deserialize("<green>Your active quest has been reset. You can now choose a different quest type for today (if available).</green>")));
    }

    /**
     * Called by progress listener when an action is performed.
     */
    public void handleProgress(Player p, QuestObjective objective, Material material, EntityType entityType) {
        PlayerQuestData data = getPlayerQuestData(p);
        if (data == null || data.getActiveQuest() == null) {
            return; // No active quest, do nothing
        }

        Quest quest = data.getActiveQuest();
        boolean match = false;

        if (quest.getObjective() == objective) {
            if (quest.getObjectiveMaterial() != null && quest.getObjectiveMaterial() == material) {
                match = true;
            } else if (quest.getObjectiveEntity() != null && quest.getObjectiveEntity() == entityType) {
                match = true;
            }
        }

        if (!match && (quest.getObjective() == QuestObjective.MINE || quest.getObjective() == QuestObjective.BREAK_BLOCK)
                && (objective == QuestObjective.MINE || objective == QuestObjective.BREAK_BLOCK) // <-- 'action' changed to 'objective'
                && quest.getObjectiveMaterial() == material) {
            match = true;
        }

        if (!match && quest.getObjective() == QuestObjective.PLANT && objective == QuestObjective.PLANT // <-- 'action' changed to 'objective'
                && quest.getObjectiveMaterial() == material) {
            match = true;
        }

        if (match) {
            int newProgress = data.getActiveQuestProgress() + 1;
            data.setActiveQuestProgress(newProgress);

            if (newProgress >= quest.getRequiredAmount()) {
                completeQuest(p, quest);
            } else {
                // Save progress to DB
                database.updateActiveQuestProgress(p.getUniqueId(), newProgress);
            }
        }
    }

    private void completeQuest(Player p, Quest quest) {
        PlayerQuestData data = getPlayerQuestData(p);
        if (data == null) return;

        // Give Rewards
        if (economy != null && quest.getRewardCash() > 0) {
            economy.depositPlayer(p, quest.getRewardCash());
        }
        if (tokenManager != null && quest.getRewardTokens() > 0) {
            tokenManager.addTokens(p.getUniqueId(), quest.getRewardTokens());
        }

        // Send Message
        p.sendMessage(serverPrefix.append(MiniMessage.miniMessage().deserialize(
                "<green>Quest Completed! <gray>(<white>" + quest.getObjectiveDescription() + "<gray>)"
        )));
        p.sendMessage(serverPrefix.append(MiniMessage.miniMessage().deserialize(
                "<green>Rewards: <aqua>$" + String.format("%,.0f", quest.getRewardCash()) + " <gray>and <gold>" + quest.getRewardTokens() + " Tokens"
        )));

        // Log to Discord
        if (module.getQuestsLogger() != null) {
            module.getQuestsLogger().logQuestCompletion(p, quest, quest.getRewardCash(), quest.getRewardTokens());
        }

        // Update data
        data.getCompletedQuestTypes().add(quest.getType());
        data.setActiveQuest(null);
        data.setActiveQuestProgress(0);

        // Save final state to DB
        database.savePlayerQuests(data);
    }

    public Map<QuestType, List<Quest>> getAllQuests() {
        return allQuests;
    }

    public Quest getQuestById(String questId) {
        if (questId == null || questId.isEmpty()) {
            return null;
        }
        for (List<Quest> questList : allQuests.values()) {
            for (Quest quest : questList) {
                if (quest.getId().equals(questId)) {
                    return quest;
                }
            }
        }
        return null; // Not found
    }

    public Component getServerPrefix() {
        return serverPrefix;
    }
}