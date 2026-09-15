package com.osrssniffin.threechoices;

public class RollCandidate
{
    private final int itemId;
    private final String name;
    private final long price;
    private final int slot;
    private final CombatStyle style;
    private final RollCategory category;
    private final SkillGate skillGate;
    private final int progressionScore;
    private final int accountCapacity;

    public RollCandidate(
        int itemId,
        String name,
        long price,
        int slot,
        CombatStyle style,
        int progressionScore,
        int accountCapacity)
    {
        this(itemId, name, price, slot, style, RollCategory.PVM, null, progressionScore, accountCapacity);
    }

    public RollCandidate(
        int itemId,
        String name,
        long price,
        int slot,
        CombatStyle style,
        RollCategory category,
        int progressionScore,
        int accountCapacity)
    {
        this(itemId, name, price, slot, style, category, null, progressionScore, accountCapacity);
    }

    public RollCandidate(
        int itemId,
        String name,
        long price,
        int slot,
        CombatStyle style,
        RollCategory category,
        SkillGate skillGate,
        int progressionScore,
        int accountCapacity)
    {
        this.itemId = itemId;
        this.name = name;
        this.price = price;
        this.slot = slot;
        this.style = style;
        this.category = category == null ? RollCategory.PVM : category;
        this.skillGate = skillGate;
        this.progressionScore = progressionScore;
        this.accountCapacity = accountCapacity;
    }

    public int getItemId() { return itemId; }
    public String getName() { return name; }
    public long getPrice() { return price; }
    public int getSlot() { return slot; }
    public CombatStyle getStyle() { return style; }
    public RollCategory getCategory() { return category; }
    public SkillGate getSkillGate() { return skillGate; }
    public boolean isSkilling() { return category.isSkilling(); }
    public int getProgressionScore() { return progressionScore; }
    public int getAccountCapacity() { return accountCapacity; }
    public int getFitDelta() { return progressionScore - accountCapacity; }
}
