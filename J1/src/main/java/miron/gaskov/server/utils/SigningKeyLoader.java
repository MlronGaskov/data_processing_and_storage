package miron.gaskov.server.utils;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;

public final class SigningKeyLoader {
    private SigningKeyLoader() {
    }

    public static PrivateKey loadPem(Path path) throws Exception {
        try (BufferedReader br = Files.newBufferedReader(path);
             PEMParser parser = new PEMParser(br)) {

            Object obj = parser.readObject();
            if (obj == null) {
                throw new IllegalArgumentException("Empty PEM: " + path);
            }

            var conv = new JcaPEMKeyConverter().setProvider("BC");

            if (obj instanceof PrivateKeyInfo pki) {
                return conv.getPrivateKey(pki);
            }
            if (obj instanceof PEMKeyPair kp) {
                return conv.getPrivateKey(kp.getPrivateKeyInfo());
            }

            throw new IllegalArgumentException("Unsupported PEM object in " + path +
                    " (expected unencrypted PRIVATE KEY or RSA PRIVATE KEY)");
        }
    }
}
