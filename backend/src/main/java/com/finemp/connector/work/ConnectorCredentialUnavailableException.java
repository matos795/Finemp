package com.finemp.connector.work;
public class ConnectorCredentialUnavailableException extends RuntimeException {
    public ConnectorCredentialUnavailableException() { super("Connector credential is not current."); }
}
