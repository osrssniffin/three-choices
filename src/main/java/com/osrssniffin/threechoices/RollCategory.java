package com.osrssniffin.threechoices;

public enum RollCategory
{
    PVM("PvM gear", false),
    SKILLING("Skilling", true),
    SKILL_METHOD("Training method", true),
    CLUE("Treasure Trail", false),
    BUNDLE("Special unlock", false);

    private final String displayName;
    private final boolean skilling;

    RollCategory(String displayName, boolean skilling)
    {
        this.displayName = displayName;
        this.skilling = skilling;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public boolean isSkilling()
    {
        return skilling;
    }

    public boolean isShiny()
    {
        return this == CLUE || this == BUNDLE;
    }
}
