package com.osrssniffin.threechoices;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

/**
 * Small, curated skilling progression catalog. It deliberately contains only
 * skill-opening items and genuinely useful tool/utility upgrades; resources are
 * handled separately and never consume a choice.
 */
@Singleton
public class SkillingUnlockCatalog
{
    static final int COOKING_GAUNTLETS = 775;
    static final int GOLDSMITH_GAUNTLETS = 776;
    static final int CAPTAINS_LOG_AFTER_QUEST = 31986;

    static final class Spec
    {
        final SkillGate gate;
        final String name;
        final int targetLevel;
        final boolean entryKey;
        final int fixedItemId;

        Spec(SkillGate gate, String name, int targetLevel, boolean entryKey)
        {
            this(gate, name, targetLevel, entryKey, -1);
        }

        Spec(SkillGate gate, String name, int targetLevel, boolean entryKey, int fixedItemId)
        {
            this.gate = gate;
            this.name = name;
            this.targetLevel = targetLevel;
            this.entryKey = entryKey;
            this.fixedItemId = fixedItemId;
        }
    }

    private static Spec s(SkillGate gate, String name, int level, boolean entry)
    {
        return new Spec(gate, name, level, entry);
    }

    private static final List<Spec> SPECS = List.of(
        // Prayer is the deliberate resource exception: Bones is the key that
        // opens Prayer, regardless of the account's current Prayer level.
        s(SkillGate.PRAYER, "Bones", 1, true),
        s(SkillGate.PRAYER, "Dragonbone necklace", 70, false),

        // Skills that require/revolve around a reusable tool or meaningful
        // utility item. Entry keys stay available until the skill is opened.
        s(SkillGate.RUNECRAFT, "Air talisman", 1, true),
        s(SkillGate.RUNECRAFT, "Binding necklace", 23, false),
        s(SkillGate.RUNECRAFT, "Ring of the elements", 50, false),

        s(SkillGate.CONSTRUCTION, "Saw", 1, true),

        // Agility has no mandatory held tool; stamina is the intentional
        // permission key and Ring of endurance is the later meaningful upgrade.
        s(SkillGate.AGILITY, "Stamina potion(4)", 1, true),
        s(SkillGate.AGILITY, "Ring of endurance", 70, false),

        // Herblore opens through a complete level-matched potion recipe in
        // TrainingMethodCatalog, not through an arbitrary utility object.
        s(SkillGate.HERBLORE, "Amulet of chemistry", 20, false),
        s(SkillGate.HERBLORE, "Alchemist's amulet", 70, false),

        s(SkillGate.THIEVING, "Dodgy necklace", 1, true),
        s(SkillGate.THIEVING, "Gloves of silence", 54, false),

        s(SkillGate.CRAFTING, "Chisel", 1, true),
        s(SkillGate.CRAFTING, "Glassblowing pipe", 33, false),

        s(SkillGate.FLETCHING, "Knife", 1, true),
        s(SkillGate.FLETCHING, "Fletching knife", 10, false),

        s(SkillGate.SLAYER, "Enchanted gem", 1, true),
        s(SkillGate.SLAYER, "Expeditious bracelet", 1, false),
        s(SkillGate.SLAYER, "Bracelet of slaughter", 40, false),

        // Hunter methods are genuinely tool-specific; Bird snare is the actual
        // level-1 entry route, with later methods entering near their levels.
        s(SkillGate.HUNTER, "Bird snare", 1, true),
        s(SkillGate.HUNTER, "Noose wand", 9, false),
        s(SkillGate.HUNTER, "Butterfly net", 25, false),
        s(SkillGate.HUNTER, "Box trap", 39, false),

        // Smithing opens through a level-matched bar in TrainingMethodCatalog.
        // Cooking opens through a raw food the account can actually cook.
        // Family Crest gauntlets are quest-derived and never consume a roll.

        // Firemaking is already open from Tutorial Island's tinderbox. Keeping
        // the baseline item in the catalog lets shared classification stay simple;
        // it is never offered because the starter skill gate is already open.
        s(SkillGate.FIREMAKING, "Tinderbox", 1, true),

        s(SkillGate.FARMING, "Seed dibber", 1, true),
        s(SkillGate.FARMING, "Bottomless compost bucket", 50, false),

        // Captain's log is the intentional level-1 Sailing entry key. Keep the
        // fixed post-quest item ID so it remains resolvable even though it is not
        // a normal tradeable catalog item. Choosing it opens the Sailing gate.
        new Spec(SkillGate.SAILING, "Captain's log", 1, true, CAPTAINS_LOG_AFTER_QUEST),

        // Woodcutting: normal axes below rune, then useful felling/dragon options.
        s(SkillGate.WOODCUTTING, "Bronze axe", 1, true),
        s(SkillGate.WOODCUTTING, "Iron axe", 1, false),
        s(SkillGate.WOODCUTTING, "Steel axe", 6, false),
        s(SkillGate.WOODCUTTING, "Black axe", 11, false),
        s(SkillGate.WOODCUTTING, "Mithril axe", 21, false),
        s(SkillGate.WOODCUTTING, "Adamant axe", 31, false),
        s(SkillGate.WOODCUTTING, "Rune axe", 41, false),
        s(SkillGate.WOODCUTTING, "Rune felling axe", 41, false),
        s(SkillGate.WOODCUTTING, "Dragon axe", 61, false),
        s(SkillGate.WOODCUTTING, "Dragon felling axe", 61, false),

        // Mining: use the best real pickaxe near the player's Mining level.
        s(SkillGate.MINING, "Bronze pickaxe", 1, true),
        s(SkillGate.MINING, "Iron pickaxe", 1, false),
        s(SkillGate.MINING, "Steel pickaxe", 6, false),
        s(SkillGate.MINING, "Black pickaxe", 11, false),
        s(SkillGate.MINING, "Mithril pickaxe", 21, false),
        s(SkillGate.MINING, "Adamant pickaxe", 31, false),
        s(SkillGate.MINING, "Rune pickaxe", 41, false),
        s(SkillGate.MINING, "Dragon pickaxe", 61, false),

        // Fishing has multiple legitimate method-specific tools.
        s(SkillGate.FISHING, "Small fishing net", 1, true),
        s(SkillGate.FISHING, "Fishing rod", 5, true),
        s(SkillGate.FISHING, "Fly fishing rod", 20, false),
        s(SkillGate.FISHING, "Harpoon", 35, false),
        s(SkillGate.FISHING, "Dragon harpoon", 61, false),
        s(SkillGate.FISHING, "Karambwan vessel", 65, false)
    );
    private static final Map<String, Spec> BY_NAME;
    static
    {
        Map<String, Spec> map = new HashMap<>();
        for (Spec spec : SPECS)
        {
            map.put(normalize(spec.name), spec);
        }
        BY_NAME = Collections.unmodifiableMap(map);
    }

