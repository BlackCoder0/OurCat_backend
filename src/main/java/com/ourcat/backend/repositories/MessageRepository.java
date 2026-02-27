package com.ourcat.backend.repositories;

import com.ourcat.backend.models.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
