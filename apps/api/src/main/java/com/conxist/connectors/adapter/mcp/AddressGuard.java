package com.conxist.connectors.adapter.mcp;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import com.conxist.connectors.application.McpGateway;

/**
 * Lets ConXist call only https addresses on the public internet, so an address a person types (or a server's metadata
 * names) can't reach this computer or a private network. With {@code allowLoopback}, only for tests and the local
 * fake, plain http on this computer is let through too; other private addresses never are.
 */
final class AddressGuard {

    static final int MAX_ADDRESS_LENGTH = 2_000;

    private final boolean allowLoopback;

    AddressGuard(boolean allowLoopback) {
        this.allowLoopback = allowLoopback;
    }

    URI check(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_ADDRESS_LENGTH) {
            throw new McpGateway.AddressRefused("address-invalid");
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException unreadable) {
            throw new McpGateway.AddressRefused("address-invalid");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!uri.isAbsolute() || uri.getHost() == null || uri.getRawUserInfo() != null) {
            throw new McpGateway.AddressRefused("address-invalid");
        }
        if (!(scheme.equals("https") || scheme.equals("http"))) {
            throw new McpGateway.AddressRefused("address-not-https");
        }
        if (!allowLoopback && !scheme.equals("https")) {
            throw new McpGateway.AddressRefused("address-not-https");
        }
        InetAddress[] addresses = resolve(uri.getHost());
        boolean onThisComputer = Arrays.stream(addresses).allMatch(InetAddress::isLoopbackAddress);
        if (allowLoopback && onThisComputer) {
            return withoutFragment(uri);
        }
        if (!scheme.equals("https")) {
            throw new McpGateway.AddressRefused("address-not-https");
        }
        if (Arrays.stream(addresses).anyMatch(AddressGuard::notPublic)) {
            throw new McpGateway.AddressRefused("address-private");
        }
        return withoutFragment(uri);
    }

    private static InetAddress[] resolve(String host) {
        try {
            return InetAddress.getAllByName(host.startsWith("[") ? host.substring(1, host.length() - 1) : host);
        } catch (UnknownHostException unknown) {
            throw new McpGateway.Unreachable("unknown host");
        }
    }

    /** Loopback, private, link-local (cloud metadata), carrier-grade NAT, multicast and unspecified addresses. */
    static boolean notPublic(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet6Address) {
            // Only global unicast (2000::/3) is public, and some of it carries an IPv4 address that must pass too.
            if ((bytes[0] & 0xe0) != 0x20) {
                return !embeddedV4Public(bytes);
            }
            int high = ((bytes[0] & 0xff) << 8) | (bytes[1] & 0xff);
            if (high == 0x2002) {
                return notPublic(v4(Arrays.copyOfRange(bytes, 2, 6)));
            }
            if (high == 0x2001 && bytes[2] == 0 && bytes[3] == 0) {
                return true;
            }
            return false;
        }
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return first == 0 || first == 100 && second >= 64 && second <= 127 || first == 198 && (second == 18
                || second == 19) || first == 192 && second == 0 && bytes[2] == 0 || first >= 240;
    }

    /**
     * Outside 2000::/3 the only addresses that reach the internet carry an IPv4 one: IPv4-mapped (::ffff:0:0/96) and
     * NAT64 (64:ff9b::/96). They are as public as the address they carry; everything else there is not.
     */
    private static boolean embeddedV4Public(byte[] bytes) {
        boolean mapped = Arrays.equals(Arrays.copyOf(bytes, 12), new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff,
                (byte) 0xff});
        boolean nat64 = Arrays.equals(Arrays.copyOf(bytes, 12), new byte[] {0, 0x64, (byte) 0xff, (byte) 0x9b, 0, 0, 0, 0,
                0, 0, 0, 0});
        return (mapped || nat64) && !notPublic(v4(Arrays.copyOfRange(bytes, 12, 16)));
    }

    private static InetAddress v4(byte[] bytes) {
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static URI withoutFragment(URI uri) {
        try {
            return new URI(uri.getScheme(), uri.getRawAuthority(), uri.getRawPath(), uri.getRawQuery(), null);
        } catch (URISyntaxException impossible) {
            return uri;
        }
    }
}
