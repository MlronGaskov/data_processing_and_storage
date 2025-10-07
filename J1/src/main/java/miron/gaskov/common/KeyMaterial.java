package miron.gaskov.common;

public record KeyMaterial(byte[] privateKeyPem, byte[] certificatePem) {}
