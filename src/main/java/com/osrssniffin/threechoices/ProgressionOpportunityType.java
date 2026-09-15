package com.osrssniffin.threechoices;

/**
 * Explicit progression directions understood by Three Choices. Items are
 * resolved only after one of these directions has been selected.
 */
public enum ProgressionOpportunityType
{
    MELEE_WEAPON(true, false, 100),
    RANGED_WEAPON(true, false, 100),
    MAGIC_WEAPON(true, false, 100),
    MELEE_ARMOUR(true, false, 82),
    RANGED_ARMOUR(true, false, 82),
    MAGIC_ARMOUR(true, false, 82),
    DEFENSIVE_UPGRADE(true, false, 68),
    COMBAT_JEWELLERY(true, false, 72),
    COMBAT_UTILITY(true, false, 62),
    SKILL_OPENING(false, true, 132),
    SKILL_METHOD(false, true, 88),
    TOOL_PROGRESSION(false, true, 96),
    SKILL_EQUIPMENT(false, true, 76),
    UTILITY_PROGRESSION(false, true, 68),
    SPECIAL(false, false, 20);

    private final boolean combat;
    private final boolean skilling;
    private final int baseWeight;

    ProgressionOpportunityType(boolean combat, boolean skilling, int baseWeight)
    {
        this.combat = combat;
        this.skilling = skilling;
        this.baseWeight = baseWeight;
    }

    public boolean isCombat()
    {
        return combat;
    }

    public boolean isSkilling()
    {
        return skilling;
    }

    public int getBaseWeight()
    {
        return baseWeight;
    }
}
