package com.finemp.finance.reconciliation;

import com.finemp.access.*;
import com.finemp.administration.*;
import com.finemp.finance.bank.BankAccountRepository;
import com.finemp.security.UserAccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Authenticated, bounded, non-persistent statement preview. */
@RestController
@RequestMapping("/api/companies/{companyId}/bank-accounts/{bankId}/statement-previews")
@ConditionalOnProperty(prefix="finemp.finance.statement-preview",name="enabled",havingValue="true")
public class StatementPreviewController {
    private static final int MAX_BYTES=16*1024*1024;
    private final StatementRequestCapacity capacity;
    private final CompanyPermissionAuthorizationService permissions;
    private final BankAccountRepository banks;
    public StatementPreviewController(CompanyPermissionAuthorizationService permissions,BankAccountRepository banks,StatementRequestCapacity capacity){this.permissions=permissions;this.banks=banks;this.capacity=capacity;}

    @PostMapping(consumes=MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> preview(@AuthenticationPrincipal UserAccountPrincipal actor,@PathVariable UUID companyId,@PathVariable UUID bankId,
            @RequestParam String format,@RequestParam(required=false) String separator,HttpServletRequest request) {
        if(actor==null || !permissions.hasFreshPermission(actor.getUserAccountId(),companyId,CompanyPermission.FINANCIAL_ENTRY_MANAGE))throw new OrganizationAccessDeniedException();
        banks.findByIdAndCompanyId(bankId,companyId).orElseThrow(()->new SaasResourceNotFoundException("Conta financeira"));
        var encodings=Collections.list(request.getHeaders("Content-Encoding"));
        if(encodings.size()>1 || encodings.stream().anyMatch(value->!value.isBlank() && !value.equalsIgnoreCase("identity")))return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE,Map.of("code","UNSUPPORTED_ENCODING"));
        char delimiter=0;
        if("CSV".equals(format))delimiter=switch(separator==null?"":separator){case "COMMA"->',';case "SEMICOLON"->';';case "TAB"->'\t';case "PIPE"->'|';default->throw new IllegalArgumentException("Separador inválido.");};
        else if(!"OFX".equals(format) || separator!=null)throw new IllegalArgumentException("Formato inválido.");
        if(request.getContentLengthLong()>MAX_BYTES)return response(HttpStatus.PAYLOAD_TOO_LARGE,Map.of("code","STATEMENT_PAYLOAD_TOO_LARGE"));
        if(!capacity.acquire())return response(HttpStatus.TOO_MANY_REQUESTS,Map.of("code","STATEMENT_PREVIEW_BUSY"));
        try {
            byte[] bytes;try{bytes=request.getInputStream().readNBytes(MAX_BYTES+1);}catch(IOException invalid){return response(HttpStatus.BAD_REQUEST,Map.of("code","INVALID_STATEMENT_PREVIEW"));}
            if(bytes.length>MAX_BYTES)return response(HttpStatus.PAYLOAD_TOO_LARGE,Map.of("code","STATEMENT_PAYLOAD_TOO_LARGE"));
            Object result="OFX".equals(format)?new OfxPreviewParser().parse(bytes):new CsvStatementPreviewParser().parse(bytes,delimiter);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache").header("X-Statement-SHA256",StatementFileHash.sha256(bytes)).body(result);
        }finally{capacity.release();}
    }
    private ResponseEntity<?> response(HttpStatus status,Object body){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).header("Pragma","no-cache").body(body);}
}