    private final ItemManager itemManager;
    private final Map<String, Integer> resolvedIds = new HashMap<>();

    @Inject
    public SkillingUnlockCatalog(ItemManager itemManager)
    {
        this.itemManager = itemManager;
    }

    public void clearCache()
    {
        resolvedIds.clear();
    }

    /** Must be called on the RuneLite client thread. */
    public List<RollCandidate> candidates(AccountProgressService progress, ItemAccessService access)
    {
        Map<SkillGate, List<Spec>> byGate = new EnumMap<>(SkillGate.class);
        for (Spec spec : SPECS)
        {
            byGate.computeIfAbsent(spec.gate, ignored -> new ArrayList<>()).add(spec);
        }

        List<RollCandidate> out = new ArrayList<>();
        for (SkillGate gate : SkillGate.values())
        {
            List<Spec> specs = byGate.getOrDefault(gate, List.of());
            boolean skillOpen = access.isSkillUnlocked(gate);
            int level = progress.getLevel(gate);
            List<Spec> eligibleSpecs = new ArrayList<>();

            if (!skillOpen && isLevelScaledToolSkill(gate))
            {
                // Gathering skills open with a useful tool near the player's
                // current level. Entry keys remain eligible, while Tutorial
                // Island starter tools are skipped so a fresh account never pays
                // a roll for an item it already owns.
                for (Spec spec : specs)
                {
                    if ((spec.entryKey || spec.targetLevel <= level + 3)
                        && !StarterItemCatalog.isStarterName(spec.name))
                    {
                        eligibleSpecs.add(spec);
                    }
                }
                eligibleSpecs.sort(Comparator.comparingInt((Spec x) -> x.targetLevel).reversed());
                trimTo(eligibleSpecs, 2);
                if (eligibleSpecs.isEmpty() && !specs.isEmpty())
                {
                    eligibleSpecs.add(specs.stream().min(Comparator.comparingInt(x -> x.targetLevel)).orElse(specs.get(0)));
                }
            }
            else if (!skillOpen)
            {
                // Skills without a linear tool ladder have one intentionally
                // meaningful opening item (Bones, saw, tinderbox, etc.).
                for (Spec spec : specs)
                {
                    if (spec.entryKey)
                    {
                        eligibleSpecs.add(spec);
                    }
                }
            }
            else
            {
                // Once open, surface only useful upgrades the account can use.
                for (Spec spec : specs)
                {
                    if (!spec.entryKey && spec.targetLevel <= level + 3)
                    {
                        eligibleSpecs.add(spec);
                    }
                }
                eligibleSpecs.sort(Comparator.comparingInt((Spec x) -> x.targetLevel).reversed());
                trimTo(eligibleSpecs, 2);
            }

            for (Spec spec : eligibleSpecs)
            {
                int id = resolveId(spec);
                if (id <= 0 || access.isExactUnlocked(id))
                {
                    continue;
                }

                String displayName = spec.name;
                long price = 0L;
                if (spec.fixedItemId <= 0)
                {
                    ItemPrice exact = findExactPrice(spec.name);
                    if (exact != null)
                    {
                        displayName = exact.getName();
                        price = Math.max(0, itemManager.getWikiPrice(exact));
                    }
                }

                out.add(new RollCandidate(
                    id,
                    displayName,
                    price,
                    -200 - spec.gate.ordinal(),
                    CombatStyle.GENERAL,
                    RollCategory.SKILLING,
                    spec.gate,
                    spec.targetLevel,
                    level));
            }
        }
        return out;
    }

