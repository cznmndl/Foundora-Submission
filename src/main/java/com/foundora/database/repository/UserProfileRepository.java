package com.foundora.database.repository;

import com.foundora.database.entity.UserProfile;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, String> {

    Optional<UserProfile> findByEmailIgnoreCase(String email);

    // SELECT ... FOR UPDATE: locks the profile row until the transaction ends, so concurrent PIN tries or
    // admin actions (reset, unlock) can't overwrite each other's changes.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from UserProfile p where p.uid = :uid")
    Optional<UserProfile> findForUpdate(@Param("uid") String uid);

    List<UserProfile> findByEmailIn(Collection<String> emails);

    List<UserProfile> findByPinLockedUntilAfterOrderByPinLockedUntilAsc(Instant now);

    // Case-insensitive partial match on name or email; callers pass the text already in lower case.
    @Query("""
            select p from UserProfile p
            where lower(p.fullName) like concat('%', :text, '%') or lower(p.email) like concat('%', :text, '%')
            order by p.fullName""")
    List<UserProfile> search(@Param("text") String lowerCaseText, Pageable limit);
}
