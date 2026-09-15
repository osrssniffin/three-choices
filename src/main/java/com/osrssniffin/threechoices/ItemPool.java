package com.osrssniffin.threechoices;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemComposition;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.http.api.item.ItemPrice;

/**
 * Live choice pool. PvM gear comes from RuneLite's live item data and is ranked
 * against the player's actual combat stats. Skilling choices and special clue
 * unlocks are deliberately curated so resources and cosmetic junk never crowd
 * out useful progression.
 */
@Singleton
public class ItemPool
{
    private static final int MAX_PVM_CANDIDATES = 72;
    private static final int MAX_CLUE_CANDIDATES = 18;
    private static final int MAX_TOTAL_CANDIDATES = 120;
    private static final int MAX_ALLOWED_GEAR_REGRESSION = 8;

    private final Client client;
    private final ItemManager itemManager;
    private final ItemAccessService itemAccessService;
    private final AccountProgressService accountProgressService;
    private final SkillingUnlockCatalog skillingCatalog;
    private final TrainingMethodCatalog trainingMethodCatalog;
    private final UnlockBundleCatalog bundleCatalog;
    private final Map<Integer, RollCandidate> descriptorCache = new ConcurrentHashMap<>();
    private final Map<Quest, Boolean> questRequirementCache = new EnumMap<>(Quest.class);

    @Inject
    public ItemPool(
        Client client,
        ItemManager itemManager,
        ItemAccessService itemAccessService,
        AccountProgressService accountProgressService,
        SkillingUnlockCatalog skillingCatalog,
        TrainingMethodCatalog trainingMethodCatalog,
        UnlockBundleCatalog bundleCatalog)
    {
        this.client = client;
        this.itemManager = itemManager;
        this.itemAccessService = itemAccessService;
        this.accountProgressService = accountProgressService;
        this.skillingCatalog = skillingCatalog;
        this.trainingMethodCatalog = trainingMethodCatalog;
        this.bundleCatalog = bundleCatalog;
    }

    public boolean isLiveDataReady()
    {
        return !itemManager.search("").isEmpty();
    }

    public void clearCache()
    {
        descriptorCache.clear();
        questRequirementCache.clear();
        skillingCatalog.clearCache();
        trainingMethodCatalog.clearCache();
        bundleCatalog.clearCache();
    }

