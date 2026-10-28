package com.finemp.finance.period;

import com.finemp.finance.http.FinancialCommandPayloadLimitFilter;

public class PeriodPayloadLimitFilter extends FinancialCommandPayloadLimitFilter {
    public PeriodPayloadLimitFilter(int maxBytes,int concurrency) {super(maxBytes,concurrency,PeriodCommandAuthorizationManager.BODY_ATTRIBUTE,"FINANCIAL_PERIOD");}
}
