CREATE TABLE IF NOT EXISTS cats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    color VARCHAR(64),
    feature TEXT,
    personality VARCHAR(64),
    ai_embedding LONGBLOB
);

CREATE TABLE IF NOT EXISTS cat_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lat DOUBLE NOT NULL,
    lng DOUBLE NOT NULL,
    report_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    image_url VARCHAR(512),
    description VARCHAR(500),
    cat_id BIGINT,
    user_id BIGINT NOT NULL,
    FOREIGN KEY (cat_id) REFERENCES cats(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_cat_reports_lat_lng ON cat_reports(lat, lng);
CREATE INDEX idx_cat_reports_cat_id ON cat_reports(cat_id);