    /** Must be called on the RuneLite client thread. */
    public List<RollCandidate> eligibleItems(
        CombatProfile profile,
        Set<Integer> unlocked,
        List<Integer> pending,
        int relaxation)
    {
        if (profile == null || !profile.isReady())
        {
            return List.of();
        }

        Set<Integer> excluded = new HashSet<>();
        for (int id : unlocked)
        {
            excluded.add(itemAccessService.canonicalize(id));
        }
        for (int id : pending)
        {
            excluded.add(itemAccessService.canonicalize(id));
        }

        Map<Integer, RollCandidate> result = new LinkedHashMap<>();
        GearBenchmarks unlockedGear = buildUnlockedGearBenchmarks(unlocked);

        // Logical training methods (raw food, Smithing bars, grouped potion
        // recipes) may intentionally use resource items as progression keys.
        for (RollCandidate candidate : trainingMethodCatalog.candidates(accountProgressService, itemAccessService))
        {
            int id = itemAccessService.canonicalize(candidate.getItemId());
            if (id <= 0 || excluded.contains(id) || itemAccessService.isExactUnlocked(id))
            {
                continue;
            }
            putCandidate(result, id, candidate);
        }

        // Curated reusable skill choices are already level-matched by the skill they train.
        for (RollCandidate candidate : skillingCatalog.candidates(accountProgressService, itemAccessService))
        {
            int id = itemAccessService.canonicalize(candidate.getItemId());
            if (excluded.contains(id) || !itemAccessService.isRollUnlockTarget(id))
            {
                continue;
            }
            putCandidate(result, id, candidate);
        }

        // Grouped clue rewards (god pages / equivalent blessed d'hide variants)
        // enter as one shiny choice and unlock the whole group when selected.
        for (RollCandidate candidate : bundleCatalog.candidates(accountProgressService, itemAccessService))
        {
            int id = itemAccessService.canonicalize(candidate.getItemId());
            if (id <= 0 || excluded.contains(id))
            {
                continue;
            }
            putCandidate(result, id, candidate);
        }

        List<RollCandidate> pvm = new ArrayList<>();
        List<RollCandidate> clues = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        // Scan the complete live tradeable catalog, then rank it. We never stop
        // on an arbitrary early sample, which was the main source of odd rolls.
        for (ItemPrice priceEntry : itemManager.search(""))
        {
            if (priceEntry == null || priceEntry.getId() <= 0 || priceEntry.getName() == null)
            {
                continue;
            }

            String nameKey = normalizeName(priceEntry.getName());
            if (nameKey.isEmpty() || !seenNames.add(nameKey))
            {
                continue;
            }

            int canonicalId = itemAccessService.canonicalize(priceEntry.getId());
            if (excluded.contains(canonicalId) || StarterItemCatalog.isStarterName(priceEntry.getName())
                || SkillingUnlockCatalog.isCatalogName(priceEntry.getName())
                || QuestRewardCatalog.isDirectQuestRewardName(priceEntry.getName()))
            {
                continue;
            }

            // Bundle members never roll individually.
            if (ClueRewardCatalog.isGodPage(priceEntry.getName()) || ClueRewardCatalog.isGodBlessing(priceEntry.getName())
                || ClueRewardCatalog.isBlessedDragonhide(priceEntry.getName()))
            {
                continue;
            }

            RollCandidate candidate = buildGearCandidate(priceEntry, profile);
            if (candidate == null)
            {
                continue;
            }

            if (candidate.getCategory() == RollCategory.CLUE)
            {
                clues.add(candidate);
                continue;
            }

            if (!itemAccessService.isRollUnlockTarget(candidate.getItemId()))
            {
                continue;
            }
            if (!fitsAccount(candidate, relaxation))
            {
                continue;
            }
            if (isObsoleteAgainstUnlockedGear(candidate, unlockedGear))
            {
                continue;
            }
            pvm.add(candidate);
        }

        Comparator<RollCandidate> ranking = Comparator
            .comparingInt((RollCandidate c) -> ProgressionRules.progressionVariantPenalty(c.getName()))
            .thenComparingInt(ItemPool::fitDistance)
            .thenComparingInt(c -> stylePreferencePenalty(c, profile))
            .thenComparingInt(c -> slotPreferencePenalty(c.getSlot()))
            .thenComparing(Comparator.comparingInt(RollCandidate::getProgressionScore).reversed());
        pvm.sort(ranking);
        clues.sort(ranking);

        int pvmLimit = Math.min(MAX_PVM_CANDIDATES, pvm.size());
        for (int i = 0; i < pvmLimit && result.size() < MAX_TOTAL_CANDIDATES; i++)
        {
            RollCandidate candidate = pvm.get(i);
            putCandidate(result, candidate.getItemId(), candidate);
        }

        int clueLimit = Math.min(MAX_CLUE_CANDIDATES, clues.size());
        for (int i = 0; i < clueLimit && result.size() < MAX_TOTAL_CANDIDATES; i++)
        {
            RollCandidate candidate = clues.get(i);
            putCandidate(result, candidate.getItemId(), candidate);
        }

        return new ArrayList<>(result.values());
    }

