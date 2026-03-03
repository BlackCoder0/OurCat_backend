package com.ourcat.backend.services;

import com.ourcat.backend.models.Message;
import com.ourcat.backend.repositories.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;

    public Page<Message> listByUser(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return messageRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional
    public void markRead(Long messageId, Long userId) {
        messageRepository.findById(messageId).ifPresent(m -> {
            if (m.getUserId().equals(userId)) {
                m.setRead(true);
                messageRepository.save(m);
            }
        });
    }

    public Long create(Long userId, String type, String content) {
        return create(userId, type, content, null, null);
    }

    public Long create(Long userId, String type, String content, String targetType, Long targetId) {
        Message m = new Message();
        m.setUserId(userId);
        m.setType(type);
        m.setContent(content);
        m.setTargetType(targetType);
        m.setTargetId(targetId);
        return messageRepository.save(m).getId();
    }
}
