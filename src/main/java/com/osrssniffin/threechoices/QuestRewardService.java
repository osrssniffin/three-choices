package com.osrssniffin.threechoices;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

/**
 * Paces quest-state reads at one quest per game tick. The baseline covers every
 * RuneLite Quest so genuine future completions can receive a
 * small QP-based point bonus without retroactive payouts. Direct quest reward
 * unlocks continue to use QuestRewardCatalog.
 */
@Singleton
public class QuestRewardService
{
    private static final String KEY_BASELINE_INITIALIZED = "questRewardBaselineInitialized";
    private static final String KEY_OBSERVED = "questRewardObserved";
    private static final String KEY_BASELINE_BUILDING = "questRewardBaselineBuilding";
    private static final String KEY_COMPLETION_BASELINE_VERSION = "questCompletionBaselineVersion";
    private static final String KEY_LAST_QUEST_POINTS = "questCompletionLastQuestPoints";
    private static final String KEY_PENDING_QUEST_POINTS = "questCompletionPendingQuestPoints";
    private static final String KEY_AWAITING_COMPLETIONS = "questCompletionAwaitingFinished";
    private static final String KEY_PENDING_GRANDMASTER_QP = "questCompletionPendingGrandmasterQuestPoints";
    private static final int COMPLETION_BASELINE_VERSION = 1;

    // Normal quest rewards taper gently with completed Three Choices rolls so
    // questing remains a worthwhile boost without becoming the dominant roll
    // farm. Grandmaster quests always resolve to 250 points per official QP.
    private static final long GRANDMASTER_POINTS_PER_QUEST_POINT = 250L;
    private static final long QUEST_POINTS_PER_QP_0_ROLLS = 100L;
    private static final long QUEST_POINTS_PER_QP_1_TO_2_ROLLS = 85L;
    private static final long QUEST_POINTS_PER_QP_3_TO_5_ROLLS = 70L;
    private static final long QUEST_POINTS_PER_QP_6_TO_10_ROLLS = 55L;
    private static final long QUEST_POINTS_PER_QP_11_PLUS_ROLLS = 40L;

    // RuneLite's Quest enum exposes quest identity/state, not official
    // difficulty or QP reward. Keep this tiny, explicit table limited to OSRS
    // Grandmaster quests so their premium is deterministic and cannot be
    // confused with high-QP non-Grandmaster quests.
    private static final Map<String, Integer> GRANDMASTER_QUEST_POINTS = Map.of(
        "monkey madness ii", 4,
        "dragon slayer ii", 5,
        "song of the elves", 4,
        "desert treasure ii - the fallen empire", 5,
        "while guthix sleeps", 5,
        "the blood moon rises", 4
    );

    private final Client client;
    private final ItemManager itemManager;
    private final ThreeChoicesStateService stateService;
    private final ItemPool itemPool;
    private final ConfigManager configManager;
    private final UnlockCelebrationOverlay celebrationOverlay;
    private final Set<String> observedFinished = new HashSet<>();

    private boolean initialized;
    private boolean baselineBuilding;
    private int cursor;
    private int lastQuestPoints;
    private int pendingQuestPoints;
    private int awaitingFinishedCompletions;
    private int awaitingFinishedAgeTicks;
    private int pendingGrandmasterQuestPoints;
    private long lastPointAward;

    @Inject
    public QuestRewardService(
        Client client,
        ItemManager itemManager,
        ThreeChoicesStateService stateService,
        ItemPool itemPool,
        ConfigManager configManager,
        UnlockCelebrationOverlay celebrationOverlay)
    {
        this.client = client;
        this.itemManager = itemManager;
        this.stateService = stateService;
        this.itemPool = itemPool;
        this.configManager = configManager;
        this.celebrationOverlay = celebrationOverlay;
    }

