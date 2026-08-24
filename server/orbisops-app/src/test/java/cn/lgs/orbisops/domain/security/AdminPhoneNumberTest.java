package cn.lgs.orbisops.domain.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminPhoneNumberTest {
    @ParameterizedTest
    @ValueSource(strings = {"13800138000", " 13800138000 ", "+8613800138000", "+86 13800138000", "8613800138000"})
    void normalizesOnlySupportedCountryCodeForms(String input) {
        assertEquals("13800138000", AdminPhoneNumber.required(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "12345678901", "1380013800", "138001380000", "+113800138000",
            "138 0013 8000", "138-0013-8000", "１３８００１３８０００", "13800138000 OR 1=1", "86+8613800138000"})
    void rejectsMalformedInputInsteadOfRemovingArbitraryCharacters(String input) {
        assertThrows(IllegalArgumentException.class, () -> AdminPhoneNumber.required(input));
    }

    @Test
    void missingPhoneDoesNotInventAnIdentityForLegacyAccounts() {
        assertNull(AdminPhoneNumber.optional(null));
        assertNull(AdminPhoneNumber.optional(" "));
        assertThrows(IllegalArgumentException.class, () -> AdminPhoneNumber.required(null));
    }
}
