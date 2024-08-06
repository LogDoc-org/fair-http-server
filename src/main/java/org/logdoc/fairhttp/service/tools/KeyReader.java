package org.logdoc.fairhttp.service.tools;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * @author Denis Danilin | me@loslobos.ru
 * 01.06.2023 14:37
 * ItWave ☭ sweat and blood
 */
public abstract class KeyReader<T extends Key> {
    public static final KeyReader<PrivateKey> privater = new KeyReader<>() {
        @Override
        protected KeySpec getKeySpec(final byte[] keyBytes) {
            return new PKCS8EncodedKeySpec(keyBytes);
        }

        @Override
        protected PrivateKey fromBytes(final byte[] bytes) throws NoSuchAlgorithmException, InvalidKeySpecException {
            return KeyFactory.getInstance("RSA").generatePrivate(getKeySpec(bytes));
        }
    };
    public static final KeyReader<PublicKey> publicer = new KeyReader<>() {
        @Override
        protected PublicKey fromBytes(final byte[] bytes) throws Exception {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
        }

        @Override
        protected KeySpec getKeySpec(final byte[] keyBytes) {
            return new X509EncodedKeySpec(keyBytes);
        }
    };

    public T fromResourcePath(final String path) throws Exception {
        try (final InputStream is = KeyReader.class.getClassLoader().getResourceAsStream(path)) {
            if (is != null)
                try (final ByteArrayOutputStream os = new ByteArrayOutputStream(512)) {
                    final byte[] buffer = new byte[4096];
                    int n;
                    while (-1 != (n = is.read(buffer)))
                        os.write(buffer, 0, n);
                    return fromBytes(os.toByteArray());
                }
        }

        throw new NullPointerException();
    }

    public T fromPath(final Path path) throws Exception {
        return fromBytes(Files.readAllBytes(path));
    }

    protected abstract T fromBytes(final byte[] bytes) throws Exception;

    public T fromFile(String filename) throws Exception {
        return fromBytes(Files.readAllBytes(Paths.get(filename)));
    }

    protected abstract KeySpec getKeySpec(final byte[] keyBytes);
}
