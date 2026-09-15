package com.osrssniffin.threechoices;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

/**
 * Logical, level-matched training methods. These are intentionally compact and
 * only cover cases where a resource/method is a better progression key than an
 * arbitrary reusable tool. The representative item is the ID stored in a roll;
 * grouped methods unlock every required member when chosen.
 */
@Singleton
public class TrainingMethodCatalog
{
    static final class Method
    {
        final SkillGate gate;
        final String displayName;
        final int targetLevel;
        final List<String> memberNames;
        final boolean opensSkill;
        final boolean grouped;

        Method(SkillGate gate, String displayName, int targetLevel, boolean opensSkill, String... memberNames)
        {
            this.gate = gate;
            this.displayName = displayName;
            this.targetLevel = targetLevel;
            this.opensSkill = opensSkill;
            this.memberNames = List.of(memberNames);
            this.grouped = memberNames.length > 1;
        }
    }

    // Raw food is a natural Cooking opener. Only a food the player can actually
    // cook at their real Cooking level is eligible.
    private static final List<Method> COOKING_OPENERS = List.of(
        new Method(SkillGate.COOKING, "Raw shrimps", 1, true, "Raw shrimps"),
        new Method(SkillGate.COOKING, "Raw herring", 5, true, "Raw herring"),
        new Method(SkillGate.COOKING, "Raw mackerel", 10, true, "Raw mackerel"),
        new Method(SkillGate.COOKING, "Raw trout", 15, true, "Raw trout"),
        new Method(SkillGate.COOKING, "Raw cod", 18, true, "Raw cod"),
        new Method(SkillGate.COOKING, "Raw pike", 20, true, "Raw pike"),
        new Method(SkillGate.COOKING, "Raw salmon", 25, true, "Raw salmon"),
        new Method(SkillGate.COOKING, "Raw tuna", 30, true, "Raw tuna"),
        new Method(SkillGate.COOKING, "Raw lobster", 40, true, "Raw lobster"),
        new Method(SkillGate.COOKING, "Raw swordfish", 45, true, "Raw swordfish"),
        new Method(SkillGate.COOKING, "Raw monkfish", 62, true, "Raw monkfish"),
        new Method(SkillGate.COOKING, "Raw shark", 80, true, "Raw shark"),
        new Method(SkillGate.COOKING, "Raw anglerfish", 84, true, "Raw anglerfish"),
        new Method(SkillGate.COOKING, "Raw dark crab", 90, true, "Raw dark crab")
    );

    // Complete potion recipes: one roll choice, one coherent training method.
    // Levels intentionally track normal potion creation requirements.
    private static final List<Method> HERBLORE_METHODS = List.of(
        new Method(SkillGate.HERBLORE, "Attack potion ingredients", 3, true,
            "Vial of water", "Guam leaf", "Eye of newt"),
        new Method(SkillGate.HERBLORE, "Antipoison ingredients", 5, true,
            "Vial of water", "Marrentill", "Unicorn horn dust"),
        new Method(SkillGate.HERBLORE, "Strength potion ingredients", 12, true,
            "Vial of water", "Tarromin", "Limpwurt root"),
        new Method(SkillGate.HERBLORE, "Energy potion ingredients", 26, true,
            "Vial of water", "Harralander", "Chocolate dust"),
        new Method(SkillGate.HERBLORE, "Defence potion ingredients", 30, true,
            "Vial of water", "Ranarr weed", "White berries"),
        new Method(SkillGate.HERBLORE, "Agility potion ingredients", 34, true,
            "Vial of water", "Toadflax", "Toad's legs"),
        new Method(SkillGate.HERBLORE, "Combat potion ingredients", 36, true,
            "Vial of water", "Harralander", "Goat horn dust"),
        new Method(SkillGate.HERBLORE, "Prayer potion ingredients", 38, true,
            "Vial of water", "Ranarr weed", "Snape grass"),
        new Method(SkillGate.HERBLORE, "Super attack ingredients", 45, true,
            "Vial of water", "Irit leaf", "Eye of newt"),
        new Method(SkillGate.HERBLORE, "Super strength ingredients", 55, true,
            "Vial of water", "Kwuarm", "Limpwurt root"),
        new Method(SkillGate.HERBLORE, "Super restore ingredients", 63, true,
            "Vial of water", "Snapdragon", "Red spiders' eggs"),
        new Method(SkillGate.HERBLORE, "Super defence ingredients", 66, true,
            "Vial of water", "Cadantine", "White berries"),
        new Method(SkillGate.HERBLORE, "Ranging potion ingredients", 72, true,
            "Vial of water", "Dwarf weed", "Wine of zamorak"),
        new Method(SkillGate.HERBLORE, "Magic potion ingredients", 76, true,
            "Vial of water", "Lantadyme", "Potato cactus"),
        new Method(SkillGate.HERBLORE, "Saradomin brew ingredients", 81, true,
            "Vial of water", "Toadflax", "Crushed nest")
    );

