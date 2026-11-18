package com.silentecho.silentecho_backend.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrganisationNameTest {

    @Test
    void normalizesCaseAndRepeatedWhitespaceForDuplicateDetection() {
        assertEquals("abes engineering college",
                Organisation.normalizeName("  ABES   Engineering\tCollege  "));
        assertEquals(Organisation.normalizeName("ABES Engineering College"),
                Organisation.normalizeName("abes engineering college"));
    }

    @Test
    void displayNameTrimsAndCollapsesWhitespaceWithoutChangingReadableCase() {
        assertEquals("ABES Engineering College",
                Organisation.displayName("  ABES   Engineering\nCollege  "));
    }
}
