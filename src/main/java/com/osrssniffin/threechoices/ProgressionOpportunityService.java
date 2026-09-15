package com.osrssniffin.threechoices;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Builds progression directions from account state. The complete OSRS item
 * catalog is deliberately not consulted here for generic PvM gear; ItemPool
 * only resolves an item after RollService selects one of these directions.
 */
@Singleton
public class ProgressionOpportunityService
{
    private final ThreeChoicesStateService stateService;
    private final AccountProgressService progressService;
    private final ItemAccessService itemAccessService;
    private final SkillingUnlockCatalog skillingCatalog;
    private final TrainingMethodCatalog trainingMethodCatalog;
    private final UnlockBundleCatalog bundleCatalog;

    @Inject
    public ProgressionOpportunityService(
        ThreeChoicesStateService stateService,
        AccountProgressService progressService,
        ItemAccessService itemAccessService,
        SkillingUnlockCatalog skillingCatalog,
        TrainingMethodCatalog trainingMethodCatalog,
        UnlockBundleCatalog bundleCatalog)
    {
        this.stateService = stateService;
        this.progressService = progressService;
        this.itemAccessService = itemAccessService;
        this.skillingCatalog = skillingCatalog;
        this.trainingMethodCatalog = trainingMethodCatalog;
        this.bundleCatalog = bundleCatalog;
    }

    /** Must be called on the RuneLite client thread. */
    public List<ProgressionOpportunity> discover(CombatProfile profile)
    {
        if (profile == null || !profile.isReady()) return List.of();

        List<ProgressionOpportunity> out = new ArrayList<>();

        // These are directions, not item candidates. Whether a direction can
        // currently resolve is decided later under hard requirements.
        out.add(ProgressionOpportunity.combat(
            ProgressionOpportunityType.MELEE_WEAPON,
            "Improves or expands the account's usable melee weapon progression."));
        out.add(ProgressionOpportunity.combat(
            ProgressionOpportunityType.RANGED_WEAPON,
            "Improves or expands the account's usable Ranged weapon progression."));
        out.add(ProgressionOpportunity.combat(
            ProgressionOpportunityType.MAGIC_WEAPON,
            "Improves or expands the account's usable Magic weapon progression."));
        out.add(ProgressionOpportunity.combat(
            ProgressionOpportunityType.MELEE_ARMOUR,
            "Provides a meaningful melee-oriented armour progression step."));
        out.add(ProgressionOpportunity.combat(
            ProgressionOpportunityType.RANGED_ARMOUR,
            "Provides a meaningful Ranged-oriented armour progression step."));
        out.add(ProgressionOpportunity.combat(
            ProgressionOpportunityType.MAGIC_ARMOUR,
            "Provides a meaningful Magic-oriented armour progression step."));
        out.add(ProgressionOpportunity.combat(
            ProgressionOpportunityType.DEFENSIVE_UPGRADE,
            "Provides a genuine defensive upgrade or useful defensive niche."));
        out.add(ProgressionOpportunity.combat(
            ProgressionOpportunityType.COMBAT_JEWELLERY,
            "Provides meaningful combat jewellery progression."));
        out.add(ProgressionOpportunity.combat(
            ProgressionOpportunityType.COMBAT_UTILITY,
            "Adds a legitimate combat utility or niche capability."));

        Map<String, MutableOpportunity> curated = new LinkedHashMap<>();

        for (RollCandidate candidate : trainingMethodCatalog.candidates(progressService, itemAccessService))
        {
            SkillGate gate = candidate.getSkillGate();
            ProgressionOpportunityType type = gate != null && !stateService.isSkillUnlocked(gate)
                ? ProgressionOpportunityType.SKILL_OPENING
                : ProgressionOpportunityType.SKILL_METHOD;
            addCurated(curated, type, gate, candidate,
                type == ProgressionOpportunityType.SKILL_OPENING
                    ? "Opens " + gate.getDisplayName() + " with a complete level-appropriate training method."
                    : "Adds a level-appropriate " + gate.getDisplayName() + " training method.");
        }

        for (RollCandidate candidate : skillingCatalog.candidates(progressService, itemAccessService))
        {
            SkillGate gate = candidate.getSkillGate();
            ProgressionOpportunityType type = classifySkilling(candidate);
            String reason;
            if (type == ProgressionOpportunityType.SKILL_OPENING)
            {
                reason = "Opens " + gate.getDisplayName() + " with a logical usable starting method.";
            }
            else if (type == ProgressionOpportunityType.TOOL_PROGRESSION)
            {
                reason = "Improves the account's " + gate.getDisplayName() + " tool progression.";
            }
            else if (type == ProgressionOpportunityType.SKILL_EQUIPMENT)
            {
                reason = "Adds equipment that genuinely improves or supports " + gate.getDisplayName() + ".";
            }
            else
            {
                reason = "Adds a meaningful " + gate.getDisplayName() + " training method or capability.";
            }
            addCurated(curated, type, gate, candidate, reason);
        }

        for (MutableOpportunity value : curated.values())
        {
            out.add(ProgressionOpportunity.curated(
                value.type,
                value.gate,
                "CURATED",
                value.reason,
                value.candidates));
        }

        // Bundles/clue families remain occasional special progression, never
        // generic pool filler. Each distinct bundle is its own decision group.
        for (RollCandidate candidate : bundleCatalog.candidates(progressService, itemAccessService))
        {
            out.add(ProgressionOpportunity.special(
                Integer.toString(candidate.getItemId()),
                "Unlocks a coherent special reward family rather than an isolated filler item.",
                List.of(candidate)));
        }

        return out;
    }

    private ProgressionOpportunityType classifySkilling(RollCandidate candidate)
    {
        SkillGate gate = candidate.getSkillGate();
        if (gate != null && !stateService.isSkillUnlocked(gate))
        {
            return ProgressionOpportunityType.SKILL_OPENING;
        }
        if (ProgressionRules.toolGroup(candidate.getName()) != ProgressionRules.ToolGroup.NONE)
        {
            return ProgressionOpportunityType.TOOL_PROGRESSION;
        }

        String n = candidate.getName() == null ? "" : candidate.getName().toLowerCase(Locale.ROOT);
        if (containsAny(n, "gauntlet", "glove", "bracelet", "necklace", "amulet", "ring", "boots"))
        {
            return ProgressionOpportunityType.SKILL_EQUIPMENT;
        }
        return ProgressionOpportunityType.SKILL_METHOD;
    }

    private static void addCurated(
        Map<String, MutableOpportunity> grouped,
        ProgressionOpportunityType type,
        SkillGate gate,
        RollCandidate candidate,
        String reason)
    {
        String gateKey = gate == null ? "GENERAL" : gate.name();
        String key = type.name() + ":" + gateKey;
        MutableOpportunity value = grouped.computeIfAbsent(
            key, ignored -> new MutableOpportunity(type, gate, reason));
        value.candidates.add(candidate);
    }

    private static boolean containsAny(String text, String... values)
    {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private static final class MutableOpportunity
    {
        final ProgressionOpportunityType type;
        final SkillGate gate;
        final String reason;
        final List<RollCandidate> candidates = new ArrayList<>();

        MutableOpportunity(ProgressionOpportunityType type, SkillGate gate, String reason)
        {
            this.type = type;
            this.gate = gate;
            this.reason = reason;
        }
    }
}
