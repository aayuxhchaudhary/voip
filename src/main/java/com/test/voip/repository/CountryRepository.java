package com.test.voip.repository;

import com.test.voip.entity.Country;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class CountryRepository {

    private static final String DEFAULT_COUNTRY_CODE = "+91";

    private final List<Country> countryList;

    public CountryRepository() {
        List<Country> list = new ArrayList<>();
        list.add(new Country("+91", "audio/IN.wav"));
        list.add(new Country("+1", "audio/US.wav"));
        list.add(new Country("+44", "audio/UK.wav"));
        list.add(new Country("+33", "audio/FR.wav"));
        list.add(new Country("+49", "audio/DE.wav"));
        list.add(new Country("+34", "audio/ES.wav"));
        list.add(new Country("+39", "audio/IT.wav"));
        list.add(new Country("+55", "audio/BR.wav"));
        list.add(new Country("+7", "audio/RU.wav"));
        list.add(new Country("+86", "audio/CN.wav"));
        list.add(new Country("+81", "audio/JP.wav"));
        list.add(new Country("+61", "audio/AU.wav"));
        list.add(new Country("+971", "audio/AE.wav"));

        list.sort((a, b) -> Integer.compare(b.getDialCode().length(), a.getDialCode().length()));
        this.countryList = Collections.unmodifiableList(list);
    }

    public String getSongPath(String dialCode) {
        String resolved = resolveDialCode(dialCode);
        return findByDialCode(resolved)
                .map(Country::getSongPath)
                .orElse("audio/IN.wav");
    }

    public String getDefaultCountryCode() {
        return DEFAULT_COUNTRY_CODE;
    }

    public String resolveDialCode(String dialCode) {
        if (dialCode == null || dialCode.trim().isEmpty()) {
            return DEFAULT_COUNTRY_CODE;
        }
        String cleaned = dialCode.trim().replace(" ", "");
        if (!cleaned.startsWith("+")) {
            cleaned = "+" + cleaned;
        }
        Optional<Country> match = findByDialCode(cleaned);
        if (match.isPresent()) {
            return match.get().getDialCode();
        }
        return DEFAULT_COUNTRY_CODE;
    }

    private Optional<Country> findByDialCode(String dialCode) {
        return countryList.stream()
                .filter(c -> dialCode.equals(c.getDialCode()) || dialCode.startsWith(c.getDialCode()))
                .findFirst();
    }
}
