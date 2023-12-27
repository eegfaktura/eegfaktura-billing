package org.vfeeg.eegfaktura.billing.util;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;

import static java.util.Locale.US;

import javax.net.ssl.X509TrustManager;

public class IgnoreExpirationTrustManager implements X509TrustManager {
    private final X509TrustManager innerTrustManager;
    private final String fingerprint;

    public IgnoreExpirationTrustManager(X509TrustManager innerTrustManager, String fingerprint) {
        this.innerTrustManager = innerTrustManager;
        this.fingerprint = fingerprint;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        this.innerTrustManager.checkClientTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        try {
            if (getFingerprint(chain[0]).equals(fingerprint)) {
                chain = Arrays.copyOf(chain, chain.length);
                X509Certificate[] newChain = new X509Certificate[chain.length];
                newChain[0] = new EternalCertificate(chain[0]);
                System.arraycopy(chain, 1, newChain, 1, chain.length - 1);
                chain = newChain;
            }
            this.innerTrustManager.checkServerTrusted(chain, authType);
        } catch (NoSuchAlgorithmException e) {
            throw new CertificateException(e);
        }
    }

    private String getFingerprint(X509Certificate cert) throws NoSuchAlgorithmException, CertificateEncodingException {
        MessageDigest hash = MessageDigest.getInstance("SHA-1");
        hash.update(cert.getEncoded());
        byte[] fingerprint = hash.digest();
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < fingerprint.length; i++) {
            output.append(String.format(US, "%02X", fingerprint[i]));
        }
        return output.toString();
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return this.innerTrustManager.getAcceptedIssuers();
    }

    private class EternalCertificate extends X509Certificate {
        private final X509Certificate originalCertificate;

        public EternalCertificate(X509Certificate originalCertificate) {
            this.originalCertificate = originalCertificate;
        }

        @Override
        public void checkValidity() throws CertificateExpiredException, CertificateNotYetValidException {
            // Ignore notBefore/notAfter
        }

        @Override
        public void checkValidity(Date date) throws CertificateExpiredException, CertificateNotYetValidException {
            // Ignore notBefore/notAfter
        }

        @Override
        public int getVersion() {
            return originalCertificate.getVersion();
        }

        @Override
        public BigInteger getSerialNumber() {
            return originalCertificate.getSerialNumber();
        }

        @Override
        public Principal getIssuerDN() {
            return originalCertificate.getIssuerDN();
        }

        @Override
        public Principal getSubjectDN() {
            return originalCertificate.getSubjectDN();
        }

        @Override
        public Date getNotBefore() {
            return originalCertificate.getNotBefore();
        }

        @Override
        public Date getNotAfter() {
            return originalCertificate.getNotAfter();
        }

        @Override
        public byte[] getTBSCertificate() throws CertificateEncodingException {
            return originalCertificate.getTBSCertificate();
        }

        @Override
        public byte[] getSignature() {
            return originalCertificate.getSignature();
        }

        @Override
        public String getSigAlgName() {
            return originalCertificate.getSigAlgName();
        }

        @Override
        public String getSigAlgOID() {
            return originalCertificate.getSigAlgOID();
        }

        @Override
        public byte[] getSigAlgParams() {
            return originalCertificate.getSigAlgParams();
        }

        @Override
        public boolean[] getIssuerUniqueID() {
            return originalCertificate.getIssuerUniqueID();
        }

        @Override
        public boolean[] getSubjectUniqueID() {
            return originalCertificate.getSubjectUniqueID();
        }

        @Override
        public boolean[] getKeyUsage() {
            return originalCertificate.getKeyUsage();
        }

        @Override
        public int getBasicConstraints() {
            return originalCertificate.getBasicConstraints();
        }

        @Override
        public byte[] getEncoded() throws CertificateEncodingException {
            return originalCertificate.getEncoded();
        }

        @Override
        public void verify(PublicKey key) throws CertificateException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException, SignatureException {
            originalCertificate.verify(key);
        }

        @Override
        public void verify(PublicKey key, String sigProvider) throws CertificateException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException, SignatureException {
            originalCertificate.verify(key, sigProvider);
        }

        @Override
        public String toString() {
            return originalCertificate.toString();
        }

        @Override
        public PublicKey getPublicKey() {
            return originalCertificate.getPublicKey();
        }

        @Override
        public Set<String> getCriticalExtensionOIDs() {
            return originalCertificate.getCriticalExtensionOIDs();
        }

        @Override
        public byte[] getExtensionValue(String oid) {
            return originalCertificate.getExtensionValue(oid);
        }

        @Override
        public Set<String> getNonCriticalExtensionOIDs() {
            return originalCertificate.getNonCriticalExtensionOIDs();
        }

        @Override
        public boolean hasUnsupportedCriticalExtension() {
            return originalCertificate.hasUnsupportedCriticalExtension();
        }
    }
}