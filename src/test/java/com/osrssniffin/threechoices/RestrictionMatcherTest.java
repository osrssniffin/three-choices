package com.osrssniffin.threechoices;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class RestrictionMatcherTest
{
    @Test
    public void commonSkillActionsMapToExpectedLocks()
    {
        assertEquals(SkillGate.WOODCUTTING, RestrictionService.skillGateForAction("Chop down", "Oak tree"));
        assertEquals(SkillGate.MINING, RestrictionService.skillGateForAction("Mine", "Iron rocks"));
        assertEquals(SkillGate.FISHING, RestrictionService.skillGateForAction("Net", "Fishing spot"));
        assertEquals(SkillGate.PRAYER, RestrictionService.skillGateForAction("Bury", "Bones"));
        assertEquals(SkillGate.FIREMAKING, RestrictionService.skillGateForAction("Light", "Logs"));
        assertEquals(SkillGate.FLETCHING, RestrictionService.skillGateForAction("Fletch", "Logs"));
        assertEquals(SkillGate.HERBLORE, RestrictionService.skillGateForAction("Clean", "Grimy guam leaf"));
        assertEquals(SkillGate.SMITHING, RestrictionService.skillGateForAction("Smith", "Anvil"));
        assertEquals(SkillGate.COOKING, RestrictionService.skillGateForAction("Cook", "Range"));
        assertEquals(SkillGate.RUNECRAFT, RestrictionService.skillGateForAction("Craft-rune", "Air altar"));
        assertEquals(SkillGate.CRAFTING, RestrictionService.skillGateForAction("Craft", "Leather"));
        assertEquals(SkillGate.THIEVING, RestrictionService.skillGateForAction("Pickpocket", "Man"));
        assertEquals(SkillGate.AGILITY, RestrictionService.skillGateForAction("Cross", "Log balance"));
        assertEquals(SkillGate.FARMING, RestrictionService.skillGateForAction("Rake", "Herb patch"));
        assertEquals(SkillGate.HUNTER, RestrictionService.skillGateForAction("Set-trap", "Trap"));
        assertEquals(SkillGate.CONSTRUCTION, RestrictionService.skillGateForAction("Build", "Chair space"));
        assertEquals(SkillGate.SLAYER, RestrictionService.skillGateForAction("Get-task", "Slayer master"));
    }

    @Test
    public void prayerTrainingSpellMatcherIsNarrow()
    {
        assertEquals(SkillGate.PRAYER, RestrictionService.skillGateForAction("Cast", "Sinister Offering"));
        assertEquals(SkillGate.PRAYER, RestrictionService.skillGateForAction("Cast", "Demonic Offering"));
        assertNull(RestrictionService.skillGateForAction("Cast", "High Level Alchemy"));
    }

    @Test
    public void itemOnTargetTrainingPathsAreCovered()
    {
        assertEquals(SkillGate.PRAYER, RestrictionService.skillGateForItemUseNames("Bones", "Altar"));
        assertEquals(SkillGate.COOKING, RestrictionService.skillGateForItemUseNames("Raw lobster", "Range"));
        assertEquals(SkillGate.FIREMAKING, RestrictionService.skillGateForItemUseNames("Tinderbox", "Logs"));
        assertEquals(SkillGate.FLETCHING, RestrictionService.skillGateForItemUseNames("Knife", "Maple logs"));
        assertEquals(SkillGate.SMITHING, RestrictionService.skillGateForItemUseNames("Iron ore", "Furnace"));
        assertEquals(SkillGate.CRAFTING, RestrictionService.skillGateForItemUseNames("Chisel", "Uncut sapphire"));
        assertEquals(SkillGate.CONSTRUCTION, RestrictionService.skillGateForItemUseNames("Hammer", "Chair space"));
        assertEquals(SkillGate.HUNTER, RestrictionService.skillGateForItemUseNames("Box trap", "Chinchompa"));
    }
}
