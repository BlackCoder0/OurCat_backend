package com.ourcat.backend.services;

import com.ourcat.backend.models.SquareComment;
import com.ourcat.backend.models.SquarePost;
import com.ourcat.backend.models.RescueActivity;
import com.ourcat.backend.models.Cat;
import com.ourcat.backend.models.CatReport;
import com.ourcat.backend.repositories.CatRepository;
import com.ourcat.backend.repositories.CatReportRepository;
import com.ourcat.backend.repositories.RescueActivityRepository;
import com.ourcat.backend.repositories.SquareCommentRepository;
import com.ourcat.backend.repositories.SquarePostRepository;
import com.ourcat.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SquareService {

    private final SquarePostRepository squarePostRepository;
    private final SquareCommentRepository squareCommentRepository;
    private final UserRepository userRepository;
    private final MessageService messageService;
    private final RescueService rescueService;
    private final CatRepository catRepository;
    private final CatReportRepository catReportRepository;
    private final RescueActivityRepository rescueActivityRepository;

    public Page<SquarePost> listPosts(int page, int size, String sort) {
        Pageable pageable = PageRequest.of(page, size);
        if ("hot".equalsIgnoreCase(sort)) {
            return squarePostRepository.findAllByOrderByLikesDescCreatedAtDesc(pageable);
        }
        return squarePostRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Optional<SquarePost> getPost(Long id) {
        return squarePostRepository.findById(id);
    }

    @Transactional
    public SquarePost createPost(Long userId, String text, List<String> images, String location, String type,
            Long referencedCatId) {
        // 校验救助类型广播
        if ("rescue".equalsIgnoreCase(type)) {
            boolean hasCat = referencedCatId != null && referencedCatId > 0;
            boolean hasLocation = location != null && !location.trim().isEmpty();
            boolean hasImages = images != null && !images.isEmpty();
            if (!hasCat && !hasLocation && !hasImages) {
                throw new IllegalArgumentException("发布救助广播必须包含以下之一：猫咪档案、位置信息、图片");
            }
        }

        String imagesJson = images == null || images.isEmpty() ? null
                : images.stream().collect(Collectors.joining("\",\"", "[\"", "\"]"));
        SquarePost post = SquarePost.builder()
                .text(text)
                .images(imagesJson)
                .location(location)
                .type(type != null ? type : "inquiry")
                .status("open")
                .likes(0)
                .userId(userId)
                .referencedCatId(referencedCatId)
                .build();
        post = squarePostRepository.save(post);

        // 救助类型广播自动创建救助活动
        if ("rescue".equalsIgnoreCase(type)) {
            try {
                RescueActivity activity = rescueService.create(userId,
                        "救助广播-" + (text != null && text.length() > 20 ? text.substring(0, 20) + "..." : text),
                        text, referencedCatId, post.getId(), null);
                // 关联救助活动ID到广播
                post.setRescueActivityId(activity.getId());
                post = squarePostRepository.save(post);
            } catch (Exception e) {
                // 救助活动创建失败不影响广播发布
            }
        }

        return post;
    }

    /**
     * 从救助活动同步创建广场广播（地图发起救助时调用）
     */
    @Transactional
    public SquarePost createPostFromRescue(Long userId, String text, Long rescueActivityId, Long catId,
            String location) {
        SquarePost post = SquarePost.builder()
                .text(text)
                .images(null)
                .location(location)
                .type("rescue")
                .status("open")
                .likes(0)
                .userId(userId)
                .referencedCatId(catId != null && catId > 0 ? catId : null)
                .rescueActivityId(rescueActivityId)
                .build();
        post = squarePostRepository.save(post);

        // 更新救助活动的 squarePostId，建立双向关联
        if (rescueActivityId != null) {
            var activityOpt = rescueActivityRepository.findById(rescueActivityId);
            if (activityOpt.isPresent()) {
                var activity = activityOpt.get();
                activity.setSquarePostId(post.getId());
                rescueActivityRepository.save(activity);
            }
        }

        return post;
    }

    /**
     * 获取猫咪档案的位置信息用于广播定位
     */
    public String getCatLocationForPost(Long catId) {
        if (catId == null || catId <= 0)
            return null;
        // 通过 CatReport 获取最新的位置信息
        return catReportRepository.findFirstByCatIdOrderByReportTimeDesc(catId)
                .filter(report -> report.getLat() != null && report.getLng() != null)
                .map(report -> report.getLat() + "," + report.getLng())
                .orElse(null);
    }

    @Transactional
    public boolean markSolved(Long postId, Long userId, int userRole) {
        Optional<SquarePost> opt = squarePostRepository.findById(postId);
        if (opt.isEmpty())
            return false;
        SquarePost post = opt.get();
        if ("rescue".equals(post.getType())) {
            if (!canMarkSolvedRescue(post, userId)) {
                return false;
            }
        } else {
            if (!post.getUserId().equals(userId) && userRole < 2) {
                return false;
            }
        }
        post.setStatus("resolved");
        squarePostRepository.save(post);
        if (post.getRescueActivityId() != null) {
            rescueService.updateStatus(post.getRescueActivityId(), "completed", userId);
        }
        List<Long> commenterIds = squareCommentRepository.findDistinctUserIdsBySquarePostId(postId);
        String snippet = buildSnippet(post.getText());
        String type = "rescue".equals(post.getType()) ? "rescue_response" : "square_reply";
        for (Long uid : commenterIds) {
            if (uid != null && !uid.equals(userId)) {
                String text = snippet.isEmpty() ? "广播已解决" : "广播已解决: " + snippet;
                messageService.create(uid, type, text, "square_post", postId);
            }
        }
        return true;
    }

    private boolean canMarkSolvedRescue(SquarePost post, Long userId) {
        if (post.getUserId() != null && post.getUserId().equals(userId)) {
            return true;
        }
        if (post.getRescueActivityId() == null) {
            return false;
        }
        RescueActivity activity = rescueActivityRepository.findById(post.getRescueActivityId()).orElse(null);
        if (activity != null && activity.getCreatedBy() != null && activity.getCreatedBy().equals(userId)) {
            return true;
        }
        List<com.ourcat.backend.models.RescueTask> tasks = rescueService
                .getTasksByActivityId(post.getRescueActivityId());
        for (com.ourcat.backend.models.RescueTask task : tasks) {
            if (task.getAssigneeUserId() != null && task.getAssigneeUserId().equals(userId)) {
                return true;
            }
            if (task.getAssignerUserId() != null && task.getAssignerUserId().equals(userId)) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    public boolean deletePost(Long postId, Long userId, int userRole) {
        Optional<SquarePost> opt = squarePostRepository.findById(postId);
        if (opt.isEmpty())
            return false;
        SquarePost post = opt.get();

        // Check author role
        Optional<com.ourcat.backend.models.User> authorOpt = userRepository.findById(post.getUserId());
        int authorRole = authorOpt.map(com.ourcat.backend.models.User::getRole).orElse(1);

        // Role 3 (Admin) can delete all
        if (userRole >= 3) {
            completeLinkedRescue(post);
            squarePostRepository.delete(post);
            return true;
        }

        // Role 2 (Volunteer) can delete posts by Role 1 and Role 2
        if (userRole == 2) {
            if (authorRole <= 2) {
                completeLinkedRescue(post);
                squarePostRepository.delete(post);
                return true;
            }
            return false;
        }

        // Role 1 (User) can only delete their own posts
        if (post.getUserId().equals(userId)) {
            completeLinkedRescue(post);
            squarePostRepository.delete(post);
            return true;
        }

        return false;
    }

    public List<SquareComment> getComments(Long postId, int page, int size) {
        return squareCommentRepository.findBySquarePostIdOrderByCreatedAtAsc(postId, PageRequest.of(page, size));
    }

    @Transactional
    public SquareComment addComment(Long postId, Long userId, String content) {
        SquareComment c = new SquareComment();
        c.setSquarePostId(postId);
        c.setUserId(userId);
        c.setContent(content);
        SquareComment saved = squareCommentRepository.save(c);
        squarePostRepository.findById(postId).ifPresent(post -> {
            if (!post.getUserId().equals(userId)) {
                String snippet = buildSnippet(post.getText());
                String type = "rescue".equals(post.getType()) ? "rescue_response" : "square_reply";
                String text = snippet.isEmpty() ? "有人回复了你的广播" : "有人回复了你的广播: " + snippet;
                messageService.create(post.getUserId(), type, text, "square_post", postId);
            }
        });
        return saved;
    }

    public Optional<com.ourcat.backend.models.User> getAuthor(Long userId) {
        return userRepository.findById(userId);
    }

    private String buildSnippet(String text) {
        if (text == null)
            return "";
        String t = text.trim();
        if (t.isEmpty())
            return "";
        if (t.length() <= 20)
            return t;
        return t.substring(0, 20) + "...";
    }

    private void completeLinkedRescue(SquarePost post) {
        if (post.getRescueActivityId() == null) {
            return;
        }
        rescueActivityRepository.findById(post.getRescueActivityId()).ifPresent(activity -> {
            if (!"completed".equals(activity.getStatus())) {
                activity.setStatus("completed");
                activity.setCompletedAt(java.time.Instant.now());
                rescueActivityRepository.save(activity);
            }
        });
    }
}
