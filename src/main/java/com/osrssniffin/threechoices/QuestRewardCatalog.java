package com.osrssniffin.threechoices;

import java.util.List;
import java.util.Locale;

/**
 * Meaningful direct quest-item rewards are earned by doing their quest, not by
 * spending a Three Choices roll. Generic quest prerequisites and ordinary
 * post-quest shop/content unlocks are intentionally not treated as rewards here
 * (for example Dragon scimitar).
 *
 * Names are kept instead of a giant item-ID table so current RuneLite item data
 * remains authoritative. Untradeable rewards are legal when genuinely owned;
 * tradeable rewards are exact-unlocked when the quest observer sees completion.
 */
final class QuestRewardCatalog
{
    static final class Entry
    {
        final String questName;
        final SkillGate skillGate;
        final int fixedPrimaryRewardId;
        final List<String> rewardNames;

        Entry(String questName, SkillGate skillGate, String... rewardNames)
        {
            this(questName, skillGate, -1, rewardNames);
        }

        Entry(String questName, SkillGate skillGate, int fixedPrimaryRewardId, String... rewardNames)
        {
            this.questName = questName;
            this.skillGate = skillGate;
            this.fixedPrimaryRewardId = fixedPrimaryRewardId;
            this.rewardNames = List.of(rewardNames);
        }
    }

