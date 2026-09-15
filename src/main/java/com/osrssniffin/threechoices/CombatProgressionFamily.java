package com.osrssniffin.threechoices;

import java.util.Locale;
import java.util.Set;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemEquipmentStats;

/**
 * Curated combat progression taxonomy for Three Choices.
 *
 * The live OSRS item database remains the source of IDs/stats/prices, but an
 * equipable item does not become progression merely because it has bonuses.
 * It must first map to a family that represents an understood account path.
 */
enum CombatProgressionFamily
{
    STANDARD_MELEE_WEAPON(ProgressionOpportunityType.MELEE_WEAPON),
    MELEE_LANDMARK_WEAPON(ProgressionOpportunityType.MELEE_WEAPON),
    STANDARD_RANGED_WEAPON(ProgressionOpportunityType.RANGED_WEAPON),
    RANGED_LANDMARK_WEAPON(ProgressionOpportunityType.RANGED_WEAPON),
    STANDARD_MAGIC_WEAPON(ProgressionOpportunityType.MAGIC_WEAPON),
    MAGIC_LANDMARK_WEAPON(ProgressionOpportunityType.MAGIC_WEAPON),
    STANDARD_MELEE_ARMOUR(ProgressionOpportunityType.DEFENSIVE_UPGRADE),
    MELEE_POWER_ARMOUR(ProgressionOpportunityType.MELEE_ARMOUR),
    RANGED_ARMOUR(ProgressionOpportunityType.RANGED_ARMOUR),
    MAGIC_ARMOUR(ProgressionOpportunityType.MAGIC_ARMOUR),
    DEFENSIVE_SHIELD(ProgressionOpportunityType.DEFENSIVE_UPGRADE),
    COMBAT_JEWELLERY(ProgressionOpportunityType.COMBAT_JEWELLERY),
    COMBAT_UTILITY(ProgressionOpportunityType.COMBAT_UTILITY);

    private static final Set<String> STANDARD_MELEE_WEAPON_MATERIALS = Set.of(
        "bronze", "iron", "steel", "black", "mithril", "adamant", "rune");
    private static final Set<String> STANDARD_MELEE_WEAPON_TERMS = Set.of(
        "dagger", "sword", "longsword", "scimitar", "mace", "warhammer",
        "battleaxe", "2h sword", "spear", "hasta", "halberd", "claws");
    private static final Set<String> STANDARD_MELEE_ARMOUR_MATERIALS = Set.of(
        "bronze", "iron", "steel", "black", "mithril", "adamant", "rune");
    private static final Set<String> STANDARD_MELEE_ARMOUR_TERMS = Set.of(
        "full helm", "med helm", "platebody", "chainbody", "platelegs",
        "plateskirt", "sq shield", "kiteshield");

    private final ProgressionOpportunityType type;

    CombatProgressionFamily(ProgressionOpportunityType type)
    {
        this.type = type;
    }

    ProgressionOpportunityType getType()
    {
        return type;
    }

    static CombatProgressionFamily classify(String rawName, ItemEquipmentStats equipment)
    {
        if (equipment == null) return null;
        String n = normalize(rawName);
        if (n.isEmpty()) return null;

        int slot = equipment.getSlot();
        if (slot == EquipmentInventorySlot.WEAPON.getSlotIdx())
        {
            return classifyWeapon(n, equipment);
        }

        if (slot == EquipmentInventorySlot.AMULET.getSlotIdx()
            || slot == EquipmentInventorySlot.RING.getSlotIdx()
            || slot == EquipmentInventorySlot.CAPE.getSlotIdx())
        {
            return classifyJewelleryOrUtility(n);
        }

        CombatProgressionFamily armour = classifyArmour(n, slot);
        if (armour != null) return armour;

        return null;
    }