    // A bar is a much more natural first Smithing method than "Hammer unlocked".
    private static final List<Method> SMITHING_OPENERS = List.of(
        new Method(SkillGate.SMITHING, "Bronze bar", 1, true, "Bronze bar"),
        new Method(SkillGate.SMITHING, "Iron bar", 15, true, "Iron bar"),
        // Silver/gold bars are Crafting materials rather than normal anvil
        // Smithing progression, so stay on the real metal-equipment ladder.
        new Method(SkillGate.SMITHING, "Steel bar", 30, true, "Steel bar"),
        new Method(SkillGate.SMITHING, "Mithril bar", 50, true, "Mithril bar"),
        new Method(SkillGate.SMITHING, "Adamantite bar", 70, true, "Adamantite bar"),
        new Method(SkillGate.SMITHING, "Runite bar", 85, true, "Runite bar")
    );

    private final ItemManager itemManager;
    private final ThreeChoicesStateService stateService;
    private final Map<String, Integer> resolvedIds = new HashMap<>();
    private final Map<Integer, Method> byRepresentative = new HashMap<>();
    private final Map<Integer, List<Integer>> memberIdsByRepresentative = new HashMap<>();

    @Inject
    public TrainingMethodCatalog(ItemManager itemManager, ThreeChoicesStateService stateService)
    {
        this.itemManager = itemManager;
        this.stateService = stateService;
    }

    public void clearCache()
    {
        resolvedIds.clear();
        byRepresentative.clear();
        memberIdsByRepresentative.clear();
    }

    /** Must be called on the RuneLite client thread. */
    public List<RollCandidate> candidates(AccountProgressService progress, ItemAccessService access)
    {
        List<RollCandidate> out = new ArrayList<>();
        addBestLockedMethod(out, COOKING_OPENERS, progress, access);
        addBestLockedMethod(out, HERBLORE_METHODS, progress, access);
        addBestLockedMethod(out, SMITHING_OPENERS, progress, access);
        return out;
    }

    private void addBestLockedMethod(
        List<RollCandidate> out,
        List<Method> methods,
        AccountProgressService progress,
        ItemAccessService access)
    {
        if (methods.isEmpty()) return;
        SkillGate gate = methods.get(0).gate;
        if (access.isSkillUnlocked(gate)) return;

        int level = progress.getLevel(gate);
        Method best = null;
        Method previous = null;
        for (Method method : methods)
        {
            if (method.targetLevel <= level)
            {
                previous = best;
                best = method;
            }
        }
        if (best == null) return; // e.g. Herblore level 1-2 has no legal potion yet.

        // Give the best current method plus, at most, one nearby alternative. This
        // preserves choice without flooding the roll pool with generic resources.
        addResolved(out, best, level, access);
        if (previous != null && best.targetLevel - previous.targetLevel <= 12)
        {
            addResolved(out, previous, level, access);
        }
    }

    /** Must be called on the RuneLite client thread. */
    private void addResolved(List<RollCandidate> out, Method method, int skillLevel, ItemAccessService access)
    {
        List<Integer> members = resolveMembers(method);
        if (members.isEmpty()) return;

        int representative = firstStillLocked(members, access);
        if (representative <= 0) return;

        // Multiple grouped methods can resolve to the same still-locked item
        // (most notably every Herblore recipe starting with a vial of water).
        // The best current-level method is added first; never add a second
        // differently-named choice with the same representative ID, otherwise
        // pending-roll display/cache reconstruction can mislabel the recipe.
        for (RollCandidate existing : out)
        {
            if (existing.getItemId() == representative) return;
        }

        // Keep the representative mapped to that best current-level method.
        byRepresentative.putIfAbsent(representative, method);
        memberIdsByRepresentative.putIfAbsent(representative, List.copyOf(members));
        long price = 0L;
        for (String name : method.memberNames)
        {
            ItemPrice exact = findExactPrice(name);
            if (exact != null) price += Math.max(0, itemManager.getWikiPrice(exact));
        }

        out.add(new RollCandidate(
            representative,
            method.displayName,
            price,
            -300 - method.gate.ordinal(),
            CombatStyle.GENERAL,
            RollCategory.SKILL_METHOD,
            method.gate,
            method.targetLevel,
            skillLevel));
    }

