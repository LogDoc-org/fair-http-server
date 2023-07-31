package org.logdoc.fairhttp.service.tools.websocket.protocol;

import java.util.regex.Pattern;

import static org.logdoc.helpers.Texts.isEmpty;

public class Protocol implements IProtocol {
    private static final Pattern patternSpace = Pattern.compile(" "), patternComma = Pattern.compile(",");

    private final String providedProtocol;

    public Protocol(final String providedProtocol) {
        if (providedProtocol == null)
            throw new IllegalArgumentException();

        this.providedProtocol = providedProtocol;
    }

    @Override
    public boolean acceptProtocol(final String input) {
        if (isEmpty(providedProtocol))
            return true;

        final String[] headers = patternComma.split(patternSpace.matcher(input).replaceAll(""));

        for (String header : headers)
            if (providedProtocol.equals(header))
                return true;

        return false;
    }

    @Override
    public String getProvidedProtocol() {
        return this.providedProtocol;
    }

    @Override
    public String toString() {
        return getProvidedProtocol();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Protocol protocol = (Protocol) o;

        return providedProtocol.equals(protocol.providedProtocol);
    }

    @Override
    public int hashCode() {
        return providedProtocol.hashCode();
    }
}
