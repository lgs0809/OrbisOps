package cn.lgs.orbisops.application.channel;

/**
 * Protects durable Channel payloads without exposing encryption details to orchestration.
 */
public interface ChannelPayloadCipherPort {

    boolean available();

    String encrypt(String plaintext, String associatedData);

    String decrypt(String protectedPayload, String associatedData);
}
