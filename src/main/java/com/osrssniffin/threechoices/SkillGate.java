package com.osrssniffin.threechoices;

import java.util.EnumSet;
import net.runelite.api.Skill;

/**
 * Non-combat skills controlled by Three Choices. Combat training remains open;
 * Prayer and Slayer are progression skills in this mode and therefore gated.
 */
public enum SkillGate
{
    PRAYER(Skill.PRAYER, "Prayer"),
    COOKING(Skill.COOKING, "Cooking"),
    WOODCUTTING(Skill.WOODCUTTING, "Woodcutting"),
    FLETCHING(Skill.FLETCHING, "Fletching"),
    FISHING(Skill.FISHING, "Fishing"),
    FIREMAKING(Skill.FIREMAKING, "Firemaking"),
    CRAFTING(Skill.CRAFTING, "Crafting"),
    SMITHING(Skill.SMITHING, "Smithing"),
    MINING(Skill.MINING, "Mining"),
    HERBLORE(Skill.HERBLORE, "Herblore"),
    AGILITY(Skill.AGILITY, "Agility"),
    THIEVING(Skill.THIEVING, "Thieving"),
    SLAYER(Skill.SLAYER, "Slayer"),
    FARMING(Skill.FARMING, "Farming"),
    RUNECRAFT(Skill.RUNECRAFT, "Runecraft"),
    HUNTER(Skill.HUNTER, "Hunter"),
    CONSTRUCTION(Skill.CONSTRUCTION, "Construction"),
    SAILING(Skill.SAILING, "Sailing");

    private final Skill skill;
    private final String displayName;

    SkillGate(Skill skill, String displayName)
    {
        this.skill = skill;
        this.displayName = displayName;
    }

    public Skill getSkill()
    {
        return skill;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public static SkillGate fromSkill(Skill skill)
    {
        if (skill == null)
        {
            return null;
        }
        for (SkillGate gate : values())
        {
            if (gate.skill == skill)
            {
                return gate;
            }
        }
        return null;
    }

    public static EnumSet<SkillGate> none()
    {
        return EnumSet.noneOf(SkillGate.class);
    }

    /** Skills intentionally open at the start of Three Choices. */
    public static EnumSet<SkillGate> starterOpenSkills()
    {
        return EnumSet.of(WOODCUTTING, FIREMAKING);
    }
}
