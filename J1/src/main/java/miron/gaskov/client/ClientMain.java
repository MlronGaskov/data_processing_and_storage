package miron.gaskov.client;

import miron.gaskov.common.KeyMaterial;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ClientMain {

    private static final int CONNECT_TIMEOUT_MS = 10_000;

    public static void main(String[] args) {
        var opts = ClientOptions.parse(args);

        try (
                Socket socket = connect(opts.host(), opts.port());
                BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))
        ) {
            sendName(out, opts.name());

            if (opts.abort()) {
                System.out.println("Abort requested: exiting before reading (simulation).");
                return;
            }
            if (opts.delaySec() > 0) {
                System.out.println("Sleeping " + opts.delaySec() + " s before reading...");
                Thread.sleep(opts.delaySec() * 1000L);
            }

            KeyMaterial resp = readResponse(in);
            saveResponse(resp, opts.outPrefix());
            System.out.println("Saved " + opts.outPrefix() + ".key (" + resp.privateKeyPem().length + " B), " +
                    opts.outPrefix() + ".crt (" + resp.certificatePem().length + " B)");
        } catch (Throwable e) {
            System.err.println("Client failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static Socket connect(String host, int port) throws Exception {
        System.out.println("Connecting to " + host + ":" + port + " ...");
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), ClientMain.CONNECT_TIMEOUT_MS);
        s.setTcpNoDelay(true);
        return s;
    }

    private static void sendName(BufferedOutputStream out, String name) throws Exception {
        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        out.write(nameBytes);
        out.write(0);
        out.flush();
        System.out.println("Sent name '" + name + "' (" + nameBytes.length + " bytes + NUL)");
    }

    private static KeyMaterial readResponse(DataInputStream in) throws Exception {
        System.out.println("Waiting for response...");

        int keyLen = in.readInt();
        if (keyLen <= 0) throw new EOFException("Invalid key length: " + keyLen);
        byte[] keyPem = new byte[keyLen];
        in.readFully(keyPem);

        int crtLen = in.readInt();
        if (crtLen <= 0) throw new EOFException("Invalid certificate length: " + crtLen);
        byte[] crtPem = new byte[crtLen];
        in.readFully(crtPem);

        System.out.println("Received key=" + keyPem.length + " B, cert=" + crtPem.length + " B");
        return new KeyMaterial(keyPem, crtPem);
    }

    private static void saveResponse(KeyMaterial resp, String outPrefix) throws Exception {
        Path keyPath = Path.of(outPrefix + ".key");
        Path crtPath = Path.of(outPrefix + ".crt");
        Files.write(keyPath, resp.privateKeyPem());
        Files.write(crtPath, resp.certificatePem());
    }
}
