package com.osrssniffin.threechoices;

/** A selected progression direction resolved into the concrete roll choice. */
final class ResolvedProgressionChoice
{
    final ProgressionOpportunity opportunity;
    final RollCandidate candidate;

    ResolvedProgressionChoice(ProgressionOpportunity opportunity, RollCandidate candidate)
    {
        this.opportunity = opportunity;
        this.candidate = candidate;
    }

    String progressionReason()
    {
        return opportunity.getReason();
    }
}