    /**
     * Resolve one already-selected progression direction into concrete choices.
     * RollService chooses the progression direction first; the full live item
     * database is consulted only to represent that direction.
     */
    public List<RollCandidate> resolveOpportunity(
        ProgressionOpportunity opportunity,
        CombatProfile profile,
        Set<Integer> unlocked,
        List<Integer> pending,
        int relaxation)
    {
        if (opportunity == null || profile == null || !profile.isReady()) return List.of();

        Set<Integer> excluded = new HashSet<>();
        for (int id : unlocked) excluded.add(itemAccessService.canonicalize(id));
        for (int id : pending) excluded.add(itemAccessService.canonicalize(id));

        if (opportunity.hasFixedCandidates())
        {
            List<RollCandidate> fixed = new ArrayList<>();
            for (RollCandidate candidate : opportunity.getFixedCandidates())
            {
                int id = itemAccessService.canonicalize(candidate.getItemId());
                if (id <= 0 || excluded.contains(id) || itemAccessService.isExactUnlocked(id)) continue;
                descriptorCache.put(id, candidate);
                fixed.add(candidate);
            }
            fixed.sort(Comparator
                .comparingInt((RollCandidate c) -> Math.abs(c.getFitDelta()))
                .thenComparing(Comparator.comparingInt(RollCandidate::getProgressionScore).reversed()));
            return fixed;
        }

        if (!opportunity.getType().isCombat()) return List.of();

        GearBenchmarks unlockedGear = buildUnlockedGearBenchmarks(unlocked);
        List<RollCandidate> matches = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        for (ItemPrice priceEntry : itemManager.search(""))
        {
            if (priceEntry == null || priceEntry.getId() <= 0 || priceEntry.getName() == null) continue;

            String nameKey = normalizeName(priceEntry.getName());
            if (nameKey.isEmpty() || !seenNames.add(nameKey)) continue;

            int canonicalId = itemAccessService.canonicalize(priceEntry.getId());
            if (excluded.contains(canonicalId)
                || StarterItemCatalog.isStarterName(priceEntry.getName())
                || SkillingUnlockCatalog.isCatalogName(priceEntry.getName())
                || QuestRewardCatalog.isDirectQuestRewardName(priceEntry.getName())
                || ClueRewardCatalog.isGodPage(priceEntry.getName())
                || ClueRewardCatalog.isGodBlessing(priceEntry.getName())
                || ClueRewardCatalog.isBlessedDragonhide(priceEntry.getName()))
            {
                continue;
            }

            RollCandidate candidate = buildGearCandidate(priceEntry, profile);
            if (candidate == null || candidate.getCategory() != RollCategory.PVM) continue;
            if (!itemAccessService.isRollUnlockTarget(candidate.getItemId())) continue;
            if (!fitsAccount(candidate, relaxation)) continue;
            if (isObsoleteAgainstUnlockedGear(candidate, unlockedGear)) continue;

            ItemStats stats = itemManager.getItemStats(candidate.getItemId());
            if (stats == null || stats.getEquipment() == null) continue;
            ProgressionOpportunityType actualPurpose = ProgressionRules.progressionOpportunityType(
                candidate.getName(), stats.getEquipment());
            if (actualPurpose != opportunity.getType()) continue;
            if (!isProgressionDistanceAppropriate(candidate.getName(), stats.getEquipment(), unlockedGear)) continue;

            descriptorCache.put(candidate.getItemId(), candidate);
            matches.add(candidate);
        }

        matches.sort(Comparator
            .comparingInt((RollCandidate c) -> ProgressionRules.progressionVariantPenalty(c.getName()))
            .thenComparingInt(ItemPool::fitDistance)
            .thenComparingInt(c -> stylePreferencePenalty(c, profile))
            .thenComparingInt(c -> slotPreferencePenalty(c.getSlot()))
            .thenComparing(Comparator.comparingInt(RollCandidate::getProgressionScore).reversed()));

        int limit = Math.min(18, matches.size());
        return new ArrayList<>(matches.subList(0, limit));
    }

