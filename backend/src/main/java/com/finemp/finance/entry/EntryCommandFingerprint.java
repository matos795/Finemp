package com.finemp.finance.entry;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

final class EntryCommandFingerprint {
    private EntryCommandFingerprint() { }
    static String of(UUID company,UUID actor,EntryCommandAction action,UUID entry,Long version,FinancialEntryCommandData data) {
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            for(Object value:new Object[]{"FINEMP_ENTRY_COMMAND_V1",company,actor,action,entry,version,data.direction(),data.description(),
                    data.nominalAmount().toPlainString(),data.documentDate(),data.entryDate(),data.competence(),data.dueDate(),data.paymentDate(),
                    data.bankAccountId(),data.categoryId(),data.partnerId(),data.resultCenterId(),data.costBehaviorOverride(),
                    data.paymentMethod(),data.documentNumber(),data.notes()}) {
                if(value==null)out.writeInt(-1);
                else {byte[] encoded=value.toString().getBytes(StandardCharsets.UTF_8);out.writeInt(encoded.length);out.write(encoded);}
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        }catch(IOException|NoSuchAlgorithmException unavailable) {throw new IllegalStateException("Fingerprint financeiro indisponível.");}
    }
}
