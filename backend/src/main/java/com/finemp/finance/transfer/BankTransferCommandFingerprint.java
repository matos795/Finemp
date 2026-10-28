package com.finemp.finance.transfer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

final class BankTransferCommandFingerprint {
    private BankTransferCommandFingerprint() { }
    static String create(UUID company,UUID actor,BankTransferValues values) {
        return hash(company,actor,BankTransferCommandAction.CREATED,null,null,values.originBankAccountId(),values.destinationBankAccountId(),
                values.transferDate(),values.amount().toPlainString(),values.description());
    }
    static String delete(UUID company,UUID actor,UUID transfer,Long version) {return hash(company,actor,BankTransferCommandAction.DELETED,transfer,version);}
    private static String hash(Object... values) {
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            write(out,"FINEMP_BANK_TRANSFER_COMMAND_V1");for(var value:values)write(out,value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        }catch(IOException|NoSuchAlgorithmException unavailable) {throw new IllegalStateException("Fingerprint de transferência indisponível.");}
    }
    private static void write(DataOutputStream out,Object value)throws IOException {
        if(value==null)out.writeInt(-1);
        else {byte[] encoded=value.toString().getBytes(StandardCharsets.UTF_8);out.writeInt(encoded.length);out.write(encoded);}
    }
}
