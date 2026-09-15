package com.osrssniffin.threechoices;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

@Singleton
public class ThreeChoicesStateService
{
    private static final int DATA_VERSION = 14;
    private static final String KEY_DATA_VERSION = "dataVersion";
    private static final String KEY_WELCOME_SEEN = "welcomeSeen";
    private static final String KEY_POINTS = "points";
    private static final String KEY_TOTAL_EARNED = "totalEarned";
    private static final String KEY_UNCREDITED_XP = "uncreditedXp";
    private static final String KEY_FRACTIONAL_POINT_MICROS = "fractionalPointMicros";
    private static final long POINT_MICROS = 1_000_000L;
    private static final String KEY_UNLOCKED = "unlockedItems";
    private static final String KEY_UNLOCKED_SKILLS = "unlockedSkills";
    private static final String KEY_PENDING = "pendingRoll";
    private static final String KEY_PENDING_META = "pendingRollMeta";
    private static final String KEY_HISTORY = "rollHistory";
    private static final String KEY_REROLL_BLOCKED_NEXT = "rerollBlockedNextRoll";
    private static final String KEY_PENDING_REROLL_ELIGIBLE = "pendingRerollEligible";
    private static final int MAX_HISTORY = 100;

    private final ConfigManager configManager;

    private long points;
    private long totalEarned;
    private long uncreditedXp;
    private long fractionalPointMicros;
    private boolean welcomeSeen;
    private final LinkedHashSet<Integer> unlockedItems = new LinkedHashSet<>();
    private final EnumSet<SkillGate> unlockedSkills = EnumSet.noneOf(SkillGate.class);
    private final List<Integer> pendingRoll = new ArrayList<>();
    private final Map<Integer, String> pendingChoiceMeta = new LinkedHashMap<>();
    private final List<String> rollHistory = new ArrayList<>();
    private boolean rerollBlockedNextRoll;
    private boolean pendingRerollEligible;

    @Inject
    public ThreeChoicesStateService(ConfigManager configManager)
    {
        this.configManager = configManager;
    }

