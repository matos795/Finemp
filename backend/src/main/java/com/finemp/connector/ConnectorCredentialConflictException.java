package com.finemp.connector;

public class ConnectorCredentialConflictException extends RuntimeException {
    public ConnectorCredentialConflictException() { super("Connector credential conflicts with the current source state."); }
}
