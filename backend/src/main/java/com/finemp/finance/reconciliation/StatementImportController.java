package com.finemp.finance.reconciliation;

import com.finemp.access.*;
import com.finemp.administration.*;
import com.finemp.finance.bank.BankAccountRepository;
import com.finemp.security.UserAccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Explicit confirmation reprocesses the reviewed file; never trusts frontend monetary values. */
@RestController
@RequestMapping("/api/companies/{companyId}/bank-accounts/{bankId}/statement-imports")
@ConditionalOnProperty(prefix="finemp.finance.statement-import",name="enabled",havingValue="true")
public class StatementImportController {
    private final CompanyPermissionAuthorizationService permissions;private final BankAccountRepository banks;private final StatementRequestCapacity capacity;
    private final StatementImportRequestDecoder decoder;private final StatementImportCommandService commands;
    public StatementImportController(CompanyPermissionAuthorizationService permissions,BankAccountRepository banks,StatementRequestCapacity capacity,StatementImportRequestDecoder decoder,StatementImportCommandService commands){this.permissions=permissions;this.banks=banks;this.capacity=capacity;this.decoder=decoder;this.commands=commands;}
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> confirm(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID bankId,
            @RequestHeader("Idempotency-Key") UUID command,HttpServletRequest request){
        if(actor==null || !permissions.hasFreshPermission(actor.getUserAccountId(),companyId,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();
        banks.findByIdAndCompanyId(bankId,companyId).orElseThrow(()->new SaasResourceNotFoundException("Conta financeira"));
        var encodings=Collections.list(request.getHeaders("Content-Encoding"));
        if(encodings.size()>1 || encodings.stream().anyMatch(value->!value.isBlank() && !value.equalsIgnoreCase("identity")))return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE,Map.of("code","UNSUPPORTED_ENCODING"));
        if(request.getContentLengthLong()>StatementImportRequestDecoder.MAX_BODY)return response(HttpStatus.PAYLOAD_TOO_LARGE,Map.of("code","STATEMENT_PAYLOAD_TOO_LARGE"));
        if(!capacity.acquire())return response(HttpStatus.TOO_MANY_REQUESTS,Map.of("code","STATEMENT_IMPORT_BUSY"));
        try{
            byte[] body;try{body=request.getInputStream().readNBytes(StatementImportRequestDecoder.MAX_BODY+1);}catch(IOException invalid){return response(HttpStatus.BAD_REQUEST,Map.of("code","INVALID_STATEMENT_IMPORT"));}
            if(body.length>StatementImportRequestDecoder.MAX_BODY)return response(HttpStatus.PAYLOAD_TOO_LARGE,Map.of("code","STATEMENT_PAYLOAD_TOO_LARGE"));
            var decoded=decoder.decode(body);byte[] file=decoded.file();String actualHash=StatementFileHash.sha256(file);
            if(!MessageDigest.isEqual(actualHash.getBytes(StandardCharsets.US_ASCII),decoded.fileSha256().getBytes(StandardCharsets.US_ASCII)))throw invalid();
            Map<Integer,StatementValues> available=new HashMap<>();
            if("OFX".equals(decoded.format())) {
                var preview=new OfxPreviewParser().parse(file);var warnings=preview.warnings();
                if(warnings.contains(OfxPreviewParser.Warning.MULTIPLE_BANKS) || warnings.contains(OfxPreviewParser.Warning.MULTIPLE_ACCOUNTS) || warnings.contains(OfxPreviewParser.Warning.MULTIPLE_CURRENCIES) || warnings.contains(OfxPreviewParser.Warning.INVALID_BANK_INFORMATION) || !preview.currency().isEmpty() && !preview.currency().equalsIgnoreCase("BRL"))throw invalid();
                for(var row:preview.rows())if(row.values()!=null && row.error()==null)available.put(row.number(),row.values());
            }else{
                char separator=switch(decoded.separator()){case "COMMA"->',';case "SEMICOLON"->';';case "TAB"->'\t';case "PIPE"->'|';default->throw invalid();};
                var preview=new CsvStatementPreviewParser().parse(file,separator);for(var row:preview.rows())if(row.values()!=null && row.error()==null && !row.warnings().contains(CsvStatementPreviewParser.Warning.BALANCE_ROW))available.put(row.number(),row.values());
            }
            var selected=new ArrayList<StatementValues>(decoded.selectedRows().size());for(var number:decoded.selectedRows()){var item=available.get(number);if(item==null)throw invalid();selected.add(item);}
            var receipt=commands.confirmWithProvenance(actor.getUserAccountId(),companyId,command,bankId,selected,StatementFileHash.sha256(body));return response(HttpStatus.OK,receipt);
        }finally{capacity.release();}
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Solicitação de importação inválida.");}
    private ResponseEntity<?> response(HttpStatus status,Object body){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).header("Pragma","no-cache").body(body);}
}
