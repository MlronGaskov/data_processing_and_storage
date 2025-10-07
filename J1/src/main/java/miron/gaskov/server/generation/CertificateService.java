package miron.gaskov.server.generation;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Objects;

public final class CertificateService {
    private final X500Name issuer;
    private final PrivateKey signingKey;
    private final String sigAlg;
    private final int validDays;
    private final SecureRandom rnd = new SecureRandom();

    public CertificateService(X500Name issuer, PrivateKey signingKey, String sigAlg, int validDays) {
        this.issuer = Objects.requireNonNull(issuer);
        this.signingKey = Objects.requireNonNull(signingKey);
        this.sigAlg = Objects.requireNonNullElse(sigAlg, "SHA512withRSA");
        this.validDays = validDays;
    }

    public X509Certificate issueCertificate(String subjectName, PublicKey subjectPublicKey) throws Exception {
        X500Name subject = new X500Name("CN=" + subjectName);

        long now = System.currentTimeMillis();
        Date notBefore = new Date(now);
        Date notAfter  = new Date(now + validDays * 24L * 60 * 60 * 1000);

        BigInteger serial = new BigInteger(160, rnd);

        SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(subjectPublicKey.getEncoded());
        X509v3CertificateBuilder builder = new X509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, subject, spki);

        ContentSigner signer = new JcaContentSignerBuilder(sigAlg).build(signingKey);
        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
    }
}
