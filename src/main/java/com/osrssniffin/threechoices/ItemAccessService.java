package com.osrssniffin.threechoices;

import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ItemComposition;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/** Single source of truth for item, GE/shop, and skill access in Three Choices. */
@Singleton
public class ItemAccessService
{
    private enum Kind
    {
        STARTER,
        QUEST_FREE_SELF_OBTAINED,
        PVM,
        CLUE_SINGLE,
        CLUE_BUNDLE_MEMBER,
        SKILL_UNLOCK,
        SKILL_VARIANT,
        RESOURCE,
        EARNED_UNTRADEABLE,
        LOCKED_UNLISTED
    }

    private static final class Descriptor
    {
        final int canonicalId;
        final String name;
        final Kind kind;
        final SkillGate skillGate;
        final EnumSet<SkillGate> resourceGates;

        Descriptor(int canonicalId, String name, Kind kind, SkillGate skillGate, EnumSet<SkillGate> resourceGates)
        {
            this.canonicalId = canonicalId;
            this.name = name;
            this.kind = kind;
            this.skillGate = skillGate;
            this.resourceGates = resourceGates == null ? SkillGate.none() : resourceGates;
        }
    }

    private final ItemManager itemManager;
    private final ThreeChoicesStateService stateService;
    private final Map<Integer, Descriptor> descriptorCache = new ConcurrentHashMap<>();

    @Inject
    public ItemAccessService(ItemManager itemManager, ThreeChoicesStateService stateService)
    {
        this.itemManager = itemManager;
        this.stateService = stateService;
    }

    public void clearCache()
    {
        descriptorCache.clear();
    }

    /** Can the owned/self-obtained item be used/equipped? */
    public boolean isAllowed(int itemId)
    {
        Descriptor d = describe(itemId);
        if (stateService.isUnlocked(d.canonicalId)) return true;

        // Anything that represents permanent Three Choices progression stays
        // unusable until that exact unlock has actually been earned. Keep this
        // invariant ahead of all convenience/resource exceptions so a future
        // catalog overlap cannot accidentally make a rollable item usable early.
        if (mustRemainLockedUntilExactUnlock(d)) return false;

        // A curated quest nuisance exception remains self-obtainable even when
        // the same item is also a locked skill resource (for example a spade,
        // wool, ashes, or a quest food ingredient). Skill-action enforcement
        // still prevents using that exception to train a closed skill.
        if (QuestFreeItemCatalog.isFreeSelfObtained(d.name)) return true;
        // Cooking access controls training, not the ability to eat legally obtained
        // cooked food. Acquisition remains restricted separately below.
        if (ProgressionRules.isCookedFoodName(d.name)) return true;
        if (isCrossSkillSupportAllowed(d)) return true;

        switch (d.kind)
        {
            case STARTER:
            case QUEST_FREE_SELF_OBTAINED:
            case EARNED_UNTRADEABLE:
                return true;
            case SKILL_VARIANT:
                return isSkillUnlocked(d.skillGate);
            case RESOURCE:
                return anyGateOpen(d.resourceGates);
            default:
                return false;
        }
    }

    public boolean isLocked(int itemId)
    {
        return itemId > 0 && !isAllowed(itemId);
    }

    /**
     * Strict acquisition allowlist used by both GE and shops. Quest nuisance
     * items are deliberately excluded here: they must be gathered/made/earned.
     * Untradeables that cannot exist in the live tradeable roll pool remain
     * obtainable through their normal in-game reward/shop routes.
     */
    public boolean isAcquisitionAllowed(int itemId)
    {
        Descriptor d = describe(itemId);
        if (stateService.isUnlocked(d.canonicalId)) return true;
        if (mustRemainLockedUntilExactUnlock(d)) return false;
        if (d.kind == Kind.STARTER || d.kind == Kind.EARNED_UNTRADEABLE) return true;
        if (isCrossSkillSupportAllowed(d)) return true;
        if (d.kind == Kind.RESOURCE) return anyGateOpen(d.resourceGates);
        return false;
    }

    public boolean isGeAllowed(int itemId)
    {
        return isAcquisitionAllowed(itemId);
    }

    public boolean isExactUnlocked(int itemId)
    {
        return stateService.isUnlocked(canonicalize(itemId));
    }

    /**
     * Skill access is explicit progression state. Woodcutting and Firemaking are
     * the only gated skills seeded open; every other gated skill, including Mining
     * and Fishing, opens only through a Three Choices progression pick.
     */
    public boolean isSkillUnlocked(SkillGate gate)
    {
        return gate == null || stateService.isSkillUnlocked(gate);
    }

    public int countUnlockedSkills()
    {
        // Attack, Strength, Defence, Hitpoints, Ranged and Magic are permanently
        // open. Prayer remains a real SkillGate and does not get counted here.
        int count = 6;
        for (SkillGate gate : SkillGate.values())
        {
            if (isSkillUnlocked(gate)) count++;
        }
        return count;
    }

