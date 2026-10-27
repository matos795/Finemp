package com.finemp.connector.work;
public class ConnectorWorkNotCurrentException extends RuntimeException {
    public ConnectorWorkNotCurrentException() { super("Connector work is not current."); }
}
