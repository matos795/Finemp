package com.finemp.finance.reconciliation;

import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

/** Strict bounded envelope; never logs or persists the supplied file. */
@Component
public class StatementImportRequestDecoder {
    static final int MAX_BODY=12*1024*1024,MAX_FILE=8*1024*1024;
    private static final Set<String> FIELDS=Set.of("format","separator","fileBase64","fileSha256","selectedRows","accountConfirmed","currencyConfirmed","warningsConfirmed");
    private final JsonMapper json=JsonMapper.builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(4).maxNumberLength(10).maxStringLength(11*1024*1024).maxNameLength(64).maxTokenCount(10100).maxDocumentLength(MAX_BODY).build()).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public Request decode(byte[] body){
        try {
            if(body==null || body.length==0 || body.length>MAX_BODY)throw invalid();var node=json.readTree(body);if(node==null || !node.isObject())throw invalid();
            for(var property:node.properties())if(!FIELDS.contains(property.getKey()))throw invalid();
            String format=text(node,"format"),fileBase64=text(node,"fileBase64"),sha=text(node,"fileSha256");String separator=null;
            var sep=node.get("separator");if(sep!=null && !sep.isNull()){if(!sep.isTextual())throw invalid();separator=sep.asString();}
            if(!Set.of("CSV","OFX").contains(format) || "CSV".equals(format) && !Set.of("COMMA","SEMICOLON","TAB","PIPE").contains(separator) || "OFX".equals(format) && separator!=null || !sha.matches("[0-9a-f]{64}"))throw invalid();
            for(var flag:List.of("accountConfirmed","currencyConfirmed","warningsConfirmed")){var value=node.get(flag);if(value==null || !value.isBoolean() || !value.booleanValue())throw invalid();}
            var selection=node.get("selectedRows");if(selection==null || !selection.isArray() || selection.isEmpty() || selection.size()>5000)throw invalid();
            var rows=new TreeSet<Integer>();for(var item:selection){if(!item.isIntegralNumber() || !item.canConvertToInt() || item.intValue()<1 || !rows.add(item.intValue()))throw invalid();}
            byte[] file=Base64.getDecoder().decode(fileBase64);if(file.length==0 || file.length>MAX_FILE)throw invalid();return new Request(format,separator,file,sha,List.copyOf(rows));
        }catch(RuntimeException invalid){throw invalid();}
    }
    private String text(JsonNode node,String name){var value=node.get(name);if(value==null || !value.isTextual())throw invalid();return value.asString();}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Solicitação de importação inválida.");}
    public record Request(String format,String separator,byte[] file,String fileSha256,List<Integer> selectedRows){
        public Request{file=file.clone();selectedRows=List.copyOf(selectedRows);}
        @Override public byte[] file(){return file.clone();}
        @Override public String toString(){return "StatementImportRequest[redacted]";}
    }
}
