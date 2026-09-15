package com.osrssniffin.threechoices;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class QuestRewardBalanceTest
{
    @Test
    public void normalQuestPointRateTapersByCompletedRolls()
    {
        assertEquals(100L, QuestRewardService.normalPointsPerQuestPoint(0));
        assertEquals(85L, QuestRewardService.normalPointsPerQuestPoint(1));
        assertEquals(85L, QuestRewardService.normalPointsPerQuestPoint(2));
        assertEquals(70L, QuestRewardService.normalPointsPerQuestPoint(3));
        assertEquals(70L, QuestRewardService.normalPointsPerQuestPoint(5));
        assertEquals(55L, QuestRewardService.normalPointsPerQuestPoint(6));
        assertEquals(55L, QuestRewardService.normalPointsPerQuestPoint(10));
        assertEquals(40L, QuestRewardService.normalPointsPerQuestPoint(11));
        assertEquals(100L, QuestRewardService.normalPointsPerQuestPoint(-1));
    }

    @Test
    public void explicitGrandmastersKeepFullQuestPointBonus()
    {
        assertEquals(1_000L, QuestRewardService.grandmasterTotalReward("Monkey Madness II"));
        assertEquals(1_250L, QuestRewardService.grandmasterTotalReward("Dragon Slayer II"));
        assertEquals(1_000L, QuestRewardService.grandmasterTotalReward("Song of the Elves"));
        assertEquals(1_250L, QuestRewardService.grandmasterTotalReward("Desert Treasure II - The Fallen Empire"));
        assertEquals(1_250L, QuestRewardService.grandmasterTotalReward("While Guthix Sleeps"));
        assertEquals(1_000L, QuestRewardService.grandmasterTotalReward("The Blood Moon Rises"));
        assertEquals(0L, QuestRewardService.grandmasterTotalReward("Cook's Assistant"));
    }
}
