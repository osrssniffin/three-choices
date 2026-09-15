package com.osrssniffin.threechoices;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.game.ItemEquipmentStats;

/** Shared progression/resource rules used by rolling and restrictions. */
final class ProgressionRules
{
    enum ToolGroup
    {
        NONE,
        WOODCUTTING,
        MINING,
        FISHING
    }

    private static final Pattern TRIMMED_SUFFIX = Pattern.compile(".*\\((?:t|g|h[1-5]|or)\\)$");
    private static final Pattern POISONED_WEAPON_SUFFIX = Pattern.compile(".*\\((?:p|p\\+|p\\+\\+|kp)\\)$");
    private static final Set<String> LOW_VALUE_NOVELTY_WEAPONS = Set.of(
        "spork", "egg whisk", "rubber chicken", "golden hammer", "boxing gloves", "flowers");
    private static final Set<String> NON_COMBAT_EQUIPMENT_FAMILY_TERMS = Set.of(
        "larupia", "graahk", "kyatt", "camouflage", "camo", "graceful",
        "angler", "prospector", "lumberjack", "pyromancer", "farmer",
        "rogue equipment", "carpenter", "smiths'", "raiments of the eye",
        "zealot", "beekeeper");
    private static final Set<String> ALWAYS_ALLOWED_NAMES = Set.of();
    private static final Set<String> BARS = Set.of(
        "bronze bar", "iron bar", "silver bar", "steel bar", "gold bar",
        "mithril bar", "adamantite bar", "runite bar", "lovakite bar");
    private static final Set<String> RAW_FISH_TERMS = Set.of(
        "shrimp", "anchovies", "sardine", "herring", "mackerel", "trout",
        "cod", "pike", "salmon", "tuna", "lobster", "bass", "swordfish",
        "shark", "monkfish", "karambwan", "anglerfish", "dark crab",
        "manta ray", "sea turtle", "cave eel", "lava eel", "slimy eel",
        "sacred eel", "infernal eel", "rainbow fish", "cavefish", "bream");
    private static final Set<String> RAW_MEATS = Set.of(
        "raw beef", "raw chicken", "raw rat meat", "raw bear meat", "raw yak meat",
        "raw rabbit", "raw bird meat", "raw beast meat", "raw chompy", "raw jubbly",
        "raw oomlie", "raw kyatt", "raw graahk", "raw larupia", "raw pyre fox",
        "raw wild kebbit", "raw sunlight antelope", "raw moonlight antelope", "raw moss lizard");
    private static final Set<String> HUNTER_MEATS = Set.of(
        "raw rabbit", "raw bird meat", "raw beast meat", "raw kyatt", "raw graahk",
        "raw larupia", "raw pyre fox", "raw wild kebbit", "raw sunlight antelope",
        "raw moonlight antelope", "raw moss lizard");
    private static final Set<String> COOKING_INGREDIENTS = Set.of(
        "egg", "bucket of milk", "pot of flour", "flour", "cheese", "tomato",
        "potato", "onion", "cabbage", "grapes", "jug of water", "pastry dough",
        "pie shell", "pizza base", "bread dough", "cake tin", "pie dish", "bowl",
        "jug", "chocolate bar", "spice");
    private static final Set<String> HERBLORE_HERBS = Set.of(
        "guam leaf", "marrentill", "tarromin", "harralander", "ranarr weed",
        "toadflax", "irit leaf", "avantoe", "kwuarm", "snapdragon",
        "cadantine", "lantadyme", "dwarf weed", "torstol");
    private static final Set<String> HERBLORE_SECONDARIES = Set.of(
        "eye of newt", "limpwurt root", "snape grass", "red spiders' eggs",
        "white berries", "potato cactus", "amylase crystal", "crushed nest",
        "unicorn horn dust", "chocolate dust", "toad's legs", "goat horn dust",
        "wine of zamorak", "mort myre fungus", "jangerberries", "yew roots",
        "magic roots", "coconut milk");
    private static final Set<String> SLAYER_SUPPORT = Set.of(
        "rock hammer", "ice cooler", "bag of salt", "slayer bell", "fishing explosive",
        "super fishing explosive", "slayer gloves", "earmuffs", "facemask", "nose peg",
        "spiny helmet", "mirror shield", "insulated boots", "witchwood icon",
        "lit bug lantern", "unlit bug lantern");

