package com.foundora.database.repository;

import com.foundora.database.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    List<LoginAttempt> findByEmailOrderByCreatedAtAscIdAsc(String email);
}
