-- One vote per user per post: like or dislike (or none). Counts on post are derived from this table.
CREATE TABLE IF NOT EXISTS post_votes (
    user_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    is_like BIT(1) NOT NULL,
    PRIMARY KEY (user_id, post_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
);

CREATE INDEX idx_post_votes_post_id ON post_votes(post_id);