    /**
     * Coarse Three Choices progression distance, deliberately separate from
     * OSRS equip requirements. Stats answer "can the account use this?"; this
     * band answers "is this a reasonable next combat progression step given
     * what the account has actually unlocked in Three Choices?"
     *
     * 0 = foundational/early, 1 = established mid progression,
     * 2 = late progression, 3 = endgame landmark.
     */
    static int progressionBand(String rawName, ItemEquipmentStats equipment)
    {
        CombatProgressionFamily family = classify(rawName, equipment);
        if (family == null) return Integer.MAX_VALUE;
        String n = normalize(rawName);

        switch (family)
        {
            case STANDARD_MELEE_WEAPON:
            case STANDARD_MELEE_ARMOUR:
            case STANDARD_RANGED_WEAPON:
            case STANDARD_MAGIC_WEAPON:
                return 0;

            case MELEE_LANDMARK_WEAPON:
                if (containsAny(n, "dragon scimitar", "zombie axe", "saradomin sword")) return 1;
                if (containsAny(n, "abyssal whip", "abyssal dagger", "abyssal bludgeon",
                    "dual macuahuitl", "godsword", "dragon hunter lance", "noxious halberd")) return 2;
                return 3;

            case RANGED_LANDMARK_WEAPON:
                if (containsAny(n, "rune crossbow", "dragon crossbow", "eclipse atlatl")) return 1;
                if (containsAny(n, "armadyl crossbow", "dragon hunter crossbow", "toxic blowpipe")) return 2;
                return 3;

            case MAGIC_LANDMARK_WEAPON:
                if (containsAny(n, "trident of the seas", "blue moon spear")) return 1;
                if (containsAny(n, "trident of the swamp", "sanguinesti staff")) return 2;
                return 3;

            case MELEE_POWER_ARMOUR:
                if (containsAny(n, "obsidian ", "dharok's ", "guthan's ", "torag's ", "verac's ")) return 1;
                if (containsAny(n, "bandos ", "inquisitor's ")) return 2;
                return 3; // Torva and future curated endgame members.

            case RANGED_ARMOUR:
                if (isLeatherProgression(n) || n.contains("green d'hide") || n.contains("snakeskin")) return 0;
                if (containsAny(n, "blue d'hide", "red d'hide", "black d'hide", "spined ", "karil's ")) return 1;
                if (n.contains("armadyl ")) return 2;
                return 3; // Masori and future curated endgame members.

            case MAGIC_ARMOUR:
                if (containsAny(n, "wizard hat", "wizard robe")) return 0;
                if (containsAny(n, "mystic ", "infinity ", "ahrim's ", "blue moon ")) return 1;
                if (containsAny(n, "ancestral ", "virtus ")) return 2;
                return 2;

            case DEFENSIVE_SHIELD:
                if (isStandardMetalShield(n)) return 0;
                if (containsAny(n, "spirit shield", "dragonfire shield", "ancient wyvern shield"))
                {
                    if (containsAny(n, "arcane spirit shield", "spectral spirit shield", "elysian spirit shield")) return 2;
                    return 1;
                }
                return 1;

            case COMBAT_JEWELLERY:
                if (containsAny(n, "amulet of strength", "amulet of power", "amulet of glory")) return 0;
                if (containsAny(n, "amulet of fury", "berserker ring", "archers ring", "seers ring",
                    "warrior ring", "brimstone ring", "ring of the gods", "treasonous ring", "tyrannical ring")) return 1;
                return 2;

            case COMBAT_UTILITY:
                return 1;

            default:
                return Integer.MAX_VALUE;
        }
    }

    private static CombatProgressionFamily classifyWeapon(String n, ItemEquipmentStats equipment)
    {
        if (isStandardMetalMeleeWeapon(n)) return STANDARD_MELEE_WEAPON;

        // Explicit melee landmarks/niches. These are progression families, not
        // a general "anything with melee stats" escape hatch.
        if (containsAny(n,
            "abyssal whip", "abyssal tentacle", "abyssal dagger", "abyssal bludgeon",
            "saradomin sword", "zombie axe", "dual macuahuitl", "godsword",
            "voidwaker", "elder maul", "dragon hunter lance", "noxious halberd",
            "soulreaper axe", "ghrazi rapier", "blade of saeldor", "inquisitor's mace",
            "osmumten's fang", "scythe of vitur", "dragon scimitar"))
        {
            return MELEE_LANDMARK_WEAPON;
        }

        if (isStandardBow(n)) return STANDARD_RANGED_WEAPON;
        if (containsAny(n,
            "rune crossbow", "dragon crossbow", "armadyl crossbow", "dragon hunter crossbow",
            "toxic blowpipe", "twisted bow", "eclipse atlatl"))
        {
            return RANGED_LANDMARK_WEAPON;
        }

        if (isElementalOrBasicCombatStaff(n)) return STANDARD_MAGIC_WEAPON;
        if (containsAny(n,
            "trident of the seas", "trident of the swamp", "sanguinesti staff",
            "tumeken's shadow", "blue moon spear"))
        {
            return MAGIC_LANDMARK_WEAPON;
        }

        return null;
    }

