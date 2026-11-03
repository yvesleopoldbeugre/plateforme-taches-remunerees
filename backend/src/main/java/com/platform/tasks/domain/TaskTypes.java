package com.platform.tasks.domain;

import java.util.Set;

/**
 * Deliberately a plain string allow-list, not a Java enum: the design dossier's whole point
 * (CDCF §2.1) is that new task types must be addable without re-architecting — adding one here
 * is a one-line change, never a schema migration (the {@code tasks.type} column is a plain
 * VARCHAR). The MVP ships exactly the two families validated in the CDCF: one digital
 * (form-based) and one field (geolocated photo) task.
 */
public final class TaskTypes {

    public static final String SURVEY = "SURVEY";
    public static final String FIELD_VISIT = "FIELD_VISIT";

    public static final Set<String> SUPPORTED = Set.of(SURVEY, FIELD_VISIT);

    private TaskTypes() {
    }
}
