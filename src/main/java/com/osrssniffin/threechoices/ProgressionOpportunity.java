package com.osrssniffin.threechoices;

import java.util.List;

/** One account-state progression direction, before a final item is chosen. */
public final class ProgressionOpportunity
{
    private final ProgressionOpportunityType type;
    private final String key;
    private final String decisionGroup;
    private final String reason;
    private final SkillGate skillGate;
    private final int weight;
    private final List<RollCandidate> fixedCandidates;

    ProgressionOpportunity(
        ProgressionOpportunityType type,
        String key,
        String decisionGroup,
        String reason,
        SkillGate skillGate,
        int weight,
        List<RollCandidate> fixedCandidates)
    {
        this.type = type;
        this.key = key;
        this.decisionGroup = decisionGroup;
        this.reason = reason;
        this.skillGate = skillGate;
        this.weight = Math.max(1, weight);
        this.fixedCandidates = fixedCandidates == null ? List.of() : List.copyOf(fixedCandidates);
    }

    static ProgressionOpportunity combat(ProgressionOpportunityType type, String reason)
    {
        return new ProgressionOpportunity(
            type,
            type.name(),
            "COMBAT:" + type.name(),
            reason,
            null,
            type.getBaseWeight(),
            List.of());
    }

    static ProgressionOpportunity curated(
        ProgressionOpportunityType type,
        SkillGate gate,
        String suffix,
        String reason,
        List<RollCandidate> candidates)
    {
        String gateKey = gate == null ? "GENERAL" : gate.name();
        String stableSuffix = suffix == null || suffix.isBlank() ? type.name() : suffix;
        return new ProgressionOpportunity(
            type,
            type.name() + ":" + gateKey + ":" + stableSuffix,
            type.name() + ":" + gateKey,
            reason,
            gate,
            type.getBaseWeight(),
            candidates);
    }

    static ProgressionOpportunity special(String key, String reason, List<RollCandidate> candidates)
    {
        return new ProgressionOpportunity(
            ProgressionOpportunityType.SPECIAL,
            "SPECIAL:" + key,
            "SPECIAL:" + key,
            reason,
            null,
            ProgressionOpportunityType.SPECIAL.getBaseWeight(),
            candidates);
    }

    public ProgressionOpportunityType getType()
    {
        return type;
    }

    public String getKey()
    {
        return key;
    }

    public String getDecisionGroup()
    {
        return decisionGroup;
    }

    public String getReason()
    {
        return reason;
    }

    public SkillGate getSkillGate()
    {
        return skillGate;
    }

    public int getWeight()
    {
        return weight;
    }

    public List<RollCandidate> getFixedCandidates()
    {
        return fixedCandidates;
    }

    public boolean hasFixedCandidates()
    {
        return !fixedCandidates.isEmpty();
    }
}
