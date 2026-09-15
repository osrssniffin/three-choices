package com.osrssniffin.threechoices;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;

@Singleton
public class NpcKillTracker
{
    private static final int INTERACTION_TIMEOUT_TICKS = 7;

    private final Client client;
    private final ThreeChoicesStateService stateService;
    private final ProgressionEconomyService economyService;
    private final Map<Integer, Integer> lastEngagedTick = new HashMap<>();

    @Inject
    public NpcKillTracker(
        Client client,
        ThreeChoicesStateService stateService,
        ProgressionEconomyService economyService)
    {
        this.client = client;
        this.stateService = stateService;
        this.economyService = economyService;
    }

    public void reset()
    {
        lastEngagedTick.clear();
    }

    public void onHitsplatApplied(HitsplatApplied event)
    {
        if (event == null || !(event.getActor() instanceof NPC))
        {
            return;
        }

        Hitsplat hitsplat = event.getHitsplat();
        if (hitsplat != null && hitsplat.isMine() && hitsplat.getAmount() > 0)
        {
            markEngaged((NPC) event.getActor());
        }
    }

    public boolean onActorDeath(ActorDeath event)
    {
        if (event == null || !(event.getActor() instanceof NPC))
        {
            return false;
        }

        NPC npc = (NPC) event.getActor();
        Integer engagedTick = lastEngagedTick.remove(npc.getIndex());
        if (engagedTick == null || npc.getCombatLevel() <= 0)
        {
            return false;
        }

        if (client.getTickCount() - engagedTick > INTERACTION_TIMEOUT_TICKS || isMessageTrackedActivityNpc(npc.getId()))
        {
            return false;
        }

        stateService.addEarnedPoints(economyService.scaleAward(basePointsForCombatLevel(npc.getCombatLevel())));
        return true;
    }

    public void onNpcDespawned(NpcDespawned event)
    {
        if (event != null && event.getNpc() != null)
        {
            lastEngagedTick.remove(event.getNpc().getIndex());
        }
    }

    private void markEngaged(NPC npc)
    {
        if (npc != null && npc.getCombatLevel() > 0)
        {
            lastEngagedTick.put(npc.getIndex(), client.getTickCount());
        }
    }

    static long basePointsForCombatLevel(int combatLevel)
    {
        // Combat XP already earns points. Kill credit is only a small extra nudge,
        // not a second primary economy. Requiring ten combat levels per base point
        // keeps bosses meaningful without making tagged kills a roll farm.
        return combatLevel <= 0 ? 0L : Math.max(1L, combatLevel / 10L);
    }

    /**
     * Activities paid as one flat completion reward are excluded here so their
     * internal monsters cannot also pay per-kill points.
     */
    private static boolean isMessageTrackedActivityNpc(int npcId)
    {
        // Great Olm forms.
        if (npcId >= 7550 && npcId <= 7555)
        {
            return true;
        }
        // Fight Caves.
        if (npcId >= 3116 && npcId <= 3128)
        {
            return true;
        }
        // Inferno combat NPCs.
        if (npcId >= 7691 && npcId <= 7708 && npcId != 7707)
        {
            return true;
        }
        // Theatre of Blood normal and entry/hard NPC blocks.
        if ((npcId >= 8338 && npcId <= 8389) || (npcId >= 10766 && npcId <= 10869))
        {
            return true;
        }
        // Tombs of Amascut combat NPC block.
        if (npcId >= 11697 && npcId <= 11799)
        {
            return true;
        }
        // Gauntlet / Corrupted Gauntlet creatures and Hunllef forms.
        return npcId >= 9021 && npcId <= 9048;
    }
}
