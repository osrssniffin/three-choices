package com.osrssniffin.threechoices;

import java.util.Locale;
import java.util.Set;

/**
 * Low-value quest nuisance items which may be self-obtained/created without a
 * Three Choices roll. They are intentionally NOT GE/shop acquisition unlocks.
 */
final class QuestFreeItemCatalog
{
    private static final Set<String> FREE_SELF_OBTAINED = Set.of(
        "cheese", "rope", "egg", "bucket of milk", "pot of flour", "flour",
        "cabbage", "onion", "redberries", "cadava berries", "banana", "orange",
        "tomato", "potato", "garlic", "dwellberries", "pineapple", "lemon",
        "lime", "papaya fruit", "charcoal", "ashes", "wool", "ball of wool",
        "silk", "beads of the dead", "bones to peaches tablet", "bucket of water",
        "jug of water", "bowl of water", "empty cup", "cup of tea", "bottle of water",
        "white apron", "chef's hat", "pink skirt", "brown apron", "spade", "shears",
        "cake tin", "pie dish", "bowl", "jug", "empty pot", "empty bucket",
        "red dye", "yellow dye", "blue dye", "orange dye", "purple dye", "green dye",
        "black mushroom", "sliced banana", "burnt meat", "cooked meat", "raw sardine",
        "raw herring", "raw chicken", "raw beef", "raw rat meat", "raw bear meat"
    );

    private QuestFreeItemCatalog() {}

    static boolean isFreeSelfObtained(String name)
    {
        return FREE_SELF_OBTAINED.contains(normalize(name));
    }

    private static String normalize(String text)
    {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
