package com.finemp.finance.entry;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Independent versioned schema; existing CREATE/UPDATE fingerprints are deliberately untouched. */
final class EntrySettlementFingerprint {
    private EntrySettlementFingerprint() { }
    static String settlement(UUID company,UUID actor,UUID entry,long version,EntrySettlementData data) {
        return hash(company,actor,entry,version,EntryCommandAction.SETTLED,data);
    }
    static String reversal(UUID company,UUID actor,UUID entry,long version) {
        return hash(company,actor,entry,version,EntryCommandAction.SETTLEMENT_REVERSED,null);
    }
    private static String hash(UUID company,UUID actor,UUID entry,long version,EntryCommandAction action,EntrySettlementData data) {
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            for(Object value:new Object[]{"FINEMP_ENTRY_SETTLEMENT_COMMAND_V1",company,actor,entry,version,action,
                    data==null?null:data.paymentDate(),data==null?null:data.paidAmount().toPlainString(),data==null?null:data.bankAccountId()}) {
                if(value==null)out.writeInt(-1);
                else {var encoded=value.toString().getBytes(StandardCharsets.UTF_8);out.writeInt(encoded.length);out.write(encoded);}
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        }catch(IOException|NoSuchAlgorithmException unavailable) {throw new IllegalStateException("Fingerprint financeiro indisponível.");}
    }
}
