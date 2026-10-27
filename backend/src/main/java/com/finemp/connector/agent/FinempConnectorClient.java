package com.finemp.connector.agent;

import com.finemp.accounting.reading.AccountingSourceSnapshot;
import com.finemp.accounting.sync.SyncFailureCode;
import com.finemp.accounting.sync.execution.SyncAttemptStatus;
import com.finemp.connector.completion.ConnectorWorkResult;
import com.finemp.connector.transport.ConnectorSessionController.ConnectorSession;
import com.finemp.connector.work.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.finemp.connector.agent.ConnectorClientException.Kind.*;

/** Stateless protocol client; caller retains claim/snapshot until receipt. Never automatically replays a batch. */
public final class FinempConnectorClient {
    private final ConnectorHttpsTransport transport;
    private final UUID source;
    private final ConnectorProtocolCodec codec;
    public FinempConnectorClient(ConnectorHttpsTransport transport, UUID source, int maxRecords) {
        if (transport == null || source == null) throw new ConnectorClientException(CONFIGURATION);
        this.transport=transport; this.source=source; this.codec=new ConnectorProtocolCodec(maxRecords);
    }
    public ConnectorSession session() { var response=transport.exchange("GET","/session",null); expect(response,200); return codec.session(response.body(),source); }
    public Optional<ConnectorWorkResponse> claim() {
        session(); // Refuse a credential for another local source before claiming work.
        var response=transport.exchange("POST","/work/claim",null);
        if (response.status()==204) { if(response.body().length!=0) throw new ConnectorClientException(PROTOCOL); return Optional.empty(); }
        expect(response,200); return Optional.of(codec.work(response.body(),source));
    }
    public ConnectorLeaseResponse heartbeat(ConnectorWorkResponse work) {
        validate(work); var response=transport.exchange("POST",path(work,"heartbeat"),null); expect(response,200);
        return codec.lease(response.body(),work.attemptId());
    }
    public ConnectorWorkResult result(ConnectorWorkResponse work) {
        validate(work); var response=transport.exchange("GET",path(work,"result"),null); expect(response,200); return codec.result(response.body(),work);
    }
    public ConnectorWorkResult complete(ConnectorWorkResponse work, AccountingSourceSnapshot snapshot) {
        validate(work); byte[] body=codec.batch(work,snapshot); return submit(work,"complete",body);
    }
    public ConnectorWorkResult fail(ConnectorWorkResponse work, SyncFailureCode code) {
        validate(work);
        if (code!=SyncFailureCode.READ_FAILED && code!=SyncFailureCode.RESOURCE_LIMIT && code!=SyncFailureCode.IDENTITY_MISMATCH
                && code!=SyncFailureCode.INVALID_PAYLOAD) throw new ConnectorClientException(PROTOCOL);
        return submit(work,"fail",("{\"code\":\""+code.name()+"\"}").getBytes(StandardCharsets.UTF_8));
    }
    private ConnectorWorkResult submit(ConnectorWorkResponse work, String operation, byte[] body) {
        ConnectorHttpsTransport.Response response;
        try { response=transport.exchange("POST",path(work,operation),body); }
        catch (ConnectorClientException uncertain) {
            if (uncertain.kind()!=TRANSPORT && uncertain.kind()!=TIMEOUT) throw uncertain;
            return result(work); // Lost response: receipt first, no resend and no assumed ACK.
        }
        if(response.status()==409 || response.status()>=500) return result(work);
        if(response.status()!=200 && response.status()!=202 && response.status()!=422) expect(response,200);
        var receipt=codec.result(response.body(),work);
        if (operation.equals("complete") && response.status()==200 && receipt.attemptStatus()!=SyncAttemptStatus.SUCCEEDED
                || response.status()!=200 && receipt.attemptStatus()!=SyncAttemptStatus.FAILED && receipt.attemptStatus()!=SyncAttemptStatus.EXPIRED)
            throw new ConnectorClientException(PROTOCOL);
        return receipt;
    }
    private void validate(ConnectorWorkResponse work) {
        if(work==null || work.attemptId()==null || work.runId()==null || work.context()==null
                || !source.equals(work.context().accountingSourceId()) || work.attemptNumber()<1) throw new ConnectorClientException(PROTOCOL);
    }
    private String path(ConnectorWorkResponse work,String operation) { return "/work/"+work.attemptId()+"/"+operation; }
    private void expect(ConnectorHttpsTransport.Response response,int expected) {
        if(response.status()!=expected) throw new ConnectorClientException(response.status()==401 ? CREDENTIAL : HTTP,response.status(),response.retryAfterSeconds());
    }
}
