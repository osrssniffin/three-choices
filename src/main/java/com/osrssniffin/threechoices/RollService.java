package com.osrssniffin.threechoices;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RollService
{
    public enum RollStatus
    {
        SUCCESS,
        REROLL_NOT_AVAILABLE,
        PENDING_ROLL,
        NOT_ENOUGH_POINTS,
        NOT_LOGGED_IN,
        ITEM_DATA_LOADING,
        NO_ELIGIBLE_ITEMS,
        FAILED
    }

    private static final int[] RELAXATION_STEPS = {0, 3, 6, 10};
    private static final int MAX_ROLL_QUALITY_ATTEMPTS = 5;
    private static final int MAX_DIRECTION_ATTEMPTS = 24;
    private static final int SPECIAL_CHANCE_PERCENT = 12;

    private final ThreeChoicesStateService stateService;
    private final ProgressionEconomyService economyService;
    private final ItemPool itemPool;
    private final AccountProgressService accountProgressService;
    private final ProgressionOpportunityService opportunityService;
    private final UnlockCelebrationOverlay celebrationOverlay;
    private final UnlockBundleCatalog bundleCatalog;
    private final TrainingMethodCatalog trainingMethodCatalog;
    private final ItemAccessService itemAccessService;
    private final SecureRandom random = new SecureRandom();

    @Inject
    public RollService(
        ThreeChoicesStateService stateService,
        ProgressionEconomyService economyService,
        ItemPool itemPool,
        AccountProgressService accountProgressService,
        ProgressionOpportunityService opportunityService,
        UnlockCelebrationOverlay celebrationOverlay,
        UnlockBundleCatalog bundleCatalog,
        TrainingMethodCatalog trainingMethodCatalog,
        ItemAccessService itemAccessService)
    {
        this.stateService = stateService;
        this.economyService = economyService;
        this.itemPool = itemPool;
        this.accountProgressService = accountProgressService;
        this.opportunityService = opportunityService;
        this.celebrationOverlay = celebrationOverlay;
        this.bundleCatalog = bundleCatalog;
        this.trainingMethodCatalog = trainingMethodCatalog;
        this.itemAccessService = itemAccessService;
    }

    /** Must be invoked on the RuneLite client thread. */
    public RollStatus rollThree()
    {
        if (!stateService.getPendingRoll().isEmpty()) return RollStatus.PENDING_ROLL;

        long cost = Math.max(1L, economyService.getRollCost());
        if (stateService.getPoints() < cost) return RollStatus.NOT_ENOUGH_POINTS;

        CombatProfile profile = accountProgressService.getSnapshot();
        if (!profile.isReady()) return RollStatus.NOT_LOGGED_IN;
        if (!itemPool.isLiveDataReady()) return RollStatus.ITEM_DATA_LOADING;

        // Identify account progression first. The complete OSRS item catalog is
        // not the roll pool; it is only a resolver for a progression direction.
        List<ProgressionOpportunity> opportunities = opportunityService.discover(profile);
        if (opportunities.size() < 3) return RollStatus.NO_ELIGIBLE_ITEMS;

        List<ResolvedProgressionChoice> resolved = List.of();
        for (int attempt = 0; attempt < MAX_ROLL_QUALITY_ATTEMPTS; attempt++)
        {
            resolved = generateProgressionRoll(profile, opportunities);
            if (passesFinalQualityGate(resolved, opportunities)) break;
            resolved = List.of();
        }

        if (resolved.size() != 3) return RollStatus.NO_ELIGIBLE_ITEMS;

        List<Integer> choices = new ArrayList<>(3);
        Map<Integer, String> choiceMeta = new LinkedHashMap<>();
        for (ResolvedProgressionChoice choice : resolved)
        {
            int choiceId = choice.candidate.getItemId();
            choices.add(choiceId);
            String methodKey = trainingMethodCatalog.methodKeyForRepresentative(choiceId, itemAccessService);
            if (methodKey != null) choiceMeta.put(choiceId, methodKey);
        }

        return stateService.beginRoll(cost, choices, choiceMeta) ? RollStatus.SUCCESS : RollStatus.FAILED;
    }

    /** Must be invoked on the RuneLite client thread. Replaces the current three for free. */
    public RollStatus rerollThree()
    {
        if (stateService.getPendingRoll().size() != 3) return RollStatus.FAILED;
        if (!stateService.canRerollPending()) return RollStatus.REROLL_NOT_AVAILABLE;

        CombatProfile profile = accountProgressService.getSnapshot();
        if (!profile.isReady()) return RollStatus.NOT_LOGGED_IN;
        if (!itemPool.isLiveDataReady()) return RollStatus.ITEM_DATA_LOADING;

        // Deliberately uses the exact same opportunity discovery, resolution,
        // weighting and final quality gate as a normal roll. The
        // only difference is that no points are charged. Existing pending items
        // remain in ItemPool's exclusion set while resolving the replacement,
        // preventing the re-roll from simply returning the same three choices.
        List<ProgressionOpportunity> opportunities = opportunityService.discover(profile);
        if (opportunities.size() < 3) return RollStatus.NO_ELIGIBLE_ITEMS;

        List<ResolvedProgressionChoice> resolved = List.of();
        for (int attempt = 0; attempt < MAX_ROLL_QUALITY_ATTEMPTS; attempt++)
        {
            resolved = generateProgressionRoll(profile, opportunities);
            if (passesFinalQualityGate(resolved, opportunities)) break;
            resolved = List.of();
        }
        if (resolved.size() != 3) return RollStatus.NO_ELIGIBLE_ITEMS;

        List<Integer> choices = new ArrayList<>(3);
        Map<Integer, String> choiceMeta = new LinkedHashMap<>();
        for (ResolvedProgressionChoice choice : resolved)
        {
            int choiceId = choice.candidate.getItemId();
            choices.add(choiceId);
            String methodKey = trainingMethodCatalog.methodKeyForRepresentative(choiceId, itemAccessService);
            if (methodKey != null) choiceMeta.put(choiceId, methodKey);
        }

        return stateService.replacePendingRollWithReroll(choices, choiceMeta)
            ? RollStatus.SUCCESS : RollStatus.FAILED;
    }

    /**
     * Select progression directions before resolving their representative items.
     * Empty directions are discarded and another real account opportunity is
     * tried. Hard requirements remain inside ItemPool and are never relaxed.
     */
    private List<ResolvedProgressionChoice> generateProgressionRoll(
        CombatProfile profile,
        List<ProgressionOpportunity> discovered)
    {
        List<ProgressionOpportunity> remaining = new ArrayList<>(discovered);
        List<ResolvedProgressionChoice> chosen = new ArrayList<>(3);
        Set<String> usedDecisionGroups = new HashSet<>();
        Set<Integer> usedItems = new HashSet<>();
        boolean allowSpecial = random.nextInt(100) < SPECIAL_CHANCE_PERCENT;
        boolean specialUsed = false;

        int attempts = 0;
        while (chosen.size() < 3 && !remaining.isEmpty() && attempts++ < MAX_DIRECTION_ATTEMPTS)
        {
            List<ProgressionOpportunity> selectable = new ArrayList<>();
            for (ProgressionOpportunity opportunity : remaining)
            {
                if (usedDecisionGroups.contains(opportunity.getDecisionGroup())) continue;
                if (opportunity.getType() == ProgressionOpportunityType.SPECIAL && (!allowSpecial || specialUsed)) continue;
                selectable.add(opportunity);
            }
            if (selectable.isEmpty()) break;

            ProgressionOpportunity direction = weightedOpportunity(selectable);
            remaining.remove(direction);

            List<RollCandidate> candidates = resolveDirection(direction, profile);
            if (candidates.isEmpty()) continue;

            RollCandidate candidate = weightedTop(candidates, 6);
            if (candidate == null || usedItems.contains(candidate.getItemId())) continue;
            if (!hasProgressionJustification(direction, candidate)) continue;
            if (isFunctionallyDuplicate(direction, candidate, chosen)) continue;

            chosen.add(new ResolvedProgressionChoice(direction, candidate));
            if (direction.getType() == ProgressionOpportunityType.SPECIAL) specialUsed = true;
            usedDecisionGroups.add(direction.getDecisionGroup());
            usedItems.add(candidate.getItemId());
        }
        return chosen;
    }

    private List<RollCandidate> resolveDirection(ProgressionOpportunity direction, CombatProfile profile)
    {
        List<RollCandidate> candidates = List.of();
        for (int relaxation : RELAXATION_STEPS)
        {
            candidates = itemPool.resolveOpportunity(
                direction,
                profile,
                stateService.getUnlockedItems(),
                stateService.getPendingRoll(),
                relaxation);
            if (!candidates.isEmpty()) break;
        }
        return candidates;
    }

    private boolean passesFinalQualityGate(
        List<ResolvedProgressionChoice> choices,
        List<ProgressionOpportunity> discovered)
    {
        if (choices == null || choices.size() != 3) return false;

        Set<Integer> ids = new HashSet<>();
        Set<String> groups = new HashSet<>();
        for (ResolvedProgressionChoice choice : choices)
        {
            if (choice == null || choice.candidate == null || choice.opportunity == null) return false;
            if (!ids.add(choice.candidate.getItemId())) return false;
            if (!groups.add(choice.opportunity.getDecisionGroup())) return false;
            if (!hasProgressionJustification(choice.opportunity, choice.candidate)) return false;
        }

        // If the account has enough distinct directions, a three-choice roll
        // must represent three different decisions rather than three versions of
        // essentially the same progression step.
        if (distinctDecisionGroups(discovered) >= 3 && groups.size() < 3) return false;
        return true;
    }

    private static int distinctDecisionGroups(List<ProgressionOpportunity> opportunities)
    {
        Set<String> groups = new HashSet<>();
        for (ProgressionOpportunity opportunity : opportunities)
        {
            groups.add(opportunity.getDecisionGroup());
        }
        return groups.size();
    }

    private static boolean hasProgressionJustification(
        ProgressionOpportunity opportunity,
        RollCandidate candidate)
    {
        if (opportunity.getReason() == null || opportunity.getReason().isBlank()) return false;
        if (candidate.getItemId() <= 0 || candidate.getName() == null || candidate.getName().isBlank()) return false;

        // Curated skilling/method choices already carry their target skill gate.
        if (opportunity.getType().isSkilling())
        {
            return candidate.getSkillGate() != null;
        }
        return opportunity.getType().isCombat() || opportunity.getType() == ProgressionOpportunityType.SPECIAL;
    }

    private static boolean isFunctionallyDuplicate(
        ProgressionOpportunity direction,
        RollCandidate candidate,
        List<ResolvedProgressionChoice> chosen)
    {
        for (ResolvedProgressionChoice existing : chosen)
        {
            if (existing.candidate.getItemId() == candidate.getItemId()) return true;
            if (existing.opportunity.getDecisionGroup().equals(direction.getDecisionGroup())) return true;

            // Two armour directions resolving to the same equipment slot are
            // normally one decision in disguise (for example two body upgrades),
            // even if one was labelled defensive and the other style-specific.
            if (isArmourDecision(direction.getType())
                && isArmourDecision(existing.opportunity.getType())
                && existing.candidate.getSlot() == candidate.getSlot())
            {
                return true;
            }

            // Likewise, do not spend two slots on same-slot jewellery/utility.
            if (isAccessoryDecision(direction.getType())
                && isAccessoryDecision(existing.opportunity.getType())
                && existing.candidate.getSlot() == candidate.getSlot())
            {
                return true;
            }
        }
        return false;
    }

    private static boolean isArmourDecision(ProgressionOpportunityType type)
    {
        return type == ProgressionOpportunityType.MELEE_ARMOUR
            || type == ProgressionOpportunityType.RANGED_ARMOUR
            || type == ProgressionOpportunityType.MAGIC_ARMOUR
            || type == ProgressionOpportunityType.DEFENSIVE_UPGRADE;
    }

    private static boolean isAccessoryDecision(ProgressionOpportunityType type)
    {
        return type == ProgressionOpportunityType.COMBAT_JEWELLERY
            || type == ProgressionOpportunityType.COMBAT_UTILITY;
    }

    private ProgressionOpportunity weightedOpportunity(List<ProgressionOpportunity> opportunities)
    {
        int total = 0;
        for (ProgressionOpportunity opportunity : opportunities)
        {
            total += Math.max(1, opportunity.getWeight());
        }
        int roll = random.nextInt(Math.max(1, total));
        for (ProgressionOpportunity opportunity : opportunities)
        {
            int weight = Math.max(1, opportunity.getWeight());
            if (roll < weight) return opportunity;
            roll -= weight;
        }
        return opportunities.get(0);
    }

    private RollCandidate weightedTop(List<RollCandidate> ranked, int maxWindow)
    {
        if (ranked == null || ranked.isEmpty()) return null;
        int n = Math.min(Math.max(1, maxWindow), ranked.size());
        int totalWeight = n * (n + 1) / 2;
        int roll = random.nextInt(totalWeight);
        for (int i = 0; i < n; i++)
        {
            int weight = n - i;
            if (roll < weight) return ranked.get(i);
            roll -= weight;
        }
        return ranked.get(0);
    }

    /** Must be invoked on the RuneLite client thread. */
    public boolean choose(int itemId)
    {
        RollCandidate selected = itemPool.describeExisting(itemId);
        UnlockBundleCatalog.Bundle bundle = selected.getCategory() == RollCategory.BUNDLE
            ? bundleCatalog.bundleForItem(itemId) : null;
        TrainingMethodCatalog.Method method = selected.getCategory() == RollCategory.SKILL_METHOD
            ? trainingMethodCatalog.methodForRepresentative(itemId, itemAccessService) : null;

        boolean saved = stateService.completeRoll(itemId);
        if (!saved) return false;

        String subtitle = "Permanently unlocked";
        if (bundle != null)
        {
            List<Integer> members = bundleCatalog.membersForRepresentative(itemId);
            stateService.addUnlockedItems(members);
            itemPool.warmDisplayCache(members);
        }
        if (method != null)
        {
            List<Integer> members = trainingMethodCatalog.membersForRepresentative(itemId, itemAccessService);
            stateService.addUnlockedItems(members);
            itemPool.warmDisplayCache(members);
            subtitle = method.grouped ? "Training method unlocked" : "Progression unlocked";
        }

        SkillGate openedSkill = null;
        if (selected.isSkilling() && selected.getSkillGate() != null
            && stateService.unlockSkill(selected.getSkillGate()))
        {
            openedSkill = selected.getSkillGate();
        }

        celebrationOverlay.show(
            itemId, selected.getName(), selected.getCategory().isShiny(), openedSkill, subtitle);
        return true;
    }
}
