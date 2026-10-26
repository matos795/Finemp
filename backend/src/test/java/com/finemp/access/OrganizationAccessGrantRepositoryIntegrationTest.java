package com.finemp.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finemp.TestcontainersConfiguration;
import com.finemp.accountingfirm.AccountingFirm;
import com.finemp.accountingfirm.AccountingFirmRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class OrganizationAccessGrantRepositoryIntegrationTest {

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private AccountingFirmRepository accountingFirmRepository;

    @Autowired
    private AccountingFirmAccessGrantRepository accountingFirmAccessGrantRepository;

    @Test
    void shouldPersistAndAuthorizePlatformPermission() {
        UserAccount userAccount = userAccountRepository.saveAndFlush(
                new UserAccount(
                        "Administrador da plataforma",
                        "plataforma@example.com",
                        "hash-exemplo",
                        Set.of(PlatformPermission.ACCOUNTING_FIRM_CREATE)));

        assertThat(userAccountRepository.existsByIdAndPlatformPermission(
                userAccount.getId(),
                PlatformPermission.ACCOUNTING_FIRM_CREATE))
                .isTrue();

        assertThat(userAccountRepository.existsByIdAndPlatformPermission(
                userAccount.getId(),
                PlatformPermission.PLATFORM_USER_MANAGE))
                .isFalse();
    }

    @Test
    void shouldKeepAccountingFirmPermissionsIsolated() {
        UserAccount userAccount = userAccountRepository.save(
                new UserAccount(
                        "Administrador da contabilidade",
                        "contabilidade@example.com",
                        "hash-exemplo"));

        AccountingFirm firstFirm = accountingFirmRepository.save(
                new AccountingFirm("Contabilidade Um", true));
        AccountingFirm secondFirm = accountingFirmRepository.save(
                new AccountingFirm("Contabilidade Dois", true));

        accountingFirmAccessGrantRepository.saveAndFlush(
                new AccountingFirmAccessGrant(
                        userAccount,
                        firstFirm,
                        Set.of(AccountingFirmPermission.HIERARCHY_MANAGE)));

        assertThat(accountingFirmAccessGrantRepository.existsByUserAccountIdAndAccountingFirmIdAndPermission(
                userAccount.getId(),
                firstFirm.getId(),
                AccountingFirmPermission.HIERARCHY_MANAGE))
                .isTrue();

        assertThat(accountingFirmAccessGrantRepository.existsByUserAccountIdAndAccountingFirmIdAndPermission(
                userAccount.getId(),
                secondFirm.getId(),
                AccountingFirmPermission.HIERARCHY_MANAGE))
                .isFalse();
    }

    @Test
    void shouldRejectDuplicateGrantForSameUserAndAccountingFirm() {
        UserAccount userAccount = userAccountRepository.save(
                new UserAccount(
                        "Usuário Exemplo",
                        "usuario-firm@example.com",
                        "hash-exemplo"));

        AccountingFirm accountingFirm = accountingFirmRepository.save(
                new AccountingFirm("Contabilidade Exemplo", true));

        accountingFirmAccessGrantRepository.saveAndFlush(
                new AccountingFirmAccessGrant(
                        userAccount,
                        accountingFirm,
                        Set.of()));

        assertThatThrownBy(() -> accountingFirmAccessGrantRepository.saveAndFlush(
                new AccountingFirmAccessGrant(
                        userAccount,
                        accountingFirm,
                        Set.of())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

