package com.osrssniffin.threechoices;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Treasure Trail handling. Meaningful clue upgrades may roll; clue cosmetics
 * and filler remain locked/off the GE instead of flooding the pool.
 */
final class ClueRewardCatalog
{
    private static final Pattern HERALDIC_SUFFIX = Pattern.compile(".*\\(h[1-5]\\)$");
    private static final Pattern TRIM_SUFFIX = Pattern.compile(".*\\((?:t|g)\\)$");
    private static final Pattern GOD_PAGE = Pattern.compile("(?:ancient|armadyl|bandos|guthix|saradomin|zamorak) page [1-4]");

    private static final Set<String> MEANINGFUL = Set.of(
        "ranger boots",
        "wizard boots",
        "holy sandals",
        "spiked manacles",
        "robin hood hat",
        "rangers' tunic",
        "rangers' tights",
        "ranger gloves",
        "holy wraps",
        "fremennik kilt",
        "ham joint",
        "master scroll book"
    );

    private static final Set<String> CLUE_ONLY_EXACT = Set.of(
        "cape of skulls", "rain bow", "staff of bob the cat", "climbing boots (g)",
        "ring of nature", "ring of coins", "ring of 3rd age", "heavy casket",
        "giant boot", "uri's hat", "briefcase", "katana", "nunchaku", "dual sai",
        "thieving bag", "mole slippers", "frog slippers", "bear feet", "demon feet",
        "jester cape", "shoulder parrot", "blacksmith's helm", "bucket helm",
        "sagacious spectacles", "top hat", "monocle", "big pirate hat", "deerstalker"
    );

    private ClueRewardCatalog() {}

    static boolean isMeaningfulSingle(String name)
    {
        return MEANINGFUL.contains(normalize(name));
    }

    static boolean isGodPage(String name)
    {
        return GOD_PAGE.matcher(normalize(name)).matches();
    }

    static boolean isGodBlessing(String name)
    {
        String n = normalize(name);
        return n.equals("ancient blessing") || n.equals("holy blessing") || n.equals("honourable blessing")
            || n.equals("peaceful blessing") || n.equals("unholy blessing") || n.equals("war blessing");
    }

    static boolean isBlessedDragonhide(String name)
    {
        String n = normalize(name);
        if (!isGodPrefix(n)) return false;
        return n.endsWith(" coif") || n.endsWith(" chaps") || n.endsWith(" bracers")
            || n.contains(" d'hide body") || n.contains(" d'hide boots") || n.contains(" d'hide shield");
    }

    static boolean isLikelyClueReward(String name)
    {
        String n = normalize(name);
        if (n.isEmpty()) return false;
        if (isMeaningfulSingle(n) || isGodPage(n) || isGodBlessing(n) || isBlessedDragonhide(n)) return true;
        if (CLUE_ONLY_EXACT.contains(n)) return true;
        if (n.contains("3rd age") || n.contains("gilded") || n.contains("elegant")
            || n.contains("cavalier") || n.contains("boater") || n.contains("headband")
            || n.contains("vestment") || n.contains("crozier") || n.contains("stole")
            || n.contains("ornament kit") || n.contains("colour kit") || n.contains("dragon mask")
            || n.contains("pirate hat") || n.contains("tuxedo") || n.contains("royal gown")
            || n.contains("musketeer") || n.contains("mitre") || n.contains("blessed d'hide"))
        {
            return true;
        }
        if (HERALDIC_SUFFIX.matcher(n).matches()) return true;
        if (TRIM_SUFFIX.matcher(n).matches())
        {
            return n.contains("plate") || n.contains("helm") || n.contains("kiteshield")
                || n.contains("d'hide") || n.contains("leather") || n.contains("robe")
                || n.contains("boots") || n.contains("body") || n.contains("chaps");
        }
        return false;
    }

    private static boolean isGodPrefix(String n)
    {
        return n.startsWith("armadyl ") || n.startsWith("bandos ") || n.startsWith("guthix ")
            || n.startsWith("saradomin ") || n.startsWith("zamorak ") || n.startsWith("ancient ");
    }

    private static String normalize(String text)
    {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
