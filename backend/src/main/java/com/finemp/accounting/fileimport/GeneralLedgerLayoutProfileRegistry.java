package com.finemp.accounting.fileimport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class GeneralLedgerLayoutProfileRegistry {
    private final Map<String, GeneralLedgerLayoutProfile> profiles;

    public GeneralLedgerLayoutProfileRegistry(List<GeneralLedgerLayoutProfile> available) {
        var registered = new LinkedHashMap<String, GeneralLedgerLayoutProfile>();

        for (var profile : available) {
            Objects.requireNonNull(profile, "profile");
            String id = profile.id();
            if (id == null || !id.matches("[a-z0-9][a-z0-9_-]{0,79}")
                    || profile.formats() == null || profile.formats().isEmpty()
                    || profile.formats().stream().anyMatch(Objects::isNull)) {
                throw new IllegalStateException("Invalid accounting file layout profile.");
            }

            if (registered.putIfAbsent(id, profile) != null) {
                throw new IllegalStateException("Duplicate accounting file layout profile.");
            }
        }

        profiles = Map.copyOf(registered);
    }

    public GeneralLedgerLayoutProfile require(String profileId, AccountingFileFormat format) {
        if (profileId == null || format == null) {
            throw new AccountingFileLayoutUnavailableException();
        }

        var profile = profiles.get(profileId);
        if (profile == null || !profile.formats().contains(format)) {
            throw new AccountingFileLayoutUnavailableException();
        }
        return profile;
    }
}
