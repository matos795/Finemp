package com.finemp.finance.entry;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** New action has its own versioned fingerprint; existing hashes are unchanged. */
final class EntryDeletionFingerprint {
    private EntryDeletionFingerprint() { }
    static String of(UUID company,UUID actor,UUID entry,long version) {
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            for(var value:new Object[]{"FINEMP_ENTRY_DELETION_V1",company,actor,EntryCommandAction.DELETED,entry,version}) {
                var encoded=value.toString().getBytes(StandardCharsets.UTF_8);out.writeInt(encoded.length);out.write(encoded);
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        }catch(IOException|NoSuchAlgorithmException unavailable) {throw new IllegalStateException("Fingerprint de exclusão indisponível.");}
    }
}
