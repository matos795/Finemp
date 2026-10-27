package com.finemp.connector.completion;
public class ConnectorBatchResourceLimitException extends RuntimeException {
    public ConnectorBatchResourceLimitException() { super("Connector batch resource limit exceeded."); }
}
