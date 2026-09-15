package com.osrssniffin.threechoices;

import java.util.Locale;
import net.runelite.api.Quest;

/**
 * Quest prerequisites for tradeable combat progression items whose wield/wear
 * access is not represented by RuneLite's ItemEquipmentStats.
 *
 * Keep this list narrow and explicit. Unknown quest requirements must not be
 * guessed; families with unresolved prerequisites should not be admitted by
 * CombatProgressionFamily until their rule is known.
 */
final class CombatQuestRequirementRules
{
    private CombatQuestRequirementRules() {}

    static Quest requiredQuest(String itemName)
    {
        String n = normalize(itemName);
        if (n.equals("dragon scimitar")) return Quest.MONKEY_MADNESS_I;
        if (n.equals("rune platebody") || n.equals("green d'hide body") || n.equals("dragonstone platebody"))
        {
            return Quest.DRAGON_SLAYER_I;
        }
        return null;
    }

    private static String normalize(String text)
    {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
