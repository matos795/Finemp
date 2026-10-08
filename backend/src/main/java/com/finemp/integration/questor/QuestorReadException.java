package com.finemp.integration.questor;

public class QuestorReadException extends RuntimeException {

    public QuestorReadException(
            String message,
            Throwable cause) {
        super(message, cause);
    }
}