    /** Must be called on the RuneLite client thread. */
    public RollCandidate describeExisting(int itemId)
    {
        int canonical = itemAccessService.canonicalize(itemId);
        RollCandidate cached = descriptorCache.get(canonical);
        if (cached != null)
        {
            return cached;
        }

        TrainingMethodCatalog.Method method = trainingMethodCatalog.methodForRepresentative(canonical, itemAccessService);
        if (method != null)
        {
            RollCandidate training = new RollCandidate(
                canonical,
                method.displayName,
                0L,
                -300 - method.gate.ordinal(),
                CombatStyle.GENERAL,
                RollCategory.SKILL_METHOD,
                method.gate,
                method.targetLevel,
                accountProgressService.getLevel(method.gate));
            descriptorCache.put(canonical, training);
            return training;
        }

        UnlockBundleCatalog.Bundle bundle = bundleCatalog.bundleForItem(canonical);
        if (bundle != null)
        {
            // Only the current representative renders as the grouped shiny choice.
            // Once selected, every unlocked member goes back to its real item name
            // in Recent/Show Unlocks instead of appearing as duplicate bundle rows.
            int representative = -1;
            for (int member : bundleCatalog.membersForRepresentative(canonical))
            {
                if (!itemAccessService.isExactUnlocked(member))
                {
                    representative = member;
                    break;
                }
            }
            if (canonical == representative)
            {
                CombatProfile profile = accountProgressService.getSnapshot();
                RollCandidate grouped = new RollCandidate(
                    canonical,
                    bundle.displayName,
                    0L,
                    bundle.slot,
                    bundle.requiredRanged > 0 ? CombatStyle.RANGED : CombatStyle.GENERAL,
                    RollCategory.BUNDLE,
                    bundle.skillGate,
                    bundle.progressionScore,
                    bundle.requiredRanged > 0 ? profile.getRanged() : profile.highestOffensiveSkill());
                descriptorCache.put(canonical, grouped);
                return grouped;
            }
        }

        SkillGate specialGate = SkillingUnlockCatalog.gateForSpecialId(canonical);
        if (specialGate != null)
        {
            String specialName = SkillingUnlockCatalog.nameForSpecialId(canonical);
            RollCandidate special = new RollCandidate(
                canonical,
                specialName == null ? "Item " + canonical : specialName,
                0L,
                -200 - specialGate.ordinal(),
                CombatStyle.GENERAL,
                RollCategory.SKILLING,
                specialGate,
                SkillingUnlockCatalog.targetLevelForSpecialId(canonical),
                accountProgressService.getLevel(specialGate));
            descriptorCache.put(canonical, special);
            return special;
        }

        for (ItemPrice price : itemManager.search(""))
        {
            if (price == null || itemAccessService.canonicalize(price.getId()) != canonical)
            {
                continue;
            }

            String name = price.getName() == null ? "Item " + canonical : price.getName();
            SkillGate gate = SkillingUnlockCatalog.gateForName(name);
            RollCandidate candidate;
            if (gate != null)
            {
                candidate = new RollCandidate(
                    canonical,
                    name,
                    Math.max(0, itemManager.getWikiPrice(price)),
                    -200 - gate.ordinal(),
                    CombatStyle.GENERAL,
                    RollCategory.SKILLING,
                    gate,
                    1,
                    accountProgressService.getLevel(gate));
            }
            else
            {
                RollCategory category = ClueRewardCatalog.isMeaningfulSingle(name) ? RollCategory.CLUE : RollCategory.PVM;
                candidate = new RollCandidate(
                    canonical,
                    name,
                    Math.max(0, itemManager.getWikiPrice(price)),
                    -1,
                    CombatStyle.GENERAL,
                    category,
                    0,
                    0);
            }
            descriptorCache.put(canonical, candidate);
            return candidate;
        }

        RollCandidate fallback = new RollCandidate(
            canonical, "Item " + canonical, 0L, -1, CombatStyle.GENERAL, RollCategory.PVM, 0, 0);
        descriptorCache.put(canonical, fallback);
        return fallback;
    }

    /** Swing-safe: never calls ItemManager/client APIs. */
    public RollCandidate describeExistingCached(int itemId)
    {
        RollCandidate cached = descriptorCache.get(itemId);
        if (cached != null)
        {
            return cached;
        }
        String methodName = trainingMethodCatalog.displayNameForCachedItem(itemId);
        if (methodName != null)
        {
            return new RollCandidate(itemId, methodName, 0L, -300, CombatStyle.GENERAL, RollCategory.SKILL_METHOD, 0, 0);
        }
        String bundleName = bundleCatalog.displayNameForCachedItem(itemId);
        if (bundleName != null)
        {
            return new RollCandidate(itemId, bundleName, 0L, -400, CombatStyle.GENERAL, RollCategory.BUNDLE, 0, 0);
        }
        return new RollCandidate(
            itemId, "Item " + itemId, 0L, -1, CombatStyle.GENERAL, RollCategory.PVM, 0, 0);
    }

    /** Must be called on the RuneLite client thread. */
    public void warmDisplayCache(Iterable<Integer> itemIds)
    {
        if (itemIds == null)
        {
            return;
        }
        for (Integer itemId : itemIds)
        {
            if (itemId != null && itemId > 0)
            {
                describeExisting(itemId);
            }
        }
    }

    private RollCandidate buildGearCandidate(ItemPrice priceEntry, CombatProfile profile)
    {
        try
        {
            int itemId = itemAccessService.canonicalize(priceEntry.getId());
            String name = priceEntry.getName();
            if (ProgressionRules.isAlwaysAllowedByName(name)
                || ProgressionRules.isCosmeticVariant(name)
                || !ProgressionRules.resourceGates(name).isEmpty()
                || ProgressionRules.variantSkillGate(name) != null
                || SkillingUnlockCatalog.isCatalogName(name)
                || QuestRewardCatalog.isDirectQuestRewardName(name))
            {
                return null;
            }

            RollCategory category = ClueRewardCatalog.isMeaningfulSingle(name) ? RollCategory.CLUE : RollCategory.PVM;
            if (category == RollCategory.PVM && ClueRewardCatalog.isLikelyClueReward(name))
            {
                return null;
            }

            ItemStats stats = itemManager.getItemStats(itemId);
            if (stats == null || !stats.isEquipable() || stats.getEquipment() == null)
            {
                return null;
            }

            ItemEquipmentStats equipment = stats.getEquipment();
            int price = Math.max(0, itemManager.getWikiPrice(priceEntry));
            if (price <= 0 || !ProgressionRules.isUsefulPvmGear(name, equipment, price))
            {
                return null;
            }

            // Hard requirements are not relaxed. A 30 Attack account never sees
            // a Dragon scimitar just because a later fallback widened scoring.
            if (!CombatRequirementRules.canUse(profile, name, equipment) || !meetsQuestRequirement(name))
            {
                return null;
            }

            CombatStyle style = ProgressionRules.classifyStyle(equipment);
            int score = ProgressionRules.progressionScore(equipment, style, price);
            int capacity = profile.capacityFor(style, equipment.getSlot());
            RollCandidate candidate = new RollCandidate(itemId, name, price, equipment.getSlot(), style, category, score, capacity);

            if (category == RollCategory.CLUE)
            {
                return fitsAccount(candidate, 0) ? candidate : null;
            }
            return candidate;
        }
        catch (RuntimeException ignored)
        {
            return null;
        }
    }


    /** Must be called on the RuneLite client thread. */
    private boolean meetsQuestRequirement(String itemName)
    {
        Quest required = CombatQuestRequirementRules.requiredQuest(itemName);
        if (required == null) return true;

        Boolean cached = questRequirementCache.get(required);
        if (cached != null) return cached;

        try
        {
            boolean finished = required.getState(client) == QuestState.FINISHED;
            questRequirementCache.put(required, finished);
            return finished;
        }
        catch (RuntimeException ex)
        {
            // Hard requirements fail closed. A transient script/API failure must
            // never turn an unknown quest prerequisite into unrestricted gear.
            return false;
        }
    }

    /** Quest completion can make previously gated combat families resolvable. */
    public void clearQuestRequirementCache()
    {
        questRequirementCache.clear();
    }

    private GearBenchmarks buildUnlockedGearBenchmarks(Set<Integer> unlocked)
    {
        GearBenchmarks out = new GearBenchmarks();
        if (unlocked == null || unlocked.isEmpty()) return out;

        for (Integer rawId : unlocked)
        {
            if (rawId == null || rawId <= 0) continue;
            try
            {
                int id = itemAccessService.canonicalize(rawId);
                ItemStats stats = itemManager.getItemStats(id);
                if (stats == null || !stats.isEquipable() || stats.getEquipment() == null) continue;

                ItemEquipmentStats equipment = stats.getEquipment();
                CombatStyle style = ProgressionRules.classifyStyle(equipment);
                int price = Math.max(0, itemManager.getItemPrice(id));
                int score = ProgressionRules.progressionScore(equipment, style, price);
                int slot = equipment.getSlot();
                out.bestBySlot.merge(slot, score, Math::max);
                if (slot == EquipmentInventorySlot.WEAPON.getSlotIdx())
                {
                    out.bestWeaponByStyle.merge(style, score, Math::max);
                }

                ItemComposition composition = itemManager.getItemComposition(id);
                String itemName = composition == null ? "" : composition.getName();
                CombatProgressionFamily family = CombatProgressionFamily.classify(itemName, equipment);
                if (family != null)
                {
                    int band = CombatProgressionFamily.progressionBand(itemName, equipment);
                    if (band != Integer.MAX_VALUE)
                    {
                        out.recognizedCombatUnlocks++;
                        out.highestProgressionBand = Math.max(out.highestProgressionBand, band);
                    }
                }
            }
            catch (RuntimeException ignored)
            {
                // Candidate generation fails conservatively if a definition is
                // temporarily unavailable; one missing benchmark must not abort a roll.
            }
        }
        return out;
    }

