package com.osrssniffin.threechoices;

public class CombatProfile
{
    private final boolean ready;
    private final int attack;
    private final int strength;
    private final int defence;
    private final int ranged;
    private final int magic;
    private final int prayer;
    private final int hitpoints;
    private final int combatLevel;

    public CombatProfile(
        boolean ready,
        int attack,
        int strength,
        int defence,
        int ranged,
        int magic,
        int prayer,
        int hitpoints,
        int combatLevel)
    {
        this.ready = ready;
        this.attack = clampSkill(attack);
        this.strength = clampSkill(strength);
        this.defence = clampSkill(defence);
        this.ranged = clampSkill(ranged);
        this.magic = clampSkill(magic);
        this.prayer = clampSkill(prayer);
        this.hitpoints = clampSkill(hitpoints);
        this.combatLevel = Math.max(3, combatLevel);
    }

    public static CombatProfile unavailable()
    {
        return new CombatProfile(false, 1, 1, 1, 1, 1, 1, 10, 3);
    }

    public boolean isReady()
    {
        return ready;
    }

    public int getAttack()
    {
        return attack;
    }

    public int getStrength()
    {
        return strength;
    }

    public int getDefence()
    {
        return defence;
    }

    public int getRanged()
    {
        return ranged;
    }

    public int getMagic()
    {
        return magic;
    }

    public int getPrayer()
    {
        return prayer;
    }

    public int getHitpoints()
    {
        return hitpoints;
    }

    public int getCombatLevel()
    {
        return combatLevel;
    }

    public int highestOffensiveSkill()
    {
        return Math.max(attack, Math.max(ranged, magic));
    }

    /**
     * Continuous account capacity used by the choice matcher. This is intentionally
     * not a tier. Weapons follow their offensive skill; major armour is anchored
     * by Defence as well as the style it supports; accessories follow the account's
     * strongest combat path.
     */
    public int capacityFor(CombatStyle style, int slot)
    {
        int styleLevel;
        switch (style)
        {
            case RANGED:
                styleLevel = ranged;
                break;
            case MAGIC:
                styleLevel = magic;
                break;
            case MELEE:
                styleLevel = attack;
                break;
            case GENERAL:
            default:
                styleLevel = highestOffensiveSkill();
                break;
        }

        if (slot == 3) // weapon
        {
            return earlyFloor(styleLevel);
        }

        // Head/body/shield/legs are the slots most often gated by Defence.
        if (slot == 0 || slot == 4 || slot == 5 || slot == 7)
        {
            if (style == CombatStyle.GENERAL)
            {
                return earlyFloor(defence);
            }
            return earlyFloor(Math.min(styleLevel, defence + 20));
        }

        return earlyFloor(Math.max(defence, styleLevel));
    }

    private static int earlyFloor(int level)
    {
        // Keeps brand-new accounts from producing an empty pool while remaining
        // far below mid/high-level equipment.
        return Math.max(10, clampSkill(level));
    }

    private static int clampSkill(int level)
    {
        return Math.max(1, Math.min(99, level));
    }
}
