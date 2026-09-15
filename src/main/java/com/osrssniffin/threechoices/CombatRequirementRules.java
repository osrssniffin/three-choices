package com.osrssniffin.threechoices;

import java.util.Locale;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemEquipmentStats;

/**
 * Conservative requirement guard used before a PvM item can enter a roll.
 * RuneLite's live equipment stats do not expose every OSRS equip requirement,
 * so this combines material families with a compact set of important named
 * progression items. It deliberately rejects doubtful over-level suggestions.
 */
final class CombatRequirementRules
{
    private CombatRequirementRules() {}

    static boolean canUse(CombatProfile profile, String itemName, ItemEquipmentStats equipment)
    {
        if (profile == null || !profile.isReady() || equipment == null) return false;
        String n = normalize(itemName);

        int attack = 1;
        int strength = 1;
        int defence = 1;
        int ranged = 1;
        int magic = 1;
        int prayer = 1;
        int hitpoints = 1;

        // Explicit high-value progression landmarks first.
        if (containsAny(n, "abyssal whip", "abyssal dagger", "saradomin sword")) attack = Math.max(attack, 70);
        if (n.contains("abyssal tentacle")) attack = Math.max(attack, 75);
        if (n.contains("abyssal bludgeon")) { attack = Math.max(attack, 70); strength = Math.max(strength, 70); }
        if (n.contains("zombie axe")) attack = Math.max(attack, 65);
        if (n.contains("dual macuahuitl")) { attack = Math.max(attack, 70); strength = Math.max(strength, 75); }
        if (n.contains("godsword") || n.contains("voidwaker")) attack = Math.max(attack, 75);
        if (n.contains("elder maul")) { attack = Math.max(attack, 75); strength = Math.max(strength, 75); }
        if (n.contains("dragon hunter lance")) attack = Math.max(attack, 78);
        if (n.contains("noxious halberd")) attack = Math.max(attack, 80);
        if (n.contains("soulreaper axe")) { attack = Math.max(attack, 80); strength = Math.max(strength, 80); }
        if (containsAny(n, "ghrazi rapier", "blade of saeldor", "inquisitor's mace")) attack = Math.max(attack, 80);
        if (n.contains("osmumten's fang")) attack = Math.max(attack, 82);
        if (n.contains("scythe of vitur")) { attack = Math.max(attack, 80); strength = Math.max(strength, 90); }

        if (containsAny(n, "toxic blowpipe")) ranged = Math.max(ranged, 75);
        if (containsAny(n, "rune crossbow")) ranged = Math.max(ranged, 61);
        if (containsAny(n, "dragon crossbow")) ranged = Math.max(ranged, 64);
        if (containsAny(n, "armadyl crossbow", "dragon hunter crossbow")) ranged = Math.max(ranged, 70);
        if (containsAny(n, "bow of faerdhinen", "crystal bow")) ranged = Math.max(ranged, 70);
        if (n.contains("twisted bow")) ranged = Math.max(ranged, 85);

        // Standard bow family. RuneLite's equipment stat object contains combat
        // bonuses/attack speed but not these wield levels.
        if (n.equals("oak shortbow") || n.equals("oak longbow")) ranged = Math.max(ranged, 5);
        if (n.equals("willow shortbow") || n.equals("willow longbow")) ranged = Math.max(ranged, 20);
        if (n.equals("maple shortbow") || n.equals("maple longbow")) ranged = Math.max(ranged, 30);
        if (n.equals("yew shortbow") || n.equals("yew longbow")) ranged = Math.max(ranged, 40);
        if (n.equals("magic shortbow") || n.equals("magic longbow")) ranged = Math.max(ranged, 50);

        if (n.contains("trident of the seas")) magic = Math.max(magic, 75);
        if (n.contains("trident of the swamp")) magic = Math.max(magic, 78);
        if (n.contains("blue moon spear")) { attack = Math.max(attack, 70); magic = Math.max(magic, 75); }
        if (n.contains("sanguinesti staff")) magic = Math.max(magic, 82);
        if (n.contains("tumeken's shadow")) magic = Math.max(magic, 85);
        if (n.contains("occult necklace")) magic = Math.max(magic, 70);
        if (n.startsWith("mystic ") && n.endsWith(" staff")) magic = Math.max(magic, 40);

        // High-value accessories with requirements which are not represented by
        // equipment bonuses alone.
        if (containsAny(n, "necklace of anguish", "amulet of torture", "tormented bracelet", "ring of suffering"))
        {
            hitpoints = Math.max(hitpoints, 75);
        }
        if (n.contains("primordial boots")) { strength = Math.max(strength, 75); defence = Math.max(defence, 75); }
        if (n.contains("pegasian boots")) { ranged = Math.max(ranged, 75); defence = Math.max(defence, 75); }
        if (n.contains("eternal boots")) { magic = Math.max(magic, 75); defence = Math.max(defence, 75); }
        if (n.contains("ferocious gloves")) { attack = Math.max(attack, 80); defence = Math.max(defence, 80); }
        if (n.contains("guardian boots")) defence = Math.max(defence, 75);
        if (n.contains("granite boots")) { strength = Math.max(strength, 50); defence = Math.max(defence, 50); }
        if (n.contains("holy wraps")) prayer = Math.max(prayer, 31);

        // Spirit shields are a concrete example of why missing metadata must
        // never mean "no requirement". RuneLite equipment stats do not encode
        // these wield requirements, so enforce the family explicitly.
        if (n.equals("spirit shield")) { defence = Math.max(defence, 45); prayer = Math.max(prayer, 55); }
        if (n.equals("blessed spirit shield")) { defence = Math.max(defence, 70); prayer = Math.max(prayer, 60); }
        if (n.equals("arcane spirit shield") || n.equals("spectral spirit shield"))
        {
            defence = Math.max(defence, 75);
            prayer = Math.max(prayer, 70);
            magic = Math.max(magic, 65);
        }
        if (n.equals("elysian spirit shield"))
        {
            defence = Math.max(defence, 75);
            prayer = Math.max(prayer, 75);
        }

        if (n.startsWith("dragonstone ")) defence = Math.max(defence, 40);

        if (n.contains("torva ")) defence = Math.max(defence, 80);
        if (n.contains("bandos ")) defence = Math.max(defence, 65);
        // Inquisitor armour is intentionally low-Defence but is not early-game
        // equipment: each armour piece requires 30 Defence and 70 Strength.
        // RuneLite equipment stats do not encode that hybrid wield requirement.
        if (n.equals("inquisitor's great helm")
            || n.equals("inquisitor's hauberk")
            || n.equals("inquisitor's plateskirt"))
        {
            defence = Math.max(defence, 30);
            strength = Math.max(strength, 70);
        }
        if (n.contains("barrows") || isBarrowsPiece(n)) defence = Math.max(defence, 70);
        if (n.contains("obsidian ")) defence = Math.max(defence, 60);

        // Magic armour families with explicit level gates.
        if (n.contains("mystic ") || n.startsWith("mystic")) { magic = Math.max(magic, 40); defence = Math.max(defence, 20); }
        if (n.contains("infinity ") || n.startsWith("infinity")) { magic = Math.max(magic, 50); defence = Math.max(defence, 25); }
        if (n.contains("ancestral ")) { magic = Math.max(magic, 75); defence = Math.max(defence, 65); }
        if (n.contains("virtus ")) { magic = Math.max(magic, 78); defence = Math.max(defence, 75); }
        if (n.contains("ahrim")) { magic = Math.max(magic, 70); defence = Math.max(defence, 70); }
        if (n.contains("blue moon ") && !n.contains("blue moon spear")) { magic = Math.max(magic, 75); defence = Math.max(defence, 50); }

        // Moons of Peril equipment uses hybrid requirements which equipment stats alone cannot infer reliably.
        if (n.contains("blood moon ")) { strength = Math.max(strength, 75); defence = Math.max(defence, 50); }
        if (n.contains("eclipse moon ")) { ranged = Math.max(ranged, 75); defence = Math.max(defence, 50); }
        if (n.contains("eclipse atlatl"))
        {
            ranged = Math.max(ranged, 75);
            attack = Math.max(attack, 50);
            strength = Math.max(strength, 50);
        }

        if (n.contains("armadyl ") && !ClueRewardCatalog.isBlessedDragonhide(n)) { ranged = Math.max(ranged, 70); defence = Math.max(defence, 70); }
        if (n.contains("masori"))
        {
            ranged = Math.max(ranged, 80);
            defence = Math.max(defence, n.contains("(f)") || n.contains("fortified") ? 80 : 30);
        }

        // Ranged armour families.
        if (n.contains("snakeskin")) { ranged = Math.max(ranged, 30); defence = Math.max(defence, 30); }
        if (n.contains("spined ")) { ranged = Math.max(ranged, 40); defence = Math.max(defence, 40); }
        if (n.contains("green d'hide")) ranged = Math.max(ranged, 40);
        if (n.contains("blue d'hide")) ranged = Math.max(ranged, 50);
        if (n.contains("red d'hide")) ranged = Math.max(ranged, 60);
        if (n.contains("black d'hide") || ClueRewardCatalog.isBlessedDragonhide(n)) ranged = Math.max(ranged, 70);
        if ((containsAny(n, "green d'hide", "blue d'hide", "red d'hide", "black d'hide"))
            && (n.contains("body") || n.contains("shield")))
        {
            defence = Math.max(defence, 40);
        }
        if (ClueRewardCatalog.isBlessedDragonhide(n)
            && (n.contains("body") || n.contains("coif") || n.contains("boots") || n.contains("shield")))
        {
            defence = Math.max(defence, 40);
        }
        if (containsAny(n, "ranger boots", "robin hood hat", "rangers' tunic", "rangers' tights", "ranger gloves")) ranged = Math.max(ranged, 40);

        // Broad metal/material bands. Apply to the stat style/slot, not just price.
        int material = materialLevel(n);
        CombatStyle style = ProgressionRules.classifyStyle(equipment);
        int slot = equipment.getSlot();
        if (material > 1)
        {
            if (slot == EquipmentInventorySlot.WEAPON.getSlotIdx())
            {
                if (style == CombatStyle.RANGED) ranged = Math.max(ranged, material);
                else if (style == CombatStyle.MAGIC) magic = Math.max(magic, material);
                else attack = Math.max(attack, material);
            }
            else if (slot == EquipmentInventorySlot.HEAD.getSlotIdx()
                || slot == EquipmentInventorySlot.BODY.getSlotIdx()
                || slot == EquipmentInventorySlot.SHIELD.getSlotIdx()
                || slot == EquipmentInventorySlot.LEGS.getSlotIdx()
                || slot == EquipmentInventorySlot.BOOTS.getSlotIdx())
            {
                defence = Math.max(defence, material);
            }
        }

        return profile.getAttack() >= attack
            && profile.getStrength() >= strength
            && profile.getDefence() >= defence
            && profile.getRanged() >= ranged
            && profile.getMagic() >= magic
            && profile.getPrayer() >= prayer
            && profile.getHitpoints() >= hitpoints;
    }

    static int materialLevel(String rawName)
    {
        String n = normalize(rawName);
        if (n.startsWith("dragon ") || n.contains(" dragon ")) return 60;
        if (n.startsWith("granite ")) return 50;
        if (n.startsWith("rune ") || n.contains(" rune ")) return 40;
        if (n.startsWith("adamant ") || n.startsWith("adamantite ")) return 30;
        if (n.startsWith("mithril ")) return 20;
        if (n.startsWith("black ")) return 10;
        if (n.startsWith("steel ")) return 5;
        return 1;
    }

    private static boolean isBarrowsPiece(String n)
    {
        return n.startsWith("ahrim's ") || n.startsWith("dharok's ") || n.startsWith("guthan's ")
            || n.startsWith("karil's ") || n.startsWith("torag's ") || n.startsWith("verac's ");
    }

    private static boolean containsAny(String text, String... values)
    {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private static String normalize(String text)
    {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
