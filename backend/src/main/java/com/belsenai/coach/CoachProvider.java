package com.belsenai.coach;

import java.util.List;

/** Provider returns identifiers only. Java retains all facts, actions and explanations. */
public interface CoachProvider {
    default void validate(CoachEvidence.Snapshot evidence) { }
    List<String> rank(CoachEvidence.Snapshot evidence);
}
