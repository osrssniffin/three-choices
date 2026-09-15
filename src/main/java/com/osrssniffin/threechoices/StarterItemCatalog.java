package com.osrssniffin.threechoices;

import java.util.Locale;
import java.util.Set;

/**
 * Exact item types a normal account keeps when leaving Tutorial Island.
 * These are the only item types that begin unlocked in Three Choices.
 */
final class StarterItemCatalog
{
    private static final Set<String> STARTER_NAMES = Set.of(
        "bronze axe",
        "bronze pickaxe",
        "tinderbox",
        "small fishing net",
        "shrimps",
        "bronze dagger",
        "bronze sword",
        "wooden shield",
        "shortbow",
        "bronze arrow",
        "air rune",
        "mind rune",
        "bucket",
        "pot",
        "bread",
        "water rune",
        "earth rune",
        "body rune",
        "coins"
    );

    private StarterItemCatalog() {}

    static boolean isStarterName(String name)
    {
        return STARTER_NAMES.contains(normalize(name));
    }

    private static String normalize(String text)
    {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
