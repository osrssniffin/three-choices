package com.osrssniffin.threechoices;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Shared point-economy rules that sit outside ordinary XP and quest rewards.
 * Paid rolls always cost 3,000 points. Bonus sources such as PvM completions
 * taper with completed rolls so they stay useful without overtaking the main
 * play-to-earn loop.
 */
@Singleton
public class ProgressionEconomyService
{
    static final long ROLL_COST = 3_000L;

    private static final double BONUS_MULTIPLIER_0_ROLLS = 1.00d;
    private static final double BONUS_MULTIPLIER_1_TO_2_ROLLS = 0.85d;
    private static final double BONUS_MULTIPLIER_3_TO_5_ROLLS = 0.70d;
    private static final double BONUS_MULTIPLIER_6_TO_10_ROLLS = 0.55d;
    private static final double BONUS_MULTIPLIER_11_PLUS_ROLLS = 0.40d;

    private final ThreeChoicesStateService stateService;

    @Inject
    public ProgressionEconomyService(ThreeChoicesStateService stateService)
    {
        this.stateService = stateService;
    }

    public long getRollCost()
    {
        return ROLL_COST;
    }

    /** Apply the completed-roll taper to non-XP, non-quest bonus awards. */
    public long scaleAward(long basePoints)
    {
        if (basePoints <= 0L)
        {
            return 0L;
        }

        double multiplier = bonusMultiplierForCompletedRolls(stateService.getRollHistory().size());
        double scaled = basePoints * multiplier;
        if (scaled >= Long.MAX_VALUE)
        {
            return Long.MAX_VALUE;
        }
        return Math.max(1L, Math.round(scaled));
    }

    static double bonusMultiplierForCompletedRolls(int completedRolls)
    {
        int rolls = Math.max(0, completedRolls);
        if (rolls == 0) return BONUS_MULTIPLIER_0_ROLLS;
        if (rolls <= 2) return BONUS_MULTIPLIER_1_TO_2_ROLLS;
        if (rolls <= 5) return BONUS_MULTIPLIER_3_TO_5_ROLLS;
        if (rolls <= 10) return BONUS_MULTIPLIER_6_TO_10_ROLLS;
        return BONUS_MULTIPLIER_11_PLUS_ROLLS;
    }
}