    public int getTotalSkillCount()
    {
        return 24;
    }

    public boolean isSkillOpen(Skill skill)
    {
        if (skill == null) return false;
        switch (skill)
        {
            case ATTACK:
            case STRENGTH:
            case DEFENCE:
            case HITPOINTS:
            case RANGED:
            case MAGIC:
                return true;
            default:
                SkillGate gate = SkillGate.fromSkill(skill);
                return gate != null && isSkillUnlocked(gate);
        }
    }

    public boolean isRollUnlockTarget(int itemId)
    {
        Descriptor d = describe(itemId);
        if (stateService.isUnlocked(d.canonicalId)) return false;

        // Tutorial Island items are baseline unlocks and must never consume a roll.
        if (d.kind == Kind.STARTER) return false;

        return d.kind == Kind.PVM || d.kind == Kind.CLUE_SINGLE || d.kind == Kind.SKILL_UNLOCK;
    }

    /**
     * Ground pickup is intentionally stricter than ordinary owned-item use.
     * Exact unlocks, starter items, opened-skill resources and genuinely
     * self-earned untradeables may be picked up. Tradeable progression gear
     * still requires an exact Three Choices unlock.
     */
    public boolean isGroundPickupAllowed(int itemId)
    {
        Descriptor d = describe(itemId);
        if (stateService.isUnlocked(d.canonicalId)) return true;
        if (mustRemainLockedUntilExactUnlock(d)) return false;
        if (d.kind == Kind.STARTER || QuestFreeItemCatalog.isFreeSelfObtained(d.name)) return true;
        if (d.kind == Kind.EARNED_UNTRADEABLE) return true;
        if (isCrossSkillSupportAllowed(d)) return true;
        return d.kind == Kind.RESOURCE && anyGateOpen(d.resourceGates);
    }

    public boolean isStarterItem(int itemId)
    {
        return describe(itemId).kind == Kind.STARTER;
    }

    public boolean isQuestFreeSelfObtained(int itemId)
    {
        return describe(itemId).kind == Kind.QUEST_FREE_SELF_OBTAINED;
    }

    public int canonicalize(int itemId)
    {
        if (itemId <= 0) return itemId;
        try
        {
            return itemManager.canonicalize(itemId);
        }
        catch (RuntimeException ex)
        {
            return itemId;
        }
    }

    public String lockedReason(int itemId)
    {
        Descriptor d = describe(itemId);
        if (d.kind == Kind.SKILL_UNLOCK && d.skillGate != null)
        {
            return d.name + " has not been chosen yet. It can open " + d.skillGate.getDisplayName() + ".";
        }
        if (d.kind == Kind.SKILL_VARIANT && d.skillGate != null)
        {
            return d.skillGate.getDisplayName() + " is still locked.";
        }
        if (d.kind == Kind.RESOURCE)
        {
            if (d.resourceGates.size() == 1)
            {
                SkillGate gate = d.resourceGates.iterator().next();
                return d.name + " becomes available after " + gate.getDisplayName() + " is opened.";
            }
            return d.name + " becomes available after a related skill is opened.";
        }
        if (d.kind == Kind.QUEST_FREE_SELF_OBTAINED)
        {
            return d.name + " must be gathered or made yourself; it is not available from the GE or shops.";
        }
        if (d.kind == Kind.CLUE_BUNDLE_MEMBER)
        {
            return d.name + " is a Treasure Trail unlock and has not been chosen yet.";
        }
        if (d.kind == Kind.CLUE_SINGLE || d.kind == Kind.PVM)
        {
            return d.name + " has not been chosen and unlocked yet.";
        }
        return "That item is locked by Three Choices.";
    }


    private static boolean mustRemainLockedUntilExactUnlock(Descriptor d)
    {
        if (d == null) return true;
        switch (d.kind)
        {
            case PVM:
            case CLUE_SINGLE:
            case CLUE_BUNDLE_MEMBER:
            case SKILL_UNLOCK:
            case LOCKED_UNLISTED:
                return true;
            default:
                return false;
        }
    }

    private boolean isCrossSkillSupportAllowed(Descriptor d)
    {
        // Hammer is the Smithing entry key, but is also mandatory basic
        // Construction support. Opening Construction should make a hammer
        // obtainable without silently opening Smithing.
        return d != null && "hammer".equals(ProgressionRules.normalize(d.name))
            && (isSkillUnlocked(SkillGate.CONSTRUCTION) || isSkillUnlocked(SkillGate.SMITHING));
    }

    private boolean anyGateOpen(EnumSet<SkillGate> gates)
    {
        for (SkillGate gate : gates)
        {
            if (isSkillUnlocked(gate)) return true;
        }
        return false;
    }