    /** Must be called on the RuneLite client thread after profile state loads. */
    public boolean initializeOrSync()
    {
        boolean wasInitialized = Boolean.parseBoolean(configManager.getRSProfileConfiguration(
            ThreeChoicesConfig.GROUP, KEY_BASELINE_INITIALIZED));
        boolean wasBuilding = Boolean.parseBoolean(configManager.getRSProfileConfiguration(
            ThreeChoicesConfig.GROUP, KEY_BASELINE_BUILDING));
        int baselineVersion = parseInt(configManager.getRSProfileConfiguration(
            ThreeChoicesConfig.GROUP, KEY_COMPLETION_BASELINE_VERSION), 0);

        loadObserved();
        initialized = true;
        cursor = 0;
        lastPointAward = 0L;
        int currentQuestPoints = currentQuestPoints();
        int persistedLastQuestPoints = Math.max(0, parseInt(configManager.getRSProfileConfiguration(
            ThreeChoicesConfig.GROUP, KEY_LAST_QUEST_POINTS), currentQuestPoints));
        pendingQuestPoints = Math.max(0, parseInt(configManager.getRSProfileConfiguration(
            ThreeChoicesConfig.GROUP, KEY_PENDING_QUEST_POINTS), 0));
        awaitingFinishedCompletions = Math.min(1, Math.max(0, parseInt(configManager.getRSProfileConfiguration(
            ThreeChoicesConfig.GROUP, KEY_AWAITING_COMPLETIONS), 0)));
        pendingGrandmasterQuestPoints = Math.max(0, parseInt(configManager.getRSProfileConfiguration(
            ThreeChoicesConfig.GROUP, KEY_PENDING_GRANDMASTER_QP), 0));
        awaitingFinishedAgeTicks = 0;
        // Preserve a narrow restart race only when a FINISHED transition was
        // already observed. Otherwise login establishes the current QP value as
        // historical so quests completed while the plugin was not tracking do
        // not become retroactive credit.
        lastQuestPoints = awaitingFinishedCompletions > 0
            ? Math.min(persistedLastQuestPoints, currentQuestPoints)
            : currentQuestPoints;

        if (!wasInitialized || wasBuilding || baselineVersion < COMPLETION_BASELINE_VERSION)
        {
            // Upgrade/reset baselines every currently completed quest without
            // payment. Never query them all in one event: Quest#getState runs a
            // client script, so discovery remains one quest per game tick.
            if (!wasBuilding || baselineVersion < COMPLETION_BASELINE_VERSION)
            {
                configManager.setRSProfileConfiguration(
                    ThreeChoicesConfig.GROUP, KEY_BASELINE_INITIALIZED, false);
                configManager.setRSProfileConfiguration(
                    ThreeChoicesConfig.GROUP, KEY_BASELINE_BUILDING, true);
                configManager.setRSProfileConfiguration(
                    ThreeChoicesConfig.GROUP, KEY_COMPLETION_BASELINE_VERSION, 0);
            }
            baselineBuilding = true;
            pendingQuestPoints = 0;
            awaitingFinishedCompletions = 0;
            pendingGrandmasterQuestPoints = 0;
            awaitingFinishedAgeTicks = 0;
            saveQuestPointTracking();
            return false;
        }

        baselineBuilding = false;
        saveQuestPointTracking();
        return false;
    }

    /**
     * Full synchronization helper retained for focused tests/tools. Runtime uses
     * syncNext() so quest-state scripts remain paced across game ticks.
     */
    public boolean syncAllNew()
    {
        if (!initialized) return initializeOrSync();
        if (baselineBuilding) return false;

        observeQuestPointDelta();
        boolean changed = false;
        boolean observedChanged = false;
        for (Quest quest : Quest.values())
        {
            String questKey = key(quest.getName());
            if (!observedFinished.contains(questKey) && isFinished(quest))
            {
                observedFinished.add(questKey);
                observedChanged = true;
                itemPool.clearQuestRequirementCache();
                QuestRewardCatalog.Entry entry = QuestRewardCatalog.entryForQuestName(quest.getName());
                if (entry != null) changed |= unlockEntry(entry);
                changed |= recordFinishedTransition(quest);
            }
        }
        if (observedChanged) saveObserved();
        return changed;
    }