    private static boolean isLevelScaledToolSkill(SkillGate gate)
    {
        return gate == SkillGate.WOODCUTTING || gate == SkillGate.MINING || gate == SkillGate.FISHING;
    }

    private static void trimTo(List<Spec> specs, int max)
    {
        while (specs.size() > max)
        {
            specs.remove(specs.size() - 1);
        }
    }

    /** Must be called on the RuneLite client thread. */
    private int resolveId(Spec spec)
    {
        if (spec.fixedItemId > 0)
        {
            return spec.fixedItemId;
        }
        String key = normalize(spec.name);
        Integer cached = resolvedIds.get(key);
        if (cached != null)
        {
            return cached;
        }

        ItemPrice exact = findExactPrice(spec.name);
        int id = exact == null ? -1 : exact.getId();
        resolvedIds.put(key, id);
        return id;
    }

    private ItemPrice findExactPrice(String name)
    {
        String wanted = normalize(name);
        for (ItemPrice price : itemManager.search(name))
        {
            if (price != null && price.getName() != null && normalize(price.getName()).equals(wanted))
            {
                try
                {
                    ItemComposition comp = itemManager.getItemComposition(price.getId());
                    if (comp != null && comp.isGeTradeable() && comp.getNote() == -1)
                    {
                        return price;
                    }
                }
                catch (RuntimeException ignored)
                {
                    return null;
                }
            }
        }
        return null;
    }

    static SkillGate gateForName(String name)
    {
        Spec spec = BY_NAME.get(normalize(name));
        return spec == null ? null : spec.gate;
    }

    static boolean isEntryKeyName(String name)
    {
        Spec spec = BY_NAME.get(normalize(name));
        return spec != null && spec.entryKey;
    }

    static SkillGate gateForSpecialId(int itemId)
    {
        return itemId == CAPTAINS_LOG_AFTER_QUEST ? SkillGate.SAILING : null;
    }

    static String nameForSpecialId(int itemId)
    {
        return itemId == CAPTAINS_LOG_AFTER_QUEST ? "Captain's log" : null;
    }

    static int targetLevelForSpecialId(int itemId)
    {
        return 1;
    }

    static boolean isCatalogName(String name)
    {
        return BY_NAME.containsKey(normalize(name));
    }

    private static String normalize(String text)
    {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
