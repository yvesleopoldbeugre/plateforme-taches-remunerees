package com.platform.validation.domain;

import java.util.Set;

/**
 * BR-031 — "les motifs de rejet sont standardisés (liste fermée)". The design dossier names
 * the rule but never enumerates concrete values; this is where that gap is closed. Same
 * open-but-centralized pattern as {@code TaskTypes}/{@code ProofTypes}: a plain string
 * allow-list, not a JPA enum, so the set can grow without a migration.
 */
public final class RejectionReasons {

    public static final String INCOMPLETE_SUBMISSION = "INCOMPLETE_SUBMISSION";
    public static final String PROOF_DOES_NOT_MATCH_INSTRUCTIONS = "PROOF_DOES_NOT_MATCH_INSTRUCTIONS";
    public static final String ILLEGIBLE_OR_UNUSABLE_PROOF = "ILLEGIBLE_OR_UNUSABLE_PROOF";
    public static final String SUSPECTED_FRAUD = "SUSPECTED_FRAUD";
    public static final String OUT_OF_SCOPE = "OUT_OF_SCOPE";
    public static final String OTHER = "OTHER";

    public static final Set<String> SUPPORTED = Set.of(
            INCOMPLETE_SUBMISSION, PROOF_DOES_NOT_MATCH_INSTRUCTIONS, ILLEGIBLE_OR_UNUSABLE_PROOF,
            SUSPECTED_FRAUD, OUT_OF_SCOPE, OTHER);

    private RejectionReasons() {
    }
}