    /** One quest per game tick keeps RuneLite quest-status script load tiny. */
    public boolean syncNext()
    {
        if (!initialized) return initializeOrSync();
        if (baselineBuilding)
        {
            advanceBaseline();
            return false;
        }

        observeQuestPointDelta();
        Quest[] quests = Quest.values();
        if (quests.length == 0) return false;
        if (cursor >= quests.length) cursor = 0;

        Quest quest = quests[cursor];
        cursor = (cursor + 1) % quests.length;
        String questKey = key(quest.getName());
        if (observedFinished.contains(questKey) || !isFinished(quest)) return false;

        observedFinished.add(questKey);
        saveObserved();
        itemPool.clearQuestRequirementCache();

        boolean changed = false;
        QuestRewardCatalog.Entry entry = QuestRewardCatalog.entryForQuestName(quest.getName());
        if (entry != null) changed |= unlockEntry(entry);
        changed |= recordFinishedTransition(quest);
        return changed;
    }

    /** Returns and clears the latest quest-completion point notification amount. */
    public long consumeLastPointAward()
    {
        long out = lastPointAward;
        lastPointAward = 0L;
        return out;
    }

    /**
     * A confirmed progression reset establishes the current OSRS quest state as history again. Already
     * completed quests therefore cannot become a repeatable point source.
     */
    public void resetBaseline()
    {
        observedFinished.clear();
        initialized = true;
        baselineBuilding = true;
        cursor = 0;
        lastPointAward = 0L;
        lastQuestPoints = currentQuestPoints();
        pendingQuestPoints = 0;
        awaitingFinishedCompletions = 0;
        pendingGrandmasterQuestPoints = 0;
        awaitingFinishedAgeTicks = 0;
        saveObserved();
        saveQuestPointTracking();
        configManager.setRSProfileConfiguration(
            ThreeChoicesConfig.GROUP, KEY_BASELINE_INITIALIZED, false);
        configManager.setRSProfileConfiguration(
            ThreeChoicesConfig.GROUP, KEY_BASELINE_BUILDING, true);
        configManager.setRSProfileConfiguration(
            ThreeChoicesConfig.GROUP, KEY_COMPLETION_BASELINE_VERSION, 0);
    }

    private void advanceBaseline()
    {
        Quest[] quests = Quest.values();
        if (quests.length == 0 || cursor >= quests.length)
        {
            finishBaseline();
            return;
        }

        // Any QP movement while baseline construction is active becomes part of
        // the historical baseline, never a payout. This also makes migration and
        // reset fail closed rather than exploitable.
        lastQuestPoints = currentQuestPoints();
        pendingQuestPoints = 0;
        awaitingFinishedCompletions = 0;
        pendingGrandmasterQuestPoints = 0;
        awaitingFinishedAgeTicks = 0;

        Quest quest = quests[cursor++];
        if (isFinished(quest)) observedFinished.add(key(quest.getName()));

        if (cursor >= quests.length) finishBaseline();
    }

    private void finishBaseline()
    {
        saveObserved();
        baselineBuilding = false;
        cursor = 0;
        lastQuestPoints = currentQuestPoints();
        pendingQuestPoints = 0;
        awaitingFinishedCompletions = 0;
        pendingGrandmasterQuestPoints = 0;
        awaitingFinishedAgeTicks = 0;
        saveQuestPointTracking();
        configManager.setRSProfileConfiguration(
            ThreeChoicesConfig.GROUP, KEY_BASELINE_INITIALIZED, true);
        configManager.setRSProfileConfiguration(
            ThreeChoicesConfig.GROUP, KEY_BASELINE_BUILDING, false);
        configManager.setRSProfileConfiguration(
            ThreeChoicesConfig.GROUP, KEY_COMPLETION_BASELINE_VERSION, COMPLETION_BASELINE_VERSION);
    }

