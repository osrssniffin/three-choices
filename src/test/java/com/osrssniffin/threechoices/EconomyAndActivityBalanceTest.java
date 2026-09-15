package com.osrssniffin.threechoices;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class EconomyAndActivityBalanceTest
{
    @Test
    public void paidRollCostIsAlwaysThreeThousand()
    {
        assertEquals(3_000L, ProgressionEconomyService.ROLL_COST);
    }

    @Test
    public void bonusMultiplierTapersAtExpectedBoundaries()
    {
        assertEquals(1.00d, ProgressionEconomyService.bonusMultiplierForCompletedRolls(0), 0.0001d);
        assertEquals(0.85d, ProgressionEconomyService.bonusMultiplierForCompletedRolls(1), 0.0001d);
        assertEquals(0.85d, ProgressionEconomyService.bonusMultiplierForCompletedRolls(2), 0.0001d);
        assertEquals(0.70d, ProgressionEconomyService.bonusMultiplierForCompletedRolls(3), 0.0001d);
        assertEquals(0.70d, ProgressionEconomyService.bonusMultiplierForCompletedRolls(5), 0.0001d);
        assertEquals(0.55d, ProgressionEconomyService.bonusMultiplierForCompletedRolls(6), 0.0001d);
        assertEquals(0.55d, ProgressionEconomyService.bonusMultiplierForCompletedRolls(10), 0.0001d);
        assertEquals(0.40d, ProgressionEconomyService.bonusMultiplierForCompletedRolls(11), 0.0001d);
        assertEquals(1.00d, ProgressionEconomyService.bonusMultiplierForCompletedRolls(-5), 0.0001d);
    }

    @Test
    public void activityCompletionRewardsFitInsideCurrentEconomy()
    {
        assertEquals(750L, ActivityCreditTracker.basePointsForMessage("Your completed Chambers of Xeric count is: 10"));
        assertEquals(1_110L, ActivityCreditTracker.basePointsForMessage("Your completed Tombs of Amascut: Expert Mode count is: 42"));
        assertEquals(210L, ActivityCreditTracker.basePointsForMessage("Your completed Theatre of Blood: Entry Mode count is: 3"));
        assertEquals(270L, ActivityCreditTracker.basePointsForMessage("Your Corrupted Gauntlet completion count is: 8"));
        assertEquals(1_500L, ActivityCreditTracker.basePointsForMessage("Your TzKal-Zuk kill count is: 1"));
        assertEquals(600L, ActivityCreditTracker.basePointsForMessage("Your TzTok-Jad kill count is: 5"));
        assertEquals(300L, ActivityCreditTracker.basePointsForMessage("You have completed 12 master Treasure Trails."));
        assertEquals(0L, ActivityCreditTracker.basePointsForMessage("unrelated game message"));

        assertTrue(ActivityCreditTracker.basePointsForMessage("Your TzKal-Zuk kill count is: 1") < ProgressionEconomyService.ROLL_COST);
    }

    @Test
    public void npcKillCreditIsOnlyASmallSupplement()
    {
        assertEquals(0L, NpcKillTracker.basePointsForCombatLevel(0));
        assertEquals(1L, NpcKillTracker.basePointsForCombatLevel(1));
        assertEquals(1L, NpcKillTracker.basePointsForCombatLevel(19));
        assertEquals(2L, NpcKillTracker.basePointsForCombatLevel(20));
        assertEquals(12L, NpcKillTracker.basePointsForCombatLevel(126));
        assertEquals(100L, NpcKillTracker.basePointsForCombatLevel(1_000));
    }
}