    private static CombatProgressionFamily classifyArmour(String n, int slot)
    {
        if (isStandardMetalArmour(n)) return STANDARD_MELEE_ARMOUR;
        if (n.startsWith("dragonstone ")) return STANDARD_MELEE_ARMOUR;

        if (containsAny(n,
            "bandos chestplate", "bandos tassets", "torva full helm", "torva platebody",
            "torva platelegs", "inquisitor's great helm", "inquisitor's hauberk",
            "inquisitor's plateskirt", "obsidian helmet", "obsidian platebody",
            "obsidian platelegs", "dharok's ", "guthan's ", "torag's ", "verac's "))
        {
            return MELEE_POWER_ARMOUR;
        }

        if (isLeatherProgression(n)
            || containsAny(n, "green d'hide", "blue d'hide", "red d'hide", "black d'hide",
                "snakeskin", "spined ", "karil's ", "armadyl helmet", "armadyl chestplate",
                "armadyl chainskirt", "masori mask", "masori body", "masori chaps"))
        {
            return RANGED_ARMOUR;
        }

        if (containsAny(n,
            "wizard hat", "wizard robe", "mystic ", "infinity ", "ahrim's ",
            "ancestral hat", "ancestral robe top", "ancestral robe bottom",
            "virtus mask", "virtus robe top", "virtus robe bottom", "blue moon "))
        {
            return MAGIC_ARMOUR;
        }

        if (slot == EquipmentInventorySlot.SHIELD.getSlotIdx()
            && (containsAny(n, "spirit shield", "dragonfire shield", "ancient wyvern shield")
                || isStandardMetalShield(n)))
        {
            return DEFENSIVE_SHIELD;
        }

        if (containsAny(n,
            "primordial boots", "pegasian boots", "eternal boots", "guardian boots",
            "granite boots", "ferocious gloves", "barrows gloves", "holy wraps"))
        {
            CombatStyle style = inferArmourStyle(n);
            if (style == CombatStyle.RANGED) return RANGED_ARMOUR;
            if (style == CombatStyle.MAGIC) return MAGIC_ARMOUR;
            return MELEE_POWER_ARMOUR;
        }

        return null;
    }

    private static CombatProgressionFamily classifyJewelleryOrUtility(String n)
    {
        if (n.equals("lightbearer")) return COMBAT_UTILITY;
        if (containsAny(n,
            "amulet of strength", "amulet of power", "amulet of glory", "amulet of fury",
            "amulet of torture", "necklace of anguish", "occult necklace", "tormented bracelet",
            "berserker ring", "archers ring", "seers ring", "warrior ring", "ring of suffering",
            "brimstone ring", "ring of the gods", "treasonous ring", "tyrannical ring"))
        {
            return COMBAT_JEWELLERY;
        }
        return null;
    }

    private static boolean isStandardMetalMeleeWeapon(String n)
    {
        String material = firstWord(n);
        if (!STANDARD_MELEE_WEAPON_MATERIALS.contains(material)) return false;
        for (String term : STANDARD_MELEE_WEAPON_TERMS)
        {
            if (n.equals(material + " " + term)) return true;
        }
        return false;
    }

    private static boolean isStandardMetalArmour(String n)
    {
        String material = firstWord(n);
        if (!STANDARD_MELEE_ARMOUR_MATERIALS.contains(material)) return false;
        for (String term : STANDARD_MELEE_ARMOUR_TERMS)
        {
            if (n.equals(material + " " + term)) return true;
        }
        return false;
    }

    private static boolean isStandardMetalShield(String n)
    {
        return isStandardMetalArmour(n) && (n.endsWith("sq shield") || n.endsWith("kiteshield"));
    }

    private static boolean isStandardBow(String n)
    {
        return n.equals("shortbow") || n.equals("longbow")
            || n.equals("oak shortbow") || n.equals("oak longbow")
            || n.equals("willow shortbow") || n.equals("willow longbow")
            || n.equals("maple shortbow") || n.equals("maple longbow")
            || n.equals("yew shortbow") || n.equals("yew longbow")
            || n.equals("magic shortbow") || n.equals("magic longbow");
    }

    private static boolean isElementalOrBasicCombatStaff(String n)
    {
        return n.equals("staff") || n.equals("magic staff")
            || n.equals("staff of air") || n.equals("staff of water")
            || n.equals("staff of earth") || n.equals("staff of fire")
            || n.equals("mystic air staff") || n.equals("mystic water staff")
            || n.equals("mystic earth staff") || n.equals("mystic fire staff");
    }

    private static boolean isLeatherProgression(String n)
    {
        // Keep the fresh-account Ranged path purposeful and narrow. Decorative
        // leather/camouflage variants are filtered before this classifier.
        return n.equals("leather body") || n.equals("leather chaps")
            || n.equals("leather vambraces") || n.equals("leather cowl");
    }

    private static CombatStyle inferArmourStyle(String n)
    {
        if (containsAny(n, "pegasian", "ranger")) return CombatStyle.RANGED;
        if (containsAny(n, "eternal", "holy wraps")) return CombatStyle.MAGIC;
        return CombatStyle.MELEE;
    }

    private static boolean containsAny(String text, String... values)
    {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private static String firstWord(String text)
    {
        int space = text.indexOf(' ');
        return space < 0 ? text : text.substring(0, space);
    }

    private static String normalize(String text)
    {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
