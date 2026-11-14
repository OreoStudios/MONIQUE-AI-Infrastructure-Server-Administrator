package fr.oreostudios.monique.pods;

import java.net.InetAddress;
import java.util.Locale;
import java.util.Set;

public final class AddressValidator {
    private static final Set<String> ROLES = Set.of("PRIMARY", "PUBLIC", "PRIVATE", "MANAGEMENT", "ALLOCATION");
    private AddressValidator() { }

    public static String role(String role) {
        if (role == null || !ROLES.contains(role.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Invalid address role");
        }
        return role.toUpperCase(Locale.ROOT);
    }

    public static String ip(String value) {
        if (value == null || value.length() > 60 || value.isBlank()) {
            throw new IllegalArgumentException("Invalid IP address");
        }
        if (value.contains(":")) {
            if (!value.matches("[0-9a-fA-F:.]+")) throw new IllegalArgumentException("Invalid IPv6");
        } else {
            String[] blocks = value.split("\\.", -1);
            if (blocks.length != 4) throw new IllegalArgumentException("Invalid IPv4");
            for (String block : blocks) {
                if (block.isBlank() || block.length() > 3 || !block.matches("[0-9]+")) {
                    throw new IllegalArgumentException("Invalid IPv4");
                }
                int part = Integer.parseInt(block);
                if (part < 0 || part > 255) throw new IllegalArgumentException("Invalid IPv4");
            }
        }
        try {
            InetAddress.getByName(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid IP literal");
        }
        return value;
    }
}