    /**
     * Loads the profile and migrates older roll state. Old pending rolls are
     * cleared and refunded once because their pool format can no longer be
     * safely rendered/selected under the current rules revision.
     *
     * @return true if an old pending roll was refunded
     */
    public boolean loadAndMigrate(long rollCost)
    {
        points = Math.max(0L, parseLong(configManager.getRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_POINTS), 0L));
        totalEarned = Math.max(0L, parseLong(configManager.getRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_TOTAL_EARNED), 0L));
        uncreditedXp = Math.max(0L, parseLong(configManager.getRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_UNCREDITED_XP), 0L));
        fractionalPointMicros = Math.max(0L, parseLong(
            configManager.getRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_FRACTIONAL_POINT_MICROS), 0L)) % POINT_MICROS;

        // Older release candidates buffered ordinary skilling credit in 1,000-XP chunks.
        // Convert that already-earned remainder once so the continuous system does not lose it.
        if (uncreditedXp > 0L)
        {
            addFractionalPointMicros(safeMultiply(uncreditedXp, 100_000L));
            uncreditedXp = 0L;
            configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_UNCREDITED_XP, 0L);
        }
        welcomeSeen = Boolean.parseBoolean(configManager.getRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_WELCOME_SEEN));
        rerollBlockedNextRoll = Boolean.parseBoolean(
            configManager.getRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_REROLL_BLOCKED_NEXT));
        pendingRerollEligible = Boolean.parseBoolean(
            configManager.getRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_PENDING_REROLL_ELIGIBLE));

        unlockedItems.clear();
        unlockedItems.addAll(parseIds(configManager.getRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_UNLOCKED)));

        unlockedSkills.clear();
        String savedSkills = configManager.getRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_UNLOCKED_SKILLS);
        if (savedSkills != null && !savedSkills.isBlank())
        {
            for (String raw : savedSkills.split(","))
            {
                try
                {
                    unlockedSkills.add(SkillGate.valueOf(raw.trim()));
                }
                catch (IllegalArgumentException ignored)
                {
                    // Ignore stale/unknown enum names from development builds.
                }
            }
        }

        // Three Choices deliberately starts with only Woodcutting and Firemaking
        // open among gated skills. Tutorial Island's fishing/mining tools remain
        // owned starter items, but those skills must now be opened through a roll.
        applyStarterSkillBaseline();

        pendingRoll.clear();
        pendingRoll.addAll(parseIds(configManager.getRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_PENDING)));
        pendingChoiceMeta.clear();
        pendingChoiceMeta.putAll(parsePendingMeta(
            configManager.getRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_PENDING_META)));
        if (pendingRoll.size() != 3 || new LinkedHashSet<>(pendingRoll).size() != 3)
        {
            pendingRoll.clear();
            pendingChoiceMeta.clear();
            pendingRerollEligible = false;
        }
        else if (configManager.getRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_PENDING_REROLL_ELIGIBLE) == null)
        {
            // Pending rolls saved before the re-roll feature get one re-roll
            // rather than silently losing that option after an update.
            pendingRerollEligible = true;
            configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_PENDING_REROLL_ELIGIBLE, true);
        }
        else
        {
            pendingChoiceMeta.keySet().retainAll(new LinkedHashSet<>(pendingRoll));
        }

        rollHistory.clear();
        String history = configManager.getRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_HISTORY);
        if (history != null && !history.isBlank())
        {
            for (String entry : history.split(";"))
            {
                if (!entry.isBlank())
                {
                    rollHistory.add(entry);
                }
            }
        }
        while (rollHistory.size() > MAX_HISTORY)
        {
            rollHistory.remove(0);
        }

        int storedVersion = (int) parseLong(
            configManager.getRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_DATA_VERSION), 0L);
        boolean refunded = false;
        if (storedVersion < DATA_VERSION)
        {
            // Pending rolls from the current release format remain valid. Only
            // pre-v13 pending rolls use the old incompatible pool representation
            // and still require the historical refund migration.
            if (storedVersion < 13 && pendingRoll.size() == 3)
            {
                points = safeAdd(points, Math.max(0L, rollCost));
                pendingRoll.clear();
                pendingChoiceMeta.clear();
                pendingRerollEligible = false;
                configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_PENDING_REROLL_ELIGIBLE, false);
                refunded = true;
            }

            // Skill gates are stored separately from item unlocks. Very old
            // development profiles cannot infer skills from historical item IDs.
            if (storedVersion < 6)
            {
                unlockedSkills.clear();
                applyStarterSkillBaseline();
            }

            // v14 changes the fresh-mode baseline: Mining and Fishing are now
            // progression skills. Every older profile had them seeded for free,
            // so remove that legacy baseline once. After v14 they persist normally
            // if the player later opens them through a Three Choices selection.
            if (storedVersion < 14)
            {
                unlockedSkills.remove(SkillGate.MINING);
                unlockedSkills.remove(SkillGate.FISHING);
                applyStarterSkillBaseline();
            }
            saveUnlockedSkills();

            // Older profiles should see the rewritten rules page once. Later
            // revisions preserve the user's selected tab/welcome state.
            if (storedVersion < 8)
            {
                welcomeSeen = false;
                configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_WELCOME_SEEN, false);
            }

            configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_POINTS, points);
            // Preserve current-format pending rolls across a data-version migration.
            // Older incompatible rolls were already cleared/refunded above.
            configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_PENDING, joinIds(pendingRoll));
            configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_PENDING_META, joinPendingMeta(pendingChoiceMeta));
            configManager.setRSProfileConfiguration(
                ThreeChoicesConfig.GROUP, KEY_PENDING_REROLL_ELIGIBLE, pendingRerollEligible);
            configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_DATA_VERSION, DATA_VERSION);
        }
        return refunded;
    }

    public long getPoints()
    {
        return points;
    }

    public long getTotalEarned()
    {
        return totalEarned;
    }

    public long getUncreditedXp()
    {
        return uncreditedXp;
    }

    public boolean isWelcomeSeen()
    {
        return welcomeSeen;
    }

    public void setWelcomeSeen(boolean seen)
    {
        welcomeSeen = seen;
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_WELCOME_SEEN, seen);
    }

    public Set<Integer> getUnlockedItems()
    {
        return Collections.unmodifiableSet(unlockedItems);
    }

    public Set<SkillGate> getUnlockedSkills()
    {
        return Collections.unmodifiableSet(unlockedSkills);
    }

    public boolean isSkillUnlocked(SkillGate gate)
    {
        return gate != null && unlockedSkills.contains(gate);
    }

    public boolean unlockSkill(SkillGate gate)
    {
        if (gate == null || !unlockedSkills.add(gate))
        {
            return false;
        }
        saveUnlockedSkills();
        return true;
    }

    private void applyStarterSkillBaseline()
    {
        unlockedSkills.addAll(SkillGate.starterOpenSkills());
    }

    private void saveUnlockedSkills()
    {
        StringBuilder value = new StringBuilder();
        for (SkillGate unlocked : unlockedSkills)
        {
            if (value.length() > 0) value.append(',');
            value.append(unlocked.name());
        }
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_UNLOCKED_SKILLS, value.toString());
    }

    public List<Integer> getPendingRoll()
    {
        return Collections.unmodifiableList(pendingRoll);
    }

    public String getPendingChoiceMeta(int itemId)
    {
        return pendingChoiceMeta.get(itemId);
    }

    public List<String> getRollHistory()
    {
        return Collections.unmodifiableList(rollHistory);
    }

    public boolean canRerollPending()
    {
        return pendingRoll.size() == 3 && pendingRerollEligible;
    }

    public boolean isUnlocked(int itemId)
    {
        return unlockedItems.contains(itemId);
    }

    public boolean addUnlockedItems(Iterable<Integer> itemIds)
    {
        if (itemIds == null) return false;
        boolean changed = false;
        for (Integer itemId : itemIds)
        {
            if (itemId != null && itemId > 0)
            {
                changed |= unlockedItems.add(itemId);
            }
        }
        if (changed)
        {
            configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_UNLOCKED, joinIds(unlockedItems));
        }
        return changed;
    }

    /** Reset progression to the same fresh baseline used by the release UI. */
    public void resetAll()
    {
        points = 0L;
        totalEarned = 0L;
        uncreditedXp = 0L;
        fractionalPointMicros = 0L;
        welcomeSeen = false;
        unlockedItems.clear();
        unlockedSkills.clear();
        applyStarterSkillBaseline();
        pendingRoll.clear();
        pendingChoiceMeta.clear();
        rollHistory.clear();
        rerollBlockedNextRoll = false;
        pendingRerollEligible = false;

        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_POINTS, 0L);
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_TOTAL_EARNED, 0L);
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_UNCREDITED_XP, 0L);
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_FRACTIONAL_POINT_MICROS, 0L);
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_WELCOME_SEEN, false);
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_UNLOCKED, "");
        saveUnlockedSkills();
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_PENDING, "");
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_PENDING_META, "");
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_HISTORY, "");
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_REROLL_BLOCKED_NEXT, false);
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_PENDING_REROLL_ELIGIBLE, false);
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_DATA_VERSION, DATA_VERSION);
    }

    public void addEarnedPoints(long amount)
    {
        if (amount <= 0L)
        {
            return;
        }

        points = safeAdd(points, amount);
        totalEarned = safeAdd(totalEarned, amount);
        saveBalances();
    }


    public void applyNonCombatXp(long xpGained, long pointsPerThousandXp)
    {
        if (xpGained <= 0L || pointsPerThousandXp <= 0L)
        {
            return;
        }

        // Settle the caller-provided XP rate fractionally so normal actions
        // award whole points as soon as they are earned instead of waiting for
        // a 1,000-XP cliff.
        long microsPerXp = safeMultiply(pointsPerThousandXp, POINT_MICROS) / 1_000L;
        addFractionalPointMicros(safeMultiply(xpGained, microsPerXp));
    }

    public void addFractionalEarnedPoints(long pointMicros)
    {
        addFractionalPointMicros(pointMicros);
    }

    private void addFractionalPointMicros(long pointMicros)
    {
        if (pointMicros <= 0L)
        {
            return;
        }

        long pool = safeAdd(fractionalPointMicros, pointMicros);
        long wholePoints = pool / POINT_MICROS;
        fractionalPointMicros = pool % POINT_MICROS;

        if (wholePoints > 0L)
        {
            points = safeAdd(points, wholePoints);
            totalEarned = safeAdd(totalEarned, wholePoints);
            saveBalances();
        }
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_FRACTIONAL_POINT_MICROS, fractionalPointMicros);
    }

    public boolean beginRoll(long cost, List<Integer> choices)
    {
        return beginRoll(cost, choices, Collections.emptyMap());
    }

    public boolean beginRoll(long cost, List<Integer> choices, Map<Integer, String> choiceMeta)
    {
        if (cost <= 0L || points < cost || !pendingRoll.isEmpty() || choices == null || choices.size() != 3)
        {
            return false;
        }

        LinkedHashSet<Integer> unique = new LinkedHashSet<>(choices);
        if (unique.size() != 3)
        {
            return false;
        }
        for (int itemId : unique)
        {
            if (itemId <= 0 || unlockedItems.contains(itemId))
            {
                return false;
            }
        }

        points -= cost;
        pendingRoll.clear();
        pendingRoll.addAll(choices);

        // A re-roll is available on normal rolls unless this is the one-roll
        // cooldown immediately following a used re-roll. Starting that cooldown
        // roll consumes the block so the following roll is eligible again.
        pendingRerollEligible = !rerollBlockedNextRoll;
        if (rerollBlockedNextRoll)
        {
            rerollBlockedNextRoll = false;
            configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_REROLL_BLOCKED_NEXT, false);
        }

        pendingChoiceMeta.clear();
        if (choiceMeta != null)
        {
            for (int itemId : unique)
            {
                String value = choiceMeta.get(itemId);
                if (value != null && !value.isBlank())
                {
                    pendingChoiceMeta.put(itemId, value.trim());
                }
            }
        }
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_POINTS, points);
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_PENDING, joinIds(pendingRoll));
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_PENDING_META, joinPendingMeta(pendingChoiceMeta));
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_PENDING_REROLL_ELIGIBLE, pendingRerollEligible);
        return true;
    }

    public boolean replacePendingRollWithReroll(List<Integer> choices, Map<Integer, String> choiceMeta)
    {
        if (!canRerollPending() || choices == null || choices.size() != 3)
        {
            return false;
        }

        LinkedHashSet<Integer> unique = new LinkedHashSet<>(choices);
        if (unique.size() != 3)
        {
            return false;
        }
        for (int itemId : unique)
        {
            if (itemId <= 0 || unlockedItems.contains(itemId))
            {
                return false;
            }
        }

        pendingRoll.clear();
        pendingRoll.addAll(choices);
        pendingChoiceMeta.clear();
        if (choiceMeta != null)
        {
            for (int itemId : unique)
            {
                String value = choiceMeta.get(itemId);
                if (value != null && !value.isBlank())
                {
                    pendingChoiceMeta.put(itemId, value.trim());
                }
            }
        }

        pendingRerollEligible = false;
        rerollBlockedNextRoll = true;
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_PENDING, joinIds(pendingRoll));
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_PENDING_META, joinPendingMeta(pendingChoiceMeta));
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_PENDING_REROLL_ELIGIBLE, false);
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_REROLL_BLOCKED_NEXT, true);
        return true;
    }

    public boolean completeRoll(int selectedItemId)
    {
        if (pendingRoll.size() != 3 || !pendingRoll.contains(selectedItemId))
        {
            return false;
        }

        String historyEntry = pendingRoll.get(0) + "," + pendingRoll.get(1) + "," + pendingRoll.get(2) + ">" + selectedItemId;
        unlockedItems.add(selectedItemId);
        rollHistory.add(historyEntry);
        while (rollHistory.size() > MAX_HISTORY)
        {
            rollHistory.remove(0);
        }
        pendingRoll.clear();
        pendingChoiceMeta.clear();
        pendingRerollEligible = false;

        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_UNLOCKED, joinIds(unlockedItems));
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_PENDING, "");
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_PENDING_META, "");
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_PENDING_REROLL_ELIGIBLE, false);
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_HISTORY, String.join(";", rollHistory));
        return true;
    }

    private void saveBalances()
    {
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_POINTS, points);
        configManager.setRSProfileConfiguration(ThreeChoicesConfig.GROUP, KEY_TOTAL_EARNED, totalEarned);
    }

    private static long safeAdd(long left, long right)
    {
        if (right > 0L && left > Long.MAX_VALUE - right)
        {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long safeMultiply(long left, long right)
    {
        if (left <= 0L || right <= 0L)
        {
            return 0L;
        }
        if (left > Long.MAX_VALUE / right)
        {
            return Long.MAX_VALUE;
        }
        return left * right;
    }

    private static long parseLong(String raw, long fallback)
    {
        if (raw == null || raw.isBlank())
        {
            return fallback;
        }
        try
        {
            return Long.parseLong(raw);
        }
        catch (NumberFormatException ignored)
        {
            return fallback;
        }
    }

    private static List<Integer> parseIds(String raw)
    {
        List<Integer> ids = new ArrayList<>();
        if (raw == null || raw.isBlank())
        {
            return ids;
        }

        for (String part : raw.split(","))
        {
            try
            {
                int id = Integer.parseInt(part.trim());
                if (id > 0)
                {
                    ids.add(id);
                }
            }
            catch (NumberFormatException ignored)
            {
                // Ignore corrupt entries instead of preventing plugin startup.
            }
        }
        return ids;
    }

    private static Map<Integer, String> parsePendingMeta(String raw)
    {
        Map<Integer, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return out;
        for (String entry : raw.split(";"))
        {
            int split = entry.indexOf('=');
            if (split <= 0 || split >= entry.length() - 1) continue;
            try
            {
                int itemId = Integer.parseInt(entry.substring(0, split).trim());
                String value = entry.substring(split + 1).trim();
                if (itemId > 0 && !value.isBlank()) out.put(itemId, value);
            }
            catch (NumberFormatException ignored)
            {
                // Ignore stale/corrupt metadata instead of blocking profile load.
            }
        }
        return out;
    }

    private static String joinPendingMeta(Map<Integer, String> values)
    {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, String> entry : values.entrySet())
        {
            if (entry.getKey() == null || entry.getKey() <= 0 || entry.getValue() == null || entry.getValue().isBlank()) continue;
            if (sb.length() > 0) sb.append(';');
            sb.append(entry.getKey()).append('=').append(entry.getValue().replace(";", "").replace("=", ""));
        }
        return sb.toString();
    }

    private static String joinIds(Iterable<Integer> ids)
    {
        StringBuilder sb = new StringBuilder();
        for (Integer id : ids)
        {
            if (id == null || id <= 0)
            {
                continue;
            }
            if (sb.length() > 0)
            {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }
}
