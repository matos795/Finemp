package com.finemp.integration.questor;

public class QuestorSourceStateException extends RuntimeException {
    public QuestorSourceStateException() { super("Fonte contábil inativa ou incompatível com esta operação."); }
}
