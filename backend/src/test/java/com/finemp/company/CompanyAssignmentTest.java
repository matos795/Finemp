package com.finemp.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finemp.accountingfirm.AccountingFirm;
import com.finemp.client.Client;
import org.junit.jupiter.api.Test;

class CompanyAssignmentTest {

    @Test
    void shouldAttachAnUnassignedCompanyWithoutChangingItsIdentity() {
        Company company = new Company("Synthetic Legacy", "11111111000111");
        Client client = new Client(new AccountingFirm("Synthetic Firm", true), "Synthetic Client", true);

        company.assignToClient(client);

        assertThat(company.getClient()).isSameAs(client);
        assertThat(company.getLegalName()).isEqualTo("Synthetic Legacy");
        assertThat(company.getCnpj()).isEqualTo("11111111000111");
    }

    @Test
    void shouldRejectReassignmentEvenOutsideTheAdministrativeService() {
        var firm = new AccountingFirm("Synthetic Firm", true);
        var first = new Client(firm, "Synthetic First", true);
        var second = new Client(firm, "Synthetic Second", true);
        var company = new Company(first, "Synthetic Company", "11111111000111");

        assertThatThrownBy(() -> company.assignToClient(second)).isInstanceOf(IllegalStateException.class);
        assertThat(company.getClient()).isSameAs(first);
    }
}
