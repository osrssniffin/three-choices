package com.osrssniffin.threechoices;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

/** Groups equivalent/multi-piece unlocks so the three choices stay exciting. */
@Singleton
public class UnlockBundleCatalog
{
    static final class Bundle
    {
        final String key;
        final String displayName;
        final List<String> memberNames;
        final RollCategory category;
        final SkillGate skillGate;
        final int progressionScore;
        final int requiredRanged;
        final int requiredDefence;
        final int slot;

        Bundle(String key, String displayName, List<String> memberNames, RollCategory category,
               SkillGate skillGate, int progressionScore, int requiredRanged, int requiredDefence, int slot)
        {
            this.key = key;
            this.displayName = displayName;
            this.memberNames = memberNames;
            this.category = category;
            this.skillGate = skillGate;
            this.progressionScore = progressionScore;
            this.requiredRanged = requiredRanged;
            this.requiredDefence = requiredDefence;
            this.slot = slot;
        }
    }

    private static final List<String> GODS = List.of("Ancient", "Armadyl", "Bandos", "Guthix", "Saradomin", "Zamorak");
    private static final List<Bundle> DEFINITIONS;
    static
    {
        List<Bundle> defs = new ArrayList<>();
        for (String god : GODS)
        {
            defs.add(new Bundle(
                "pages-" + god.toLowerCase(Locale.ROOT),
                god + " god pages (1-4)",
                List.of(god + " page 1", god + " page 2", god + " page 3", god + " page 4"),
                RollCategory.BUNDLE,
                null,
                25,
                0,
                0,
                -400));
        }

        defs.add(new Bundle(
            "god-blessings",
            "God blessings (all six)",
            List.of("Ancient blessing", "Holy blessing", "Honourable blessing",
                "Peaceful blessing", "Unholy blessing", "War blessing"),
            RollCategory.BUNDLE,
            null,
            25,
            0,
            0,
            EquipmentInventorySlot.AMMO.getSlotIdx()));

        defs.add(blessed("boots", "Blessed d'hide boots (all gods)", "d'hide boots", 40, EquipmentInventorySlot.BOOTS.getSlotIdx()));
        defs.add(blessed("coif", "Blessed d'hide coifs (all gods)", "coif", 40, EquipmentInventorySlot.HEAD.getSlotIdx()));
        defs.add(blessed("body", "Blessed d'hide bodies (all gods)", "d'hide body", 40, EquipmentInventorySlot.BODY.getSlotIdx()));
        defs.add(blessed("chaps", "Blessed d'hide chaps (all gods)", "chaps", 0, EquipmentInventorySlot.LEGS.getSlotIdx()));
        defs.add(blessed("bracers", "Blessed d'hide bracers (all gods)", "bracers", 0, EquipmentInventorySlot.GLOVES.getSlotIdx()));
        defs.add(blessed("shield", "Blessed d'hide shields (all gods)", "d'hide shield", 40, EquipmentInventorySlot.SHIELD.getSlotIdx()));
        DEFINITIONS = Collections.unmodifiableList(defs);
    }

    private static Bundle blessed(String key, String display, String suffix, int defence, int slot)
    {
        List<String> members = new ArrayList<>();
        for (String god : GODS)
        {
            members.add(god + " " + suffix);
        }
        return new Bundle("blessed-" + key, display, Collections.unmodifiableList(members),
            RollCategory.BUNDLE, null, 72, 70, defence, slot);
    }

    private final ItemManager itemManager;
    private final Map<String, Integer> nameToId = new ConcurrentHashMap<>();
    private final Map<Integer, Bundle> memberToBundle = new ConcurrentHashMap<>();
    private final Map<String, List<Integer>> resolvedMembers = new ConcurrentHashMap<>();

    @Inject
    public UnlockBundleCatalog(ItemManager itemManager)
    {
        this.itemManager = itemManager;
    }

    public void clearCache()
    {
        nameToId.clear();
        memberToBundle.clear();
        resolvedMembers.clear();
    }

    /** Must be called on the RuneLite client thread. */
    public List<RollCandidate> candidates(AccountProgressService progress, ItemAccessService access)
    {
        CombatProfile profile = progress.getSnapshot();
        List<RollCandidate> out = new ArrayList<>();
        for (Bundle bundle : DEFINITIONS)
        {
            if (bundle.requiredRanged > 0 && profile.getRanged() < bundle.requiredRanged) continue;
            if (bundle.requiredDefence > 0 && profile.getDefence() < bundle.requiredDefence) continue;

            List<Integer> members = resolveMembers(bundle);
            if (members.size() != bundle.memberNames.size()) continue;

            int remaining = 0;
            int representative = -1;
            long totalPrice = 0L;
            for (int id : members)
            {
                if (!access.isExactUnlocked(id))
                {
                    remaining++;
                    if (representative <= 0) representative = id;
                }
                totalPrice += Math.max(0, itemManager.getItemPrice(id));
            }
            if (remaining == 0 || representative <= 0) continue;

            int capacity = bundle.requiredRanged > 0 ? profile.getRanged() : Math.max(25, profile.highestOffensiveSkill());
            out.add(new RollCandidate(
                representative,
                bundle.displayName,
                totalPrice,
                bundle.slot,
                bundle.requiredRanged > 0 ? CombatStyle.RANGED : CombatStyle.GENERAL,
                bundle.category,
                bundle.skillGate,
                bundle.progressionScore,
                capacity));
        }
        return out;
    }

    /** Must be called on the RuneLite client thread. */
    public Bundle bundleForItem(int itemId)
    {
        Bundle cached = memberToBundle.get(itemId);
        if (cached != null) return cached;
        for (Bundle bundle : DEFINITIONS)
        {
            List<Integer> members = resolveMembers(bundle);
            if (members.contains(itemId)) return bundle;
        }
        return null;
    }

    /** Must be called on the RuneLite client thread. */
    public List<Integer> membersForRepresentative(int itemId)
    {
        Bundle bundle = bundleForItem(itemId);
        return bundle == null ? List.of() : resolveMembers(bundle);
    }

    public String displayNameForCachedItem(int itemId)
    {
        Bundle bundle = memberToBundle.get(itemId);
        return bundle == null ? null : bundle.displayName;
    }

    /** Must be called on the RuneLite client thread. */
    private List<Integer> resolveMembers(Bundle bundle)
    {
        List<Integer> cached = resolvedMembers.get(bundle.key);
        if (cached != null) return cached;

        Set<Integer> ids = new LinkedHashSet<>();
        for (String name : bundle.memberNames)
        {
            int id = resolveExactTradeableId(name);
            if (id > 0)
            {
                ids.add(id);
                memberToBundle.put(id, bundle);
            }
        }
        List<Integer> resolved = Collections.unmodifiableList(new ArrayList<>(ids));
        resolvedMembers.put(bundle.key, resolved);
        return resolved;
    }

    private int resolveExactTradeableId(String name)
    {
        String key = normalize(name);
        Integer cached = nameToId.get(key);
        if (cached != null) return cached;

        int found = -1;
        for (ItemPrice price : itemManager.search(name))
        {
            if (price == null || price.getName() == null || !normalize(price.getName()).equals(key)) continue;
            try
            {
                ItemComposition c = itemManager.getItemComposition(price.getId());
                if (c != null && c.isGeTradeable() && c.getNote() == -1)
                {
                    found = price.getId();
                    break;
                }
            }
            catch (RuntimeException ignored)
            {
                // Live definitions can be briefly unavailable during login.
            }
        }
        nameToId.put(key, found);
        return found;
    }

    private static String normalize(String text)
    {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
