package com.finemp.security;

import com.finemp.access.UserAccount;
import com.finemp.access.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountDetailsServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Test
    void shouldLoadUserAccountByEmailIgnoringCase() {

        UserAccount userAccount = new UserAccount(
                "Usuário Exemplo",
                "usuario@example.com",
                "{bcrypt}hash-exemplo");

        when(userAccountRepository.findByEmailIgnoreCase(
                "USUARIO@example.com"))
                .thenReturn(Optional.of(userAccount));

        UserAccountDetailsService service = new UserAccountDetailsService(userAccountRepository);

        UserAccountPrincipal principal = (UserAccountPrincipal) service.loadUserByUsername(
                "USUARIO@example.com");

        assertThat(principal.getUsername())
                .isEqualTo("usuario@example.com");

        assertThat(principal.getPassword())
                .isEqualTo("{bcrypt}hash-exemplo");

        assertThat(principal.isEnabled())
                .isTrue();
    }

    @Test
    void shouldRejectUnknownEmail() {

        when(userAccountRepository.findByEmailIgnoreCase(
                "unknown@example.com"))
                .thenReturn(Optional.empty());

        UserAccountDetailsService service = new UserAccountDetailsService(userAccountRepository);

        assertThatThrownBy(() -> service.loadUserByUsername("unknown@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}