    private Descriptor describe(int itemId)
    {
        int canonical = canonicalize(itemId);
        return descriptorCache.computeIfAbsent(canonical, this::buildDescriptor);
    }

    private Descriptor buildDescriptor(int canonicalId)
    {
        try
        {
            ItemComposition composition = itemManager.getItemComposition(canonicalId);
            String name = composition == null || composition.getName() == null
                ? "Item " + canonicalId : composition.getName();

            if (StarterItemCatalog.isStarterName(name))
            {
                return new Descriptor(canonicalId, name, Kind.STARTER, null, null);
            }

            // Direct quest rewards are earned by completing their quest, never by
            // spending a normal roll. Tradeable rewards stay locked until the
            // quest observer records that completion; untradeables remain self-earned.
            if (QuestRewardCatalog.isDirectQuestRewardName(name) && composition != null)
            {
                // A tradeable direct reward needs the quest observer's exact unlock
                // before acquisition. An untradeable reward is proof-of-earning by
                // ownership itself, so it remains usable once genuinely obtained.
                return new Descriptor(canonicalId, name,
                    composition.isGeTradeable() ? Kind.LOCKED_UNLISTED : Kind.EARNED_UNTRADEABLE,
                    null, null);
            }

            SkillGate catalogGate = SkillingUnlockCatalog.gateForSpecialId(canonicalId);
            if (catalogGate == null) catalogGate = SkillingUnlockCatalog.gateForName(name);
            if (catalogGate != null)
            {
                return new Descriptor(canonicalId, name, Kind.SKILL_UNLOCK, catalogGate, null);
            }

            if (ClueRewardCatalog.isGodPage(name) || ClueRewardCatalog.isGodBlessing(name) || ClueRewardCatalog.isBlessedDragonhide(name))
            {
                return new Descriptor(canonicalId, name, Kind.CLUE_BUNDLE_MEMBER, null, null);
            }
            if (ClueRewardCatalog.isMeaningfulSingle(name))
            {
                return new Descriptor(canonicalId, name, Kind.CLUE_SINGLE, null, null);
            }
            if (ClueRewardCatalog.isLikelyClueReward(name))
            {
                return new Descriptor(canonicalId, name, Kind.LOCKED_UNLISTED, null, null);
            }

            EnumSet<SkillGate> resourceGates = ProgressionRules.resourceGates(name);
            if (!resourceGates.isEmpty())
            {
                return new Descriptor(canonicalId, name, Kind.RESOURCE, null, resourceGates);
            }

            SkillGate variantGate = ProgressionRules.variantSkillGate(name);
            if (variantGate != null)
            {
                // Tradeable variants must be specifically chosen. Earned variants
                // become usable after the underlying skill has opened.
                if (composition != null && !composition.isGeTradeable())
                {
                    return new Descriptor(canonicalId, name, Kind.SKILL_VARIANT, variantGate, null);
                }
                return new Descriptor(canonicalId, name, Kind.LOCKED_UNLISTED, variantGate, null);
            }

            if (QuestFreeItemCatalog.isFreeSelfObtained(name))
            {
                return new Descriptor(canonicalId, name, Kind.QUEST_FREE_SELF_OBTAINED, null, null);
            }

            ItemStats stats = itemManager.getItemStats(canonicalId);
            if (stats != null && stats.isEquipable() && stats.getEquipment() != null)
            {
                ItemEquipmentStats equipment = stats.getEquipment();
                int price = Math.max(0, itemManager.getItemPrice(canonicalId));
                if (ProgressionRules.isUsefulPvmGear(name, equipment, price))
                {
                    // The live roll pool is built from the tradeable catalog, so
                    // meaningful untradeable gear has no exact roll path. Treat
                    // ownership of that gear as proof it was earned in-game rather
                    // than leaving it permanently unusable. Tradeable progression
                    // gear still requires an exact Three Choices unlock.
                    if (composition != null && !composition.isGeTradeable())
                    {
                        return new Descriptor(canonicalId, name, Kind.EARNED_UNTRADEABLE, null, null);
                    }
                    return new Descriptor(canonicalId, name, Kind.PVM, null, null);
                }
            }

            // Non-progression untradeables cover the large class of quest keys,
            // notes, books and other self-earned utility objects. Ownership is
            // sufficient because these items are not part of the tradeable roll pool.
            if (composition != null && !composition.isGeTradeable())
            {
                return new Descriptor(canonicalId, name, Kind.EARNED_UNTRADEABLE, null, null);
            }
        }
        catch (RuntimeException ignored)
        {
            // Strict mode fails closed if a live definition is temporarily unavailable.
        }

        return new Descriptor(canonicalId, "Item " + canonicalId, Kind.LOCKED_UNLISTED, null, null);
    }
}