    private void observeQuestPointDelta()
    {
        int current = currentQuestPoints();
        boolean changed = current != lastQuestPoints;
        if (current > lastQuestPoints)
        {
            int gained = current - lastQuestPoints;
            if (awaitingFinishedCompletions > 0)
            {
                // Handles the event-order case where FINISHED was observed one
                // tick before the QP varp increased. The awarded amount is still
                // the real QP delta, never a guessed quest difficulty value.
                pendingQuestPoints = safeAddInt(pendingQuestPoints, gained);
                awaitingFinishedCompletions = 0;
                awaitingFinishedAgeTicks = 0;
                awardPendingQuestPoints();
            }
            else
            {
                // QP changed before our staggered quest scan reached the newly
                // finished quest. Persist it until that FINISHED transition is
                // observed.
                pendingQuestPoints = safeAddInt(pendingQuestPoints, gained);
            }
        }
        else if (current < lastQuestPoints)
        {
            pendingQuestPoints = 0;
            awaitingFinishedCompletions = 0;
            pendingGrandmasterQuestPoints = 0;
            awaitingFinishedAgeTicks = 0;
        }
        else if (awaitingFinishedCompletions > 0)
        {
            // A genuine QP-bearing quest updates its QP varp immediately or
            // within a few game ticks. Expire a FINISHED observation that never
            // receives a QP delta so zero-QP activities cannot remain queued and
            // absorb some unrelated future quest's credit.
            awaitingFinishedAgeTicks++;
            if (awaitingFinishedAgeTicks > 8)
            {
                awaitingFinishedCompletions = 0;
                pendingGrandmasterQuestPoints = 0;
                awaitingFinishedAgeTicks = 0;
                changed = true;
            }
        }
        lastQuestPoints = current;
        if (changed) saveQuestPointTracking();
    }

    private boolean recordFinishedTransition(Quest quest)
    {
        addGrandmasterQuestPoints(quest);

        if (pendingQuestPoints > 0)
        {
            return awardPendingQuestPoints();
        }

        // FINISHED can become observable just before VarPlayerID.QP updates.
        // Remember that transition so a later positive QP delta can still be
        // paired safely across ticks or a RuneLite/plugin restart.
        awaitingFinishedCompletions = 1;
        awaitingFinishedAgeTicks = 0;
        saveQuestPointTracking();
        return false;
    }

    private void addGrandmasterQuestPoints(Quest quest)
    {
        if (quest == null) return;
        Integer questPoints = GRANDMASTER_QUEST_POINTS.get(key(quest.getName()));
        if (questPoints == null || questPoints <= 0) return;

        pendingGrandmasterQuestPoints = safeAddInt(pendingGrandmasterQuestPoints, questPoints);
        saveQuestPointTracking();
    }

    private boolean awardPendingQuestPoints()
    {
        if (pendingQuestPoints <= 0) return false;

        int grandmasterQuestPoints = Math.min(pendingQuestPoints, Math.max(0, pendingGrandmasterQuestPoints));
        int normalQuestPoints = pendingQuestPoints - grandmasterQuestPoints;
        long normalRate = normalPointsPerQuestPoint(stateService.getRollHistory().size());
        long normalAward = normalRate * (long) normalQuestPoints;
        long grandmasterAward = GRANDMASTER_POINTS_PER_QUEST_POINT * (long) grandmasterQuestPoints;
        long award = safeAddLong(normalAward, grandmasterAward);

        pendingQuestPoints = 0;
        pendingGrandmasterQuestPoints = 0;
        saveQuestPointTracking();
        stateService.addEarnedPoints(award);
        lastPointAward = safeAddLong(lastPointAward, award);
        return true;
    }

    static long normalPointsPerQuestPoint(int completedRolls)
    {
        int rolls = Math.max(0, completedRolls);
        if (rolls == 0) return QUEST_POINTS_PER_QP_0_ROLLS;
        if (rolls <= 2) return QUEST_POINTS_PER_QP_1_TO_2_ROLLS;
        if (rolls <= 5) return QUEST_POINTS_PER_QP_3_TO_5_ROLLS;
        if (rolls <= 10) return QUEST_POINTS_PER_QP_6_TO_10_ROLLS;
        return QUEST_POINTS_PER_QP_11_PLUS_ROLLS;
    }

    static long grandmasterTotalReward(String questName)
    {
        Integer qp = GRANDMASTER_QUEST_POINTS.get(key(questName));
        return qp == null ? 0L : GRANDMASTER_POINTS_PER_QUEST_POINT * (long) qp;
    }

