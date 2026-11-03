package com.platform.tasks.domain;

import java.util.Set;

/** Same rationale as {@link TaskTypes}: an open allow-list, not a Java enum (DAT §4.2). */
public final class ProofTypes {

    public static final String TEXT = "TEXT";
    public static final String IMAGE = "IMAGE";
    public static final String DOCUMENT = "DOCUMENT";
    public static final String GPS_PHOTO = "GPS_PHOTO";
    public static final String FORM = "FORM";

    public static final Set<String> SUPPORTED = Set.of(TEXT, IMAGE, DOCUMENT, GPS_PHOTO, FORM);

    private ProofTypes() {
    }
}
