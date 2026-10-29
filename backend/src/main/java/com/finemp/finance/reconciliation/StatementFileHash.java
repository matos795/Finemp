package com.finemp.finance.reconciliation;

import java.security.*;
import java.util.HexFormat;

final class StatementFileHash {
    static String sha256(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private StatementFileHash(){}
}
