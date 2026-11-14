package fr.oreostudios.monique.pods;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AddressValidatorTest {
    @Test
    void validatesIpLiteralsOnly() {
        assertEquals("192.0.2.1", AddressValidator.ip("192.0.2.1"));
        assertEquals("2001:db8::1", AddressValidator.ip("2001:db8::1"));
        assertThrows(IllegalArgumentException.class, () -> AddressValidator.ip("999.4.4.4"));
        assertThrows(IllegalArgumentException.class, () -> AddressValidator.ip("example.com"));
        assertThrows(IllegalArgumentException.class, () -> AddressValidator.ip("127.0.0.1 ; rm -rf /"));
    }
    @Test
    void validatesRoles() {
        assertEquals("ALLOCATION", AddressValidator.role("allocation"));
        assertThrows(IllegalArgumentException.class, () -> AddressValidator.role("internet"));
    }
}
