package com.osrssniffin.threechoices;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class PointAwardServiceBalanceTest
{
    @Test
    public void skillingRateTapersByCompletedRolls()
    {
        assertEquals(500L, PointAwardService.pointsPer1000XpForCompletedRolls(0));
        assertEquals(350L, PointAwardService.pointsPer1000XpForCompletedRolls(1));
        assertEquals(350L, PointAwardService.pointsPer1000XpForCompletedRolls(2));
        assertEquals(225L, PointAwardService.pointsPer1000XpForCompletedRolls(3));
        assertEquals(225L, PointAwardService.pointsPer1000XpForCompletedRolls(5));
        assertEquals(125L, PointAwardService.pointsPer1000XpForCompletedRolls(6));
        assertEquals(125L, PointAwardService.pointsPer1000XpForCompletedRolls(10));
        assertEquals(60L, PointAwardService.pointsPer1000XpForCompletedRolls(11));
        assertEquals(60L, PointAwardService.pointsPer1000XpForCompletedRolls(100));
    }

    @Test
    public void negativeRollCountUsesFreshAccountRate()
    {
        assertEquals(500L, PointAwardService.pointsPer1000XpForCompletedRolls(-1));
    }

    @Test
    public void combatRateIsTwentyPercentOfSkillingRate()
    {
        assertEquals(100L, PointAwardService.combatPointsPer1000Xp(500L));
        assertEquals(70L, PointAwardService.combatPointsPer1000Xp(350L));
        assertEquals(45L, PointAwardService.combatPointsPer1000Xp(225L));
        assertEquals(25L, PointAwardService.combatPointsPer1000Xp(125L));
        assertEquals(12L, PointAwardService.combatPointsPer1000Xp(60L));
    }
}
