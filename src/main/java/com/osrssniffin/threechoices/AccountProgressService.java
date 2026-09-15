package com.osrssniffin.threechoices;

import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;

@Singleton
public class AccountProgressService
{
    private final Client client;
    private final int[] liveLevels = new int[Skill.values().length];
    private volatile CombatProfile liveSnapshot = CombatProfile.unavailable();
    private volatile boolean loggedIn;

    @Inject
    public AccountProgressService(Client client)
    {
        this.client = client;
        Arrays.fill(liveLevels, 1);
    }

    /** Call from the RuneLite client thread. */
    public void refreshFromClient()
    {
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
        {
            loggedIn = false;
            liveSnapshot = CombatProfile.unavailable();
            return;
        }

        loggedIn = true;
        for (Skill skill : Skill.values())
        {
            liveLevels[skill.ordinal()] = Math.max(1, client.getRealSkillLevel(skill));
        }

        liveSnapshot = new CombatProfile(
            true,
            getLive(Skill.ATTACK),
            getLive(Skill.STRENGTH),
            getLive(Skill.DEFENCE),
            getLive(Skill.RANGED),
            getLive(Skill.MAGIC),
            getLive(Skill.PRAYER),
            getLive(Skill.HITPOINTS),
            client.getLocalPlayer().getCombatLevel());
    }

    public void clear()
    {
        loggedIn = false;
        liveSnapshot = CombatProfile.unavailable();
    }

    public CombatProfile getSnapshot()
    {
        return loggedIn ? liveSnapshot : CombatProfile.unavailable();
    }

    public int getLevel(SkillGate gate)
    {
        return gate == null ? 1 : getLevel(gate.getSkill());
    }

    public int getLevel(Skill skill)
    {
        return skill == null ? 1 : getLive(skill);
    }

    public int getWoodcuttingLevel() { return getLevel(Skill.WOODCUTTING); }
    public int getMiningLevel() { return getLevel(Skill.MINING); }
    public int getFishingLevel() { return getLevel(Skill.FISHING); }

    public int getNonCombatAverage()
    {
        int total = 0;
        int count = 0;
        for (SkillGate gate : SkillGate.values())
        {
            total += getLevel(gate);
            count++;
        }
        return count == 0 ? 1 : Math.max(1, total / count);
    }

    private int getLive(Skill skill)
    {
        int index = skill.ordinal();
        return index >= 0 && index < liveLevels.length ? Math.max(1, liveLevels[index]) : 1;
    }
}
