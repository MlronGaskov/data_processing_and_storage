package miron.gaskov.common;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class Pem {
    private Pem() {
    }

    public static byte[] encode(String type, byte[] der) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        String pem = "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
        return pem.getBytes(StandardCharsets.US_ASCII);
    }
}