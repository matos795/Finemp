package com.finemp.finance.entry;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

final class EntrySeriesCommandFingerprint {
    private EntrySeriesCommandFingerprint() { }
    static String monthly(UUID company,UUID actor,int quantity,FinancialEntryCommandData data) {
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            var canonical=EntryCommandFingerprint.of(company,actor,EntryCommandAction.CREATED,null,null,data);
            for(var value:new Object[]{"FINEMP_MONTHLY_ENTRY_SERIES_V1",company,actor,quantity,canonical}) {
                var encoded=value.toString().getBytes(StandardCharsets.UTF_8);out.writeInt(encoded.length);out.write(encoded);
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        }catch(IOException|NoSuchAlgorithmException unavailable) {throw new IllegalStateException("Fingerprint de série indisponível.");}
    }
}
