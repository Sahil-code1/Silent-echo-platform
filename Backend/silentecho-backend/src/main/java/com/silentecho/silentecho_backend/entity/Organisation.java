package com.silentecho.silentecho_backend.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "organisations")
public class Organisation {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 120)
    private String name;

    /** Canonical, whitespace-collapsed and case-folded key used to prevent duplicate workspaces. */
    @Column(name = "name_key", nullable = false, unique = true, length = 120)
    private String nameKey;

    /** Stable, human-readable public identifier. It is not a login credential. */
    @Column(name = "organisation_code", nullable = false, unique = true, length = 20)
    private String organisationCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private OrganisationType type;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Organisation() {
    }

    public Organisation(String id, String name, OrganisationType type, String organisationCode, Instant createdAt) {
        this.id = id;
        this.name = displayName(name);
        this.nameKey = normalizeName(name);
        this.organisationCode = organisationCode;
        this.type = type;
        this.createdAt = createdAt;
    }

    public static String displayName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    public static String normalizeName(String value) {
        return displayName(value).toLowerCase(Locale.ROOT);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getNameKey() { return nameKey; }
    public String getOrganisationCode() { return organisationCode; }
    public OrganisationType getType() { return type; }
    public Instant getCreatedAt() { return createdAt; }
}