    private static boolean isObsoleteAgainstUnlockedGear(RollCandidate candidate, GearBenchmarks benchmarks)
    {
        if (candidate == null || candidate.getCategory() != RollCategory.PVM) return false;
        Integer best;
        if (candidate.getSlot() == EquipmentInventorySlot.WEAPON.getSlotIdx())
        {
            best = benchmarks.bestWeaponByStyle.get(candidate.getStyle());
            if (best == null && candidate.getStyle() == CombatStyle.GENERAL)
            {
                best = benchmarks.bestBySlot.get(candidate.getSlot());
            }
        }
        else
        {
            best = benchmarks.bestBySlot.get(candidate.getSlot());
        }
        return best != null && candidate.getProgressionScore() + MAX_ALLOWED_GEAR_REGRESSION < best;
    }

    /**
     * Prevents a high-stat but progression-fresh account from skipping directly
     * to late/endgame families. Hard requirements are checked separately.
     *
     * With no rolled combat progression yet, only foundational band-0 gear can
     * appear. Once the account owns a recognized combat band, the next adjacent
     * band becomes eligible. This permits meaningful jumps without making OSRS
     * stats alone equivalent to Three Choices progression.
     */
    private static boolean isProgressionDistanceAppropriate(
        String itemName, ItemEquipmentStats equipment, GearBenchmarks benchmarks)
    {
        int candidateBand = CombatProgressionFamily.progressionBand(itemName, equipment);
        if (candidateBand == Integer.MAX_VALUE) return false;
        if (benchmarks == null || benchmarks.recognizedCombatUnlocks == 0)
        {
            return candidateBand == 0;
        }
        return candidateBand <= benchmarks.highestProgressionBand + 1;
    }

    private static final class GearBenchmarks
    {
        final Map<Integer, Integer> bestBySlot = new HashMap<>();
        final Map<CombatStyle, Integer> bestWeaponByStyle = new HashMap<>();
        int recognizedCombatUnlocks;
        int highestProgressionBand = -1;
    }

    private void putCandidate(Map<Integer, RollCandidate> result, int id, RollCandidate candidate)
    {
        result.putIfAbsent(id, candidate);
        descriptorCache.put(id, candidate);
    }

    static boolean fitsAccount(RollCandidate candidate, int relaxation)
    {
        if (candidate.isSkilling())
        {
            return true; // catalog already performs exact per-skill level matching
        }

        int relaxed = Math.max(0, relaxation);
        int capacity = candidate.getAccountCapacity();
        int score = candidate.getProgressionScore();
        int upper = Math.min(99, capacity + 4 + relaxed);
        int lower = Math.max(1, capacity - 16 - relaxed);
        return score >= lower && score <= upper;
    }

    private static int fitDistance(RollCandidate c)
    {
        return Math.abs(c.getProgressionScore() - c.getAccountCapacity());
    }

    private static int stylePreferencePenalty(RollCandidate c, CombatProfile profile)
    {
        if (c.getStyle() == CombatStyle.GENERAL) return 2;
        int level;
        switch (c.getStyle())
        {
            case MELEE: level = Math.max(profile.getAttack(), profile.getStrength()); break;
            case RANGED: level = profile.getRanged(); break;
            case MAGIC: level = profile.getMagic(); break;
            default: level = 1;
        }
        return 99 - level;
    }

    private static int slotPreferencePenalty(int slot)
    {
        if (slot == EquipmentInventorySlot.WEAPON.getSlotIdx()) return 0;
        if (slot == EquipmentInventorySlot.BODY.getSlotIdx() || slot == EquipmentInventorySlot.LEGS.getSlotIdx()) return 1;
        if (slot == EquipmentInventorySlot.AMULET.getSlotIdx() || slot == EquipmentInventorySlot.RING.getSlotIdx()) return 2;
        return 3;
    }

    static CombatStyle classifyStyle(ItemEquipmentStats equipment)
    {
        return ProgressionRules.classifyStyle(equipment);
    }

    static int progressionScore(ItemEquipmentStats equipment, CombatStyle style, int price)
    {
        return ProgressionRules.progressionScore(equipment, style, price);
    }

    static int statProgressionScore(ItemEquipmentStats equipment, CombatStyle style)
    {
        return ProgressionRules.statProgressionScore(equipment, style);
    }

    static int valueProgressionScore(int price)
    {
        return ProgressionRules.valueProgressionScore(price);
    }

    private static String normalizeName(String name)
    {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
