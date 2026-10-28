package com.finemp.connector.agent;

/** Local secret-source port. Production bootstrap uses environment references, never remote payload values. */
@FunctionalInterface
public interface ConnectorSecretSource {
    String read(String reference);
    static ConnectorSecretSource environment(){return System::getenv;}
}