    Method methodForRepresentative(int itemId, ItemAccessService access)
    {
        Method cached = byRepresentative.get(itemId);
        if (cached != null) return cached;
        if (access == null) return null;

        // Grouped methods can share ordinary ingredient IDs (for example every
        // potion recipe includes a vial of water). Persist the method identity
        // with a pending roll so a RuneLite restart cannot reinterpret a rolled
        // recipe as a different lower-level recipe.
        String pendingKey = stateService.getPendingChoiceMeta(itemId);
        if (pendingKey != null)
        {
            Method pending = methodByKey(pendingKey);
            if (pending != null)
            {
                List<Integer> members = resolveMembers(pending);
                byRepresentative.put(itemId, pending);
                memberIdsByRepresentative.put(itemId, List.copyOf(members));
                return pending;
            }
        }

        for (List<Method> methods : List.of(COOKING_OPENERS, HERBLORE_METHODS, SMITHING_OPENERS))
        {
            for (Method method : methods)
            {
                // Dynamic inference is only for a still-locked skill. Once the
                // skill has opened, an ordinary ingredient ID must not be relabeled
                // as a historical grouped method after a restart.
                if (stateService.isSkillUnlocked(method.gate)) continue;
                List<Integer> members = resolveMembers(method);
                if (!members.isEmpty() && firstStillLocked(members, access) == itemId)
                {
                    byRepresentative.put(itemId, method);
                    memberIdsByRepresentative.put(itemId, List.copyOf(members));
                    return method;
                }
            }
        }
        return null;
    }

    String methodKeyForRepresentative(int itemId, ItemAccessService access)
    {
        Method method = methodForRepresentative(itemId, access);
        return method == null ? null : methodKey(method);
    }

    private Method methodByKey(String key)
    {
        if (key == null || key.isBlank()) return null;
        for (List<Method> methods : List.of(COOKING_OPENERS, HERBLORE_METHODS, SMITHING_OPENERS))
        {
            for (Method method : methods)
            {
                if (methodKey(method).equals(key)) return method;
            }
        }
        return null;
    }

    private static String methodKey(Method method)
    {
        return method.gate.name() + ":" + normalize(method.displayName).replace(' ', '_');
    }

    List<Integer> membersForRepresentative(int itemId, ItemAccessService access)
    {
        methodForRepresentative(itemId, access);
        return memberIdsByRepresentative.getOrDefault(itemId, List.of(itemId));
    }

    String displayNameForCachedItem(int itemId)
    {
        Method method = byRepresentative.get(itemId);
        return method == null ? null : method.displayName;
    }

    private static int firstStillLocked(List<Integer> ids, ItemAccessService access)
    {
        for (int id : ids)
        {
            if (!access.isExactUnlocked(id)) return id;
        }
        return -1;
    }

    private List<Integer> resolveMembers(Method method)
    {
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        for (String name : method.memberNames)
        {
            int id = resolveExactId(name);
            if (id <= 0) return List.of();
            ids.add(id);
        }
        return new ArrayList<>(ids);
    }

    private int resolveExactId(String name)
    {
        String key = normalize(name);
        Integer cached = resolvedIds.get(key);
        if (cached != null) return cached;
        ItemPrice exact = findExactPrice(name);
        int id = exact == null ? -1 : exact.getId();
        resolvedIds.put(key, id);
        return id;
    }

    private ItemPrice findExactPrice(String name)
    {
        String wanted = normalize(name);
        for (ItemPrice price : itemManager.search(name))
        {
            if (price == null || price.getName() == null || !normalize(price.getName()).equals(wanted)) continue;
            try
            {
                ItemComposition comp = itemManager.getItemComposition(price.getId());
                if (comp != null && comp.isGeTradeable() && comp.getNote() == -1) return price;
            }
            catch (RuntimeException ignored)
            {
                return null;
            }
        }
        return null;
    }

    private static String normalize(String text)
    {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