    private ProgressionRules() {}

    static String normalize(String name)
    {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    static boolean isAlwaysAllowedByName(String name)
    {
        return ALWAYS_ALLOWED_NAMES.contains(normalize(name));
    }

    static ToolGroup toolGroup(String name)
    {
        String n = normalize(name);
        if (n.contains("pickaxe")) return ToolGroup.MINING;
        if ((n.endsWith(" axe") || n.contains(" felling axe"))
            && !n.contains("battleaxe") && !n.contains("greataxe") && !n.contains("thrownaxe"))
        {
            return ToolGroup.WOODCUTTING;
        }
        if (n.equals("small fishing net") || n.equals("big fishing net") || n.contains("fishing rod")
            || n.equals("barbarian rod") || n.equals("lobster pot") || n.endsWith("harpoon")
            || n.equals("karambwan vessel"))
        {
            return ToolGroup.FISHING;
        }
        return ToolGroup.NONE;
    }

    static SkillGate variantSkillGate(String name)
    {
        switch (toolGroup(name))
        {
            case WOODCUTTING: return SkillGate.WOODCUTTING;
            case MINING: return SkillGate.MINING;
            case FISHING: return SkillGate.FISHING;
            default: return null;
        }
    }

    /**
     * Resources are never rolled. They become legal to acquire once at least one
     * skill that meaningfully consumes/trains with that resource has been opened.
     */
    static EnumSet<SkillGate> resourceGates(String name)
    {
        String n = normalize(name);
        EnumSet<SkillGate> gates = SkillGate.none();
        if (n.isEmpty()) return gates;

        if (n.equals("logs") || n.endsWith(" logs"))
        {
            gates.add(SkillGate.WOODCUTTING);
            gates.add(SkillGate.FIREMAKING);
            gates.add(SkillGate.FLETCHING);
        }

        if (n.endsWith(" ore") || n.equals("coal") || n.equals("clay") || n.equals("soft clay")
            || n.equals("sandstone") || n.startsWith("sandstone (") || n.equals("granite")
            || n.startsWith("granite (") || n.equals("amethyst"))
        {
            gates.add(SkillGate.MINING);
            gates.add(SkillGate.SMITHING);
        }
        if (BARS.contains(n))
        {
            gates.add(SkillGate.SMITHING);
            gates.add(SkillGate.CRAFTING);
            gates.add(SkillGate.CONSTRUCTION);
        }

        if (isRawFish(n))
        {
            gates.add(SkillGate.FISHING);
            gates.add(SkillGate.COOKING);
        }
        if (RAW_MEATS.contains(n) || (n.startsWith("raw ") && (n.contains("meat") || n.contains("bird") || n.contains("rabbit"))))
        {
            gates.add(SkillGate.COOKING);
        }
        if (n.equals("fishing bait") || n.equals("dark fishing bait") || n.equals("sandworms") || n.equals("fish chunks")
            || n.equals("fish offcuts") || n.equals("fine fish offcuts"))
        {
            gates.add(SkillGate.FISHING);
        }
        if (n.equals("feather") || n.equals("feathers"))
        {
            gates.add(SkillGate.FISHING);
            gates.add(SkillGate.FLETCHING);
        }

        if (isCookedFood(n) || COOKING_INGREDIENTS.contains(n)) gates.add(SkillGate.COOKING);
        if (isHunterResource(n)) gates.add(SkillGate.HUNTER);
        if (isSlayerSupport(n)) gates.add(SkillGate.SLAYER);
        if (isPrayerResource(n)) gates.add(SkillGate.PRAYER);
        if (isRunecraftResource(n)) gates.add(SkillGate.RUNECRAFT);
        if (isHerbloreResource(n)) gates.add(SkillGate.HERBLORE);
        if (isFarmingResource(n)) gates.add(SkillGate.FARMING);
        if (isConstructionResource(n)) gates.add(SkillGate.CONSTRUCTION);
        if (isCraftingResource(n)) gates.add(SkillGate.CRAFTING);
        if (isFletchingResource(n) || isFinishedAmmo(n)) gates.add(SkillGate.FLETCHING);
        if (n.startsWith("stamina potion")) gates.add(SkillGate.AGILITY);

        // Once a skill is open, ordinary supporting tools should not consume a
        // second choice just to make the skill functional.
        if (n.equals("spade") || n.equals("rake") || n.equals("watering can") || n.startsWith("watering can("))
            gates.add(SkillGate.FARMING);
        if (n.equals("hammer") || n.equals("bolt of cloth") || n.endsWith(" nails"))
            gates.add(SkillGate.CONSTRUCTION);
        if (n.equals("needle") || n.equals("thread") || n.equals("glassblowing pipe"))
            gates.add(SkillGate.CRAFTING);

        return gates;
    }

    private static boolean isRawFish(String n)
    {
        if (!n.startsWith("raw ")) return false;
        String fish = n.substring(4);
        for (String term : RAW_FISH_TERMS)
        {
            if (fish.equals(term) || fish.startsWith(term + " ") || fish.endsWith(" " + term)) return true;
        }
        return false;
    }

    private static boolean isCookedFood(String n)
    {
        if (n.startsWith("raw ")) return false;
        if (n.startsWith("cooked ") || n.equals("bread") || n.equals("jug of wine")
            || n.equals("stew") || n.equals("curry") || n.endsWith(" stew") || n.endsWith(" curry")
            || n.equals("kebab") || n.endsWith(" kebab")
            || n.equals("cake") || n.endsWith(" cake") || n.contains(" cake slice")
            || n.equals("plain pizza") || n.endsWith(" pizza") || n.contains(" pizza ")
            || n.endsWith(" pie") || n.contains(" pie (half)") || n.startsWith("half a ") && n.endsWith(" pie")
            || n.startsWith("potato with ") || n.endsWith(" potato"))
        {
            return true;
        }
        for (String term : RAW_FISH_TERMS)
        {
            if (n.equals(term) || n.startsWith(term + " ") || n.endsWith(" " + term)) return true;
        }
        return n.equals("karambwan") || n.equals("cooked karambwan");
    }

    static boolean isCookedFoodName(String name)
    {
        return isCookedFood(normalize(name));
    }

    private static boolean isHunterResource(String n)
    {
        return n.contains("chinchompa") || n.endsWith(" fur") || HUNTER_MEATS.contains(n);
    }

    private static boolean isSlayerSupport(String n)
    {
        return SLAYER_SUPPORT.contains(n) || n.startsWith("fungicide spray");
    }

    private static boolean isPrayerResource(String n)
    {
        return n.equals("bones") || n.endsWith(" bones") || n.contains("dragon bones")
            || n.endsWith(" ashes") || n.equals("ashes") || n.startsWith("ensouled ")
            || n.contains("bone shards");
    }

    private static boolean isRunecraftResource(String n)
    {
        return n.equals("rune essence") || n.equals("pure essence") || n.endsWith(" talisman")
            || n.endsWith(" tiara") || n.endsWith(" rune") || n.endsWith(" runes")
            || n.equals("binding necklace");
    }

    private static boolean isHerbloreResource(String n)
    {
        return n.startsWith("grimy ") || HERBLORE_HERBS.contains(n) || HERBLORE_SECONDARIES.contains(n)
            || n.equals("vial") || n.equals("vial of water")
            || n.contains(" potion(") || n.contains(" potion (")
            || n.endsWith(" potion (unf)") || n.endsWith(" potion(unf)");
    }

    private static boolean isFarmingResource(String n)
    {
        return n.endsWith(" seed") || n.equals("compost") || n.equals("supercompost") || n.equals("ultracompost")
            || n.equals("plant pot") || n.equals("plant cure") || n.equals("basket") || n.equals("sack");
    }

    private static boolean isConstructionResource(String n)
    {
        return n.endsWith(" plank") || n.endsWith(" planks") || n.endsWith(" nails")
            || n.equals("bolt of cloth") || n.equals("limestone brick") || n.equals("marble block")
            || n.equals("magic stone");
    }

    private static boolean isCraftingResource(String n)
    {
        return n.startsWith("uncut ") || n.equals("molten glass") || n.equals("soda ash") || n.equals("bucket of sand")
            || n.equals("leather") || n.endsWith(" leather") || n.endsWith(" hide") || n.endsWith(" hides")
            || n.equals("flax") || n.equals("ball of wool") || n.equals("wool") || n.equals("thread");
    }

    private static boolean isFletchingResource(String n)
    {
        return n.equals("bow string") || n.equals("arrow shaft") || n.equals("arrow shafts")
            || n.equals("headless arrow") || n.equals("headless arrows") || n.endsWith(" arrowheads")
            || n.endsWith(" dart tip") || n.endsWith(" dart tips") || n.endsWith(" bolts (unf)")
            || n.endsWith(" bolt tips");
    }

    private static boolean isFinishedAmmo(String n)
    {
        return n.endsWith(" arrow") || n.endsWith(" arrows") || n.endsWith(" bolt") || n.endsWith(" bolts")
            || n.endsWith(" dart") || n.endsWith(" darts") || n.endsWith(" javelin") || n.endsWith(" javelins");
    }
    static boolean isCosmeticVariant(String name)
    {
        String n = normalize(name);
        return TRIMMED_SUFFIX.matcher(n).matches()
            || n.contains("ornament")
            || n.contains("gold-trimmed")
            || n.contains("gilded")
            || n.contains("elegant")
            || n.contains("heraldic");
    }

    static boolean isUsefulPvmGear(String name, ItemEquipmentStats equipment, int price)
    {
        String n = normalize(name);
        CombatProgressionFamily family = CombatProgressionFamily.classify(n, equipment);
        if (equipment == null || family == null
            || isCosmeticVariant(n) || isRedundantCombatVariant(n) || isLowValueNoveltyWeapon(n))
        {
            return false;
        }

        int slot = equipment.getSlot();
        int meleeAttack = Math.max(0, Math.max(equipment.getAstab(), Math.max(equipment.getAslash(), equipment.getAcrush())));
        int rangedAttack = Math.max(0, equipment.getArange());
        int magicAttack = Math.max(0, equipment.getAmagic());
        int attackSignal = Math.max(meleeAttack, Math.max(rangedAttack, magicAttack));

        int defenceSignal = Math.max(0, equipment.getDstab())
            + Math.max(0, equipment.getDslash())
            + Math.max(0, equipment.getDcrush())
            + Math.max(0, equipment.getDmagic())
            + Math.max(0, equipment.getDrange());

        int offensivePowerSignal = Math.max(0, equipment.getStr())
            + Math.max(0, equipment.getRstr())
            + Math.max(0, Math.round(equipment.getMdmg() * 2.0f));
        int prayerSignal = Math.max(0, equipment.getPrayer());
        int powerSignal = offensivePowerSignal + prayerSignal;

        if (slot == EquipmentInventorySlot.WEAPON.getSlotIdx())
        {
            // Family membership already establishes a real progression purpose.
            // Keep only a tiny stat floor so early bronze/bow/staff progression
            // is not accidentally erased by an endgame-oriented score threshold.
            return attackSignal + powerSignal >= 2;
        }

        boolean accessory = slot == EquipmentInventorySlot.AMULET.getSlotIdx()
            || slot == EquipmentInventorySlot.RING.getSlotIdx()
            || slot == EquipmentInventorySlot.CAPE.getSlotIdx();

        if (n.equals("lightbearer"))
        {
            return true;
        }
        if (accessory)
        {
            int offensiveSignal = attackSignal + offensivePowerSignal;
            if (offensiveSignal == 0)
            {
                // Price is deliberately not evidence of progression. Pure
                // utility jewellery must justify itself through real combat
                // stats, a known passive, or curated clue significance.
                boolean meaningfulUtility = ClueRewardCatalog.isMeaningfulSingle(n)
                    || prayerSignal >= 6 || defenceSignal >= 55;
                if (!meaningfulUtility) return false;
            }
            return offensiveSignal + prayerSignal + (defenceSignal / 5) >= 6;
        }

        int meaningfulSignal = attackSignal + powerSignal + (defenceSignal / 4);
        if (family == CombatProgressionFamily.STANDARD_MELEE_ARMOUR
            || family == CombatProgressionFamily.RANGED_ARMOUR
            || family == CombatProgressionFamily.MAGIC_ARMOUR
            || family == CombatProgressionFamily.DEFENSIVE_SHIELD)
        {
            return attackSignal + powerSignal + defenceSignal > 0;
        }
        return meaningfulSignal >= 6;
    }

    /**
     * Classifies an equipable item by its actual Three Choices progression
     * purpose. Defensive bonuses alone are not enough to make skilling, Hunter,
     * camouflage or cosmetic equipment generic PvM progression.
     */
    static ProgressionOpportunityType progressionOpportunityType(String name, ItemEquipmentStats equipment)
    {
        if (equipment == null) return null;
        String n = normalize(name);
        if (n.isEmpty() || isNonCombatEquipmentPurpose(n) || toolGroup(n) != ToolGroup.NONE) return null;

        int slot = equipment.getSlot();
        if (slot == EquipmentInventorySlot.AMMO.getSlotIdx()
            || slot == EquipmentInventorySlot.ARMS.getSlotIdx()
            || slot == EquipmentInventorySlot.HAIR.getSlotIdx()
            || slot == EquipmentInventorySlot.JAW.getSlotIdx())
        {
            return null;
        }

        CombatProgressionFamily family = CombatProgressionFamily.classify(n, equipment);
        return family == null ? null : family.getType();
    }

    static String combatProgressionFamilyKey(String name, ItemEquipmentStats equipment)
    {
        CombatProgressionFamily family = CombatProgressionFamily.classify(name, equipment);
        return family == null ? null : family.name();
    }

    static int progressionVariantPenalty(String name)
    {
        String n = normalize(name);
        // Dragonstone armour is a stat-equivalent decorative rune family. It can
        // still appear, but it should not dominate ordinary progression because
        // of its GE price. General ornamented variants are filtered earlier.
        if (n.startsWith("dragonstone ")) return 14;
        if (isCosmeticVariant(n)) return 20;
        return 0;
    }

    private static boolean isNonCombatEquipmentPurpose(String normalizedName)
    {
        for (String family : NON_COMBAT_EQUIPMENT_FAMILY_TERMS)
        {
            if (normalizedName.contains(family)) return true;
        }
        return false;
    }

    /** Poisoned duplicates are states of an existing weapon, not progression rolls. */
    private static boolean isRedundantCombatVariant(String normalizedName)
    {
        if (!POISONED_WEAPON_SUFFIX.matcher(normalizedName).matches()) return false;
        return normalizedName.contains("spear(")
            || normalizedName.contains("hasta(")
            || normalizedName.contains("dagger(")
            || normalizedName.contains("dart(")
            || normalizedName.contains("javelin(")
            || normalizedName.contains("knife(")
            || normalizedName.contains("arrow(")
            || normalizedName.contains("bolt(");
    }

    /** Tiny curated guard for novelty weapons that pass raw stat thresholds. */
    private static boolean isLowValueNoveltyWeapon(String normalizedName)
    {
        return LOW_VALUE_NOVELTY_WEAPONS.contains(normalizedName);
    }

    static boolean isStarterPvmGear(ItemEquipmentStats equipment, CombatStyle style, int price)
    {
        return false;
    }

    static CombatStyle classifyStyle(ItemEquipmentStats equipment)
    {
        int meleeAttack = Math.max(equipment.getAstab(), Math.max(equipment.getAslash(), equipment.getAcrush()));
        int meleeSignal = Math.max(0, meleeAttack) + Math.max(0, equipment.getStr());
        int rangedSignal = Math.max(0, equipment.getArange()) + Math.max(0, equipment.getRstr());
        int magicSignal = Math.max(0, equipment.getAmagic()) + Math.max(0, Math.round(equipment.getMdmg() * 2.0f));

        int best = Math.max(meleeSignal, Math.max(rangedSignal, magicSignal));
        if (best <= 2)
        {
            return CombatStyle.GENERAL;
        }
        if (rangedSignal == best)
        {
            return CombatStyle.RANGED;
        }
        if (magicSignal == best)
        {
            return CombatStyle.MAGIC;
        }
        return CombatStyle.MELEE;
    }

    static int progressionScore(ItemEquipmentStats equipment, CombatStyle style, int price)
    {
        int statScore = statProgressionScore(equipment, style);
        int valueScore = valueProgressionScore(price);

        // Account levels and actual equipment strength drive eligibility. GE value
        // is only a small quality nudge so expensive clue/accessory pieces do not
        // masquerade as level-80+ gear when their real requirements are much lower.
        int valueNudge = (int) Math.round((statScore * 0.95d) + (valueScore * 0.05d));
        int cappedNudge = Math.min(statScore + 3, valueNudge);
        return clamp(1, 99, Math.max(statScore, cappedNudge));
    }

    static int statProgressionScore(ItemEquipmentStats equipment, CombatStyle style)
    {
        int slot = equipment.getSlot();
        int meleeAttack = Math.max(0, Math.max(equipment.getAstab(), Math.max(equipment.getAslash(), equipment.getAcrush())));
        int rangedAttack = Math.max(0, equipment.getArange());
        int magicAttack = Math.max(0, equipment.getAmagic());
        int meleeStrength = Math.max(0, equipment.getStr());
        int rangedStrength = Math.max(0, equipment.getRstr());
        int magicDamage = Math.max(0, Math.round(equipment.getMdmg()));

        if (slot == EquipmentInventorySlot.WEAPON.getSlotIdx())
        {
            double score;
            switch (style)
            {
                case RANGED:
                    score = 17.0d + (rangedAttack * 0.49d) + (rangedStrength * 0.35d);
                    break;
                case MAGIC:
                    score = 12.0d + (magicAttack * 0.55d) + (magicDamage * 1.15d);
                    break;
                case MELEE:
                    score = 1.0d + ((meleeAttack + meleeStrength) * 0.44d);
                    break;
                case GENERAL:
                default:
                    score = 10.0d;
                    break;
            }
            return clamp(1, 99, (int) Math.round(score));
        }

        int defenceSum = Math.max(0, equipment.getDstab())
            + Math.max(0, equipment.getDslash())
            + Math.max(0, equipment.getDcrush())
            + Math.max(0, equipment.getDmagic())
            + Math.max(0, equipment.getDrange());
        int bestAttack = Math.max(meleeAttack, Math.max(rangedAttack, magicAttack));
        int bestStrength = Math.max(meleeStrength, rangedStrength);

        double armourScore = 5.0d
            + (defenceSum * 0.145d)
            + (bestAttack * 0.18d)
            + (bestStrength * 0.16d)
            + (magicDamage * 0.8d)
            + (Math.max(0, equipment.getPrayer()) * 0.65d);
        return clamp(1, 99, (int) Math.round(armourScore));
    }

    static int valueProgressionScore(int price)
    {
        if (price <= 0)
        {
            return 1;
        }
        double score = 7.0d + 17.0d * Math.log10((price / 1000.0d) + 1.0d);
        return clamp(1, 99, (int) Math.round(score));
    }

    private static int clamp(int min, int max, int value)
    {
        return Math.max(min, Math.min(max, value));
    }
}
