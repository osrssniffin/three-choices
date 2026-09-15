package com.osrssniffin.threechoices;

public enum CombatStyle
{
    MELEE("Melee"),
    RANGED("Ranged"),
    MAGIC("Magic"),
    GENERAL("General");

    private final String displayName;

    CombatStyle(String displayName)
    {
        this.displayName = displayName;
    }

    public String getDisplayName()
    {
        return displayName;
    }
}
