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
            boolean uniqueLocal = (bytes[0] & 0xfe) == 0xfc;
            boolean mappedV4 = Arrays.equals(Arrays.copyOf(bytes, 12),
                    new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff});
            if (mappedV4) {
                try {
                    return notPublic(InetAddress.getByAddress(Arrays.copyOfRange(bytes, 12, 16)));
                } catch (UnknownHostException impossible) {
                    return true;
                }
            }
            return uniqueLocal;
        }
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return first == 0 || first == 100 && second >= 64 && second <= 127 || first == 198 && (second == 18
                || second == 19) || first >= 240;
    }

    private static URI withoutFragment(URI uri) {
        try {
            return new URI(uri.getScheme(), uri.getRawAuthority(), uri.getRawPath(), uri.getRawQuery(), null);
        } catch (URISyntaxException impossible) {
            return uri;
        }
    }
}
