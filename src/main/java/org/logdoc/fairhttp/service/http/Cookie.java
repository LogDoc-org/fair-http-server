package org.logdoc.fairhttp.service.http;

import java.net.HttpCookie;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static org.logdoc.helpers.Texts.isEmpty;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 06.02.2023 15:39
 * FairHttpService ☭ sweat and blood
 */
public class Cookie {
    private static final String tspecials = ",; ";  // deliberately includes space
    private final String name;  // NAME= ... "$Name" style is reserved
    private final long whenCreated;
    private String value;       // value of NAME
    private String domain;      // Domain=VALUE ... domain that sees cookie
    private long maxAge = -1;  // Max-Age=VALUE ... cookies auto-expire
    private String path;        // Path=VALUE ... URLs that see the cookie
    private String portlist;    // Port[="portlist"] ... the port cookie may be returned to
    private boolean secure;     // Secure ... e.g. use SSL
    private boolean httpOnly;   // HttpOnly ... i.e. not accessible to scripts
    private int version = 1;    // Version=1 ... RFC 2965 style
    private SameSite sameSite;

    public Cookie(final String name, final String value) {
        if (isEmpty(name) || !isToken(name) || name.charAt(0) == '$')
            throw new IllegalArgumentException("Illegal cookie name");

        this.name = name.trim();
        this.value = value;
        secure = false;

        whenCreated = System.currentTimeMillis();
        portlist = null;
    }

    public Cookie(
            String name,
            String value,
            Integer maxAge,
            String path,
            String domain,
            boolean secure,
            boolean httpOnly,
            SameSite sameSite) {
        this.name = name;
        this.value = value;
        this.maxAge = maxAge;
        this.path = path;
        this.domain = domain;
        this.secure = secure;
        this.httpOnly = httpOnly;
        this.sameSite = sameSite;
        whenCreated = System.currentTimeMillis();
    }

    public static Builder builder(String name, String value) {
        return new Builder(name, value);
    }

    public String getName() {
        return name;
    }

    public long getWhenCreated() {
        return whenCreated;
    }

    public String getValue() {
        return value;
    }

    public void setValue(final String value) {
        this.value = value;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(final String domain) {
        this.domain = domain;
    }

    public long getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(final long maxAge) {
        this.maxAge = maxAge;
    }

    public String getPath() {
        return path;
    }

    public void setPath(final String path) {
        this.path = path;
    }

    public String getPortlist() {
        return portlist;
    }

    public void setPortlist(final String portlist) {
        this.portlist = portlist;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(final boolean secure) {
        this.secure = secure;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public void setHttpOnly(final boolean httpOnly) {
        this.httpOnly = httpOnly;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(final int version) {
        this.version = version;
    }

    private boolean isToken(final String value) {
        int len = value.length();

        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);

            if (c < 0x20 || c >= 0x7f || tspecials.indexOf(c) != -1)
                return false;
        }
        return true;
    }

    public SameSite getSameSite() {
        return sameSite;
    }

    public void setSameSite(final SameSite sameSite) {
        this.sameSite = sameSite;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(getName()).append("=\"").append(getValue()).append('"');
        if (getPath() != null)
            sb.append("; Path=\"").append(getPath()).append('"');
        if (getDomain() != null)
            sb.append("; Domain=\"").append(getDomain()).append('"');
        if (getPortlist() != null)
            sb.append("; Port=\"").append(getPortlist()).append('"');
        if (getMaxAge() > -1)
            sb.append("; Expires=").append(LocalDateTime.now().plus(getMaxAge(), ChronoUnit.SECONDS).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME));
        if (isSecure())
            sb.append("; Secure");
        if (isHttpOnly())
            sb.append("; HttpOnly");
        if (getSameSite() != null)
            sb.append("; SameSite=\"").append(getSameSite().value()).append('"');

        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof HttpCookie))
            return false;
        HttpCookie other = (HttpCookie) obj;

        return equalsIgnoreCase(getName(), other.getName()) &&
                equalsIgnoreCase(getDomain(), other.getDomain()) &&
                Objects.equals(getPath(), other.getPath());
    }

    @Override
    public int hashCode() {
        int h1 = name.toLowerCase().hashCode();
        int h2 = (domain != null) ? domain.toLowerCase().hashCode() : 0;
        int h3 = (path != null) ? path.hashCode() : 0;

        return h1 + h2 + h3;
    }

    private boolean equalsIgnoreCase(String s, String t) {
        if (s == t) return true;
        if ((s != null) && (t != null)) {
            return s.equalsIgnoreCase(t);
        }
        return false;
    }

    public enum SameSite {
        STRICT("Strict"),
        LAX("Lax"),
        NONE("None");

        private final String value;

        SameSite(String value) {
            this.value = value;
        }

        public String value() {
            return this.value;
        }
    }

    public static class Builder {

        private String name;
        private String value;
        private int maxAge;
        private String path = "/";
        private String domain;
        private boolean secure = false;
        private boolean httpOnly = true;
        private SameSite sameSite;

        private Builder(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withValue(String value) {
            this.value = value;
            return this;
        }

        public Builder withMaxAge(Duration maxAge) {
            this.maxAge = (int) maxAge.getSeconds();
            return this;
        }

        public Builder withPath(String path) {
            this.path = path;
            return this;
        }

        public Builder withDomain(String domain) {
            this.domain = domain;
            return this;
        }

        public Builder withSecure(boolean secure) {
            this.secure = secure;
            return this;
        }

        public Builder withHttpOnly(boolean httpOnly) {
            this.httpOnly = httpOnly;
            return this;
        }

        public Builder withSameSite(SameSite sameSite) {
            this.sameSite = sameSite;
            return this;
        }

        public Cookie build() {
            return new Cookie(
                    this.name,
                    this.value,
                    this.maxAge,
                    this.path,
                    this.domain,
                    this.secure,
                    this.httpOnly,
                    this.sameSite);
        }
    }
}
