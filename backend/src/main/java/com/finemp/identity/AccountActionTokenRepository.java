package com.finemp.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountActionTokenRepository extends JpaRepository<AccountActionToken, UUID> {

    @Query("""
            select new com.finemp.identity.AccountActionTokenReference(token.id, token.userAccount.id)
            from AccountActionToken token where token.tokenHash = :hash
            """)
    Optional<AccountActionTokenReference> findReferenceByTokenHash(@Param("hash") String hash);

    List<AccountActionToken> findAllByUserAccountIdAndConsumedAtIsNull(UUID userAccountId);
}
