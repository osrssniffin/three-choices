package com.osrssniffin.threechoices;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SkillGateBaselineTest
{
    @Test
    public void onlyWoodcuttingAndFiremakingStartOpen()
    {
        assertEquals(2, SkillGate.starterOpenSkills().size());
        assertTrue(SkillGate.starterOpenSkills().contains(SkillGate.WOODCUTTING));
        assertTrue(SkillGate.starterOpenSkills().contains(SkillGate.FIREMAKING));
        assertFalse(SkillGate.starterOpenSkills().contains(SkillGate.MINING));
        assertFalse(SkillGate.starterOpenSkills().contains(SkillGate.FISHING));
        assertFalse(SkillGate.starterOpenSkills().contains(SkillGate.PRAYER));
        assertEquals(18, SkillGate.values().length);
    }
}
