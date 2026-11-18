package com.silentecho.silentecho_backend.dto;

import java.util.List;

/** The complaint categories accepted by the API. The frontend offers the same list. */
public final class CaseCategories {

    public static final List<String> ALL = List.of(
            "Bullying", "Harassment", "Cyberbullying", "Discrimination", "Emotional Distress",
            "Violence/Threat", "Theft", "Facilities", "Other");

    private CaseCategories() {
    }

    public static boolean isAllowed(String category) {
        return category != null && ALL.contains(category);
    }
}
