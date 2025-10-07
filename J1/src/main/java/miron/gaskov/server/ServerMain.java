package miron.gaskov.server;

import miron.gaskov.server.generation.CertificateService;
import miron.gaskov.server.generation.RsaGenerationService;
import miron.gaskov.server.utils.SigningKeyLoader;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.Security;
import java.util.concurrent.Executors;

public final class ServerMain {
    public static void main(String[] args) throws Exception {
        var opts = ServerOptions.parse(args);

        Security.addProvider(new BouncyCastleProvider());

        PrivateKey signingKey = SigningKeyLoader.loadPem(Path.of(opts.signingKeyPath()));

        var certService = new CertificateService(
                new X500Name(opts.issuerDN()),
                signingKey,
                opts.sigAlg(),
                opts.validDays()
        );

        var pool = Executors.newFixedThreadPool(opts.threads());

        var genService = new RsaGenerationService(pool, certService, opts.keyBits());

        var server = new Server(opts.port(), genService);
        server.run();
    }
}