    private static final List<Entry> ENTRIES = List.of(
        // Weapons / combat tools directly obtained through quest completion.
        new Entry("A Tail of Two Cats", null, "Mouse toy"),
        new Entry("A Taste of Hope", null, "Ivandis flail", "Drakan's medallion"),
        new Entry("Animal Magnetism", null, "Blessed axe", "Ava's attractor", "Ava's accumulator"),
        new Entry("Another Slice of H.A.M.", null, "Ancient mace"),
        new Entry("Beneath Cursed Sands", null, "Keris partisan", "Circlet of water"),
        new Entry("Between a Rock...", null, "Rune pickaxe", "Gold helmet"),
        new Entry("Contact!", null, "Keris"),
        new Entry("Demon Slayer", null, "Silverlight"),
        new Entry("Desert Treasure I", null, "Ancient staff", "Ring of visibility"),
        new Entry("Desert Treasure II - The Fallen Empire", null, "Ring of shadows"),
        new Entry("Dragon Slayer I", null, "Anti-dragon shield"),
        new Entry("Fairytale I - Growing Pains", null, "Magic secateurs"),
        new Entry("In Aid of the Myreque", null, "Gadderhammer", "Rod of ivandis"),
        new Entry("Lost City", null, "Dramen staff"),
        new Entry("Lunar Diplomacy", null,
            "Lunar staff", "Lunar helm", "Lunar torso", "Lunar legs", "Lunar gloves",
            "Lunar boots", "Lunar cape", "Lunar amulet", "Lunar ring", "Seal of passage"),
        new Entry("Mage Arena I", null,
            "Saradomin staff", "Zamorak staff", "Guthix staff",
            "Saradomin cape", "Zamorak cape", "Guthix cape"),
        new Entry("Mage Arena II", null,
            "Imbued saradomin cape", "Imbued zamorak cape", "Imbued guthix cape"),
        new Entry("Merlin's Crystal", null, "Excalibur"),
        new Entry("Nature Spirit", null, "Silver sickle (b)"),
        new Entry("Priest in Peril", null, "Wolfbane"),
        new Entry("Ratcatchers", null, "Rat pole"),
        new Entry("Roving Elves", null, "Crystal bow", "Crystal shield"),
        new Entry("Shadow of the Storm", null, "Darklight"),
        new Entry("Sins of the Father", null, "Blisterwood flail"),
        new Entry("Tai Bwo Wannai Trio", null, "Rune spear(kp)"),
        new Entry("The Enchanted Key", null, "Saradomin mjolnir", "Zamorak mjolnir", "Guthix mjolnir"),
        new Entry("The Final Dawn", null, "Arkan blade"),
        new Entry("The General's Shadow", null, "Shadow sword"),
        new Entry("The Great Brain Robbery", null, "Barrelchest anchor"),
        new Entry("The Knight's Sword", null, "Blurite sword"),
        new Entry("Underground Pass", null, "Iban's staff", "Klank's gauntlets"),

        // Meaningful armour / utility equipment that is directly quest-earned.
        new Entry("A Kingdom Divided", null, "Book of the dead"),
        new Entry("Client of Kourend", null, "Kharedst's memoirs"),
        new Entry("Elemental Workshop I", null, "Elemental shield"),
        new Entry("Elemental Workshop II", null, "Mind helmet"),
        new Entry("Family Crest", null, "Steel gauntlets"),
        new Entry("Fight Arena", null, "Khazard helmet", "Khazard armour"),
        new Entry("The Fremennik Isles", null, "Helm of neitiznot"),
        new Entry("Grim Tales", null, "Dwarven helmet"),
        new Entry("Hazeel Cult", null, "Carnillean armour", "Hazeel's mark"),
        new Entry("Haunted Mine", null, "Salve amulet"),
        new Entry("Horror from the Deep", null,
            "Holy book", "Unholy book", "Book of balance", "Book of war", "Book of law", "Book of darkness"),
        new Entry("Imp Catcher", null, "Amulet of accuracy"),
        new Entry("Lair of Tarn Razorlor", null, "Salve amulet(e)"),
        new Entry("Legends' Quest", null, "Cape of legends"),
        new Entry("Mountain Daughter", null, "Bearhead"),
        new Entry("Rag and Bone Man II", null, "Bonesack", "Ram skull helm"),
        new Entry("Recruitment Drive", null, "Initiate sallet"),
        new Entry("Shilo Village", null, "Beads of the dead"),
        new Entry("Tree Gnome Village", null, "Gnome amulet"),
        new Entry("What Lies Below", null, "Beacon ring"),

        // Utility rewards that could otherwise be misclassified as progression.
        new Entry("A Porcine of Interest", null, "Reinforced goggles"),
        new Entry("Bear Your Soul", null, "Soul bearer"),
        new Entry("Creature of Fenkenstrain", null, "Ring of charos"),
        new Entry("Enakhra's Lament", null, "Camulet"),
        new Entry("Ghosts Ahoy", null, "Ectophial"),
        new Entry("Garden of Tranquillity", null, "Ring of charos(a)"),
        new Entry("Monkey Madness II", null, "Royal seed pod"),
        new Entry("One Small Favour", null, "Steel key ring"),
        new Entry("Rum Deal", null, "Holy wrench"),
        new Entry("The Eyes of Glouphrie", null, "Crystal saw seed"),
        new Entry("The Restless Ghost", null, "Ghostspeak amulet"),
        new Entry("Waterfall Quest", null, "Glarial's amulet"),

        // Pandemonium remains a legitimate direct quest route to Sailing.
        // Captain's log is also a curated roll entry key in SkillingUnlockCatalog.
        new Entry("Pandemonium", SkillGate.SAILING, 31986, "Captain's log")
    );

    private QuestRewardCatalog() {}

    static List<Entry> entries()
    {
        return ENTRIES;
    }

    static Entry entryForQuestName(String questName)
    {
        String wanted = normalize(questName);
        if (wanted.isEmpty()) return null;
        for (Entry entry : ENTRIES)
        {
            if (normalize(entry.questName).equals(wanted)) return entry;
        }
        return null;
    }

    static boolean isDirectQuestRewardName(String name)
    {
        String wanted = normalize(name);
        if (wanted.isEmpty()) return false;
        for (Entry entry : ENTRIES)
        {
            for (String reward : entry.rewardNames)
            {
                if (normalize(reward).equals(wanted)) return true;
            }
        }
        return false;
    }

    private static String normalize(String text)
    {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
