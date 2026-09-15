package com.osrssniffin.threechoices;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.FakeXpDrop;
import net.runelite.api.events.StatChanged;

@Singleton
public class PointAwardService
{
    private static final int LOGIN_SETTLE_TICKS = 3;
    private static final int FAKE_XP_SANITY_CAP = 20_000_000;

    // Continuous XP income tapers by completed Three Choices rolls. The first
    // choice arrives quickly enough to hook a fresh account, while established
    // accounts need increasingly more gameplay for each later choice.
    private static final long FIRST_ROLL_POINTS_PER_1000_XP = 500L;
    private static final long ROLLS_1_TO_2_POINTS_PER_1000_XP = 350L;
    private static final long ROLLS_3_TO_5_POINTS_PER_1000_XP = 225L;
    private static final long ROLLS_6_TO_10_POINTS_PER_1000_XP = 125L;
    private static final long ROLLS_11_PLUS_POINTS_PER_1000_XP = 60L;
    private static final double COMBAT_POINT_MULTIPLIER = 0.20d;

    // Level-ups remain a tiny milestone bonus, but never drive the economy.
    // Combat keeps the same 20% relationship as continuous combat XP.
    private static final long SKILLING_LEVEL_UP_BONUS = 10L;
    private static final long COMBAT_LEVEL_UP_BONUS = 2L;

    private static final Set<Skill> COMBAT_SKILLS = EnumSet.of(
        Skill.ATTACK,
        Skill.DEFENCE,
        Skill.STRENGTH,
        Skill.HITPOINTS,
        Skill.MAGIC,
        Skill.RANGED
    );

    private final Client client;
    private final ThreeChoicesStateService stateService;
    private final ItemAccessService itemAccessService;
    private final int[] previousXp = new int[Skill.values().length];

    private boolean initialized;
    private int initializeAtTick;

    @Inject
    public PointAwardService(
        Client client,
        ThreeChoicesStateService stateService,
        ItemAccessService itemAccessService)
    {
        this.client = client;
        this.stateService = stateService;
        this.itemAccessService = itemAccessService;
    }

    public void resetForLoginOrHop()
    {
        initialized = false;
        Arrays.fill(previousXp, 0);
        initializeAtTick = client.getTickCount() + LOGIN_SETTLE_TICKS;
    }

    public void onGameTick()
    {
        if (initialized || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        int tick = client.getTickCount();
        if (tick < initializeAtTick && initializeAtTick - tick <= LOGIN_SETTLE_TICKS)
        {
            return;
        }

        int[] liveXp = client.getSkillExperiences();
        if (liveXp == null)
        {
            return;
        }

        System.arraycopy(liveXp, 0, previousXp, 0, Math.min(liveXp.length, previousXp.length));
        initialized = true;
    }

    public boolean onStatChanged(StatChanged event)
    {
        if (!initialized || event == null || event.getSkill() == null)
        {
            return false;
        }

        Skill skill = event.getSkill();
        int index = skill.ordinal();
        if (index < 0 || index >= previousXp.length)
        {
            return false;
        }

        int currentXp = Math.max(0, event.getXp());
        int oldXp = previousXp[index];
        if (currentXp <= oldXp)
        {
            return false;
        }

        // Always advance the baseline before any gating decision so a locked
        // skill, login artifact, or one-off XP source can never be paid later.
        previousXp[index] = currentXp;

        SkillGate gatedSkill = SkillGate.fromSkill(skill);
        if (gatedSkill != null && !itemAccessService.isSkillUnlocked(gatedSkill))
        {
            return false;
        }

        // Hitpoints XP is generated alongside ordinary combat damage XP. The
        // attack/strength/defence/ranged/magic XP already represents that action.
        if (skill == Skill.HITPOINTS)
        {
            return false;
        }

        boolean combat = COMBAT_SKILLS.contains(skill);
        long before = stateService.getPoints();
        long xpGained = (long) currentXp - oldXp;

        int completedRolls = stateService.getRollHistory().size();
        long skillingRate = pointsPer1000XpForCompletedRolls(completedRolls);
        long effectiveRate = combat
            ? combatPointsPer1000Xp(skillingRate)
            : skillingRate;

        // ThreeChoicesStateService settles this fractionally, so even tiny XP
        // actions contribute immediately without needing a 1,000-XP threshold.
        stateService.applyNonCombatXp(xpGained, effectiveRate);

        int oldLevel = Experience.getLevelForXp(oldXp);
        int newLevel = Experience.getLevelForXp(currentXp);
        if (newLevel > oldLevel)
        {
            long bonusPerLevel = combat ? COMBAT_LEVEL_UP_BONUS : SKILLING_LEVEL_UP_BONUS;
            stateService.addEarnedPoints((long) (newLevel - oldLevel) * bonusPerLevel);
        }

        return stateService.getPoints() != before;
    }

    public boolean onFakeXpDrop(FakeXpDrop event)
    {
        if (!initialized || event == null || event.getSkill() == null)
        {
            return false;
        }

        Skill skill = event.getSkill();
        SkillGate gatedSkill = SkillGate.fromSkill(skill);
        if ((gatedSkill != null && !itemAccessService.isSkillUnlocked(gatedSkill))
            || COMBAT_SKILLS.contains(skill)
            || client.getSkillExperience(skill) < Experience.MAX_SKILL_XP)
        {
            return false;
        }

        int xp = event.getXp();
        if (xp <= 0 || xp >= FAKE_XP_SANITY_CAP)
        {
            return false;
        }

        long skillingRate = pointsPer1000XpForCompletedRolls(stateService.getRollHistory().size());
        stateService.applyNonCombatXp(xp, skillingRate);
        return true;
    }

    static long pointsPer1000XpForCompletedRolls(int completedRolls)
    {
        int rolls = Math.max(0, completedRolls);
        if (rolls == 0)
        {
            return FIRST_ROLL_POINTS_PER_1000_XP;
        }
        if (rolls <= 2)
        {
            return ROLLS_1_TO_2_POINTS_PER_1000_XP;
        }
        if (rolls <= 5)
        {
            return ROLLS_3_TO_5_POINTS_PER_1000_XP;
        }
        if (rolls <= 10)
        {
            return ROLLS_6_TO_10_POINTS_PER_1000_XP;
        }
        return ROLLS_11_PLUS_POINTS_PER_1000_XP;
    }

    static long combatPointsPer1000Xp(long skillingRate)
    {
        return Math.max(1L, Math.round(Math.max(0L, skillingRate) * COMBAT_POINT_MULTIPLIER));
    }
}