    private int currentQuestPoints()
    {
        try
        {
            return Math.max(0, client.getVarpValue(VarPlayerID.QP));
        }
        catch (RuntimeException ignored)
        {
            return 0;
        }
    }

    private boolean unlockEntry(QuestRewardCatalog.Entry entry)
    {
        List<Integer> ids = new ArrayList<>();
        if (entry.fixedPrimaryRewardId > 0) ids.add(entry.fixedPrimaryRewardId);
        for (String rewardName : entry.rewardNames)
        {
            int id = resolveTradeableId(rewardName);
            if (id > 0 && !ids.contains(id)) ids.add(id);
        }

        boolean changed = stateService.addUnlockedItems(ids);
        boolean skillOpened = entry.skillGate != null && stateService.unlockSkill(entry.skillGate);
        changed |= skillOpened;
        if (!ids.isEmpty()) itemPool.warmDisplayCache(ids);
        if (skillOpened)
        {
            int imageId = entry.fixedPrimaryRewardId > 0 ? entry.fixedPrimaryRewardId : (ids.isEmpty() ? -1 : ids.get(0));
            if (imageId > 0)
            {
                String name = entry.rewardNames.isEmpty() ? entry.skillGate.getDisplayName() : entry.rewardNames.get(0);
                celebrationOverlay.show(imageId, name, false, entry.skillGate, "Quest reward unlocked");
            }
        }
        return changed;
    }

    private boolean isFinished(Quest quest)
    {
        if (quest == null) return false;
        try
        {
            return quest.getState(client) == QuestState.FINISHED;
        }
        catch (RuntimeException ignored)
        {
            return false;
        }
    }

    private int resolveTradeableId(String name)
    {
        String wanted = normalize(name);
        for (ItemPrice price : itemManager.search(name))
        {
            if (price != null && price.getName() != null && normalize(price.getName()).equals(wanted))
            {
                return price.getId();
            }
        }
        // Untradeable rewards are already usable because they are genuinely
        // self-earned; only tradeable rewards need exact unlock state for GE use.
        return -1;
    }

    private void loadObserved()
    {
        observedFinished.clear();
        String raw = configManager.getRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_OBSERVED);
        if (raw == null || raw.isBlank()) return;
        for (String part : raw.split(";"))
        {
            if (!part.isBlank()) observedFinished.add(part.trim().toLowerCase(Locale.ROOT));
        }
    }

    private void saveObserved()
    {
        List<String> sorted = new ArrayList<>(observedFinished);
        sorted.sort(String::compareTo);
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_OBSERVED, String.join(";", sorted));
    }

    private void saveQuestPointTracking()
    {
        configManager.setRSProfileConfiguration(
            ThreeChoicesConfig.GROUP, KEY_LAST_QUEST_POINTS, lastQuestPoints);
        configManager.setRSProfileConfiguration(
            ThreeChoicesConfig.GROUP, KEY_PENDING_QUEST_POINTS, pendingQuestPoints);
        configManager.setRSProfileConfiguration(
            ThreeChoicesConfig.GROUP, KEY_AWAITING_COMPLETIONS, awaitingFinishedCompletions);
        configManager.setRSProfileConfiguration(
            ThreeChoicesConfig.GROUP, KEY_PENDING_GRANDMASTER_QP, pendingGrandmasterQuestPoints);
    }

    private static int parseInt(String raw, int fallback)
    {
        if (raw == null || raw.isBlank()) return fallback;
        try
        {
            return Integer.parseInt(raw.trim());
        }
        catch (NumberFormatException ignored)
        {
            return fallback;
        }
    }

    private static int safeAddInt(int a, int b)
    {
        long value = (long) a + (long) b;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value));
    }

    private static long safeAddLong(long a, long b)
    {
        if (a <= 0L) return Math.max(0L, b);
        if (b <= 0L) return Math.max(0L, a);
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    private static String key(String text)
    {
        return normalize(text);
    }

    private static String normalize(String text)
    {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
