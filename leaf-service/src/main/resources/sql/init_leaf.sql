-- Meituan Leaf: Database initialization
CREATE DATABASE IF NOT EXISTS `live_community` DEFAULT CHARACTER SET utf8mb4;

USE `live_community`;

CREATE TABLE IF NOT EXISTS `leaf_alloc` (
    `biz_tag` VARCHAR(128) NOT NULL DEFAULT '',
    `max_id` BIGINT NOT NULL DEFAULT 1,
    `step` INT NOT NULL,
    `description` VARCHAR(256) DEFAULT NULL,
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`biz_tag`)
) ENGINE=InnoDB;

-- Insert biz tags for note and comment ID generation
INSERT INTO `leaf_alloc` (`biz_tag`, `max_id`, `step`, `description`) VALUES
('note', 1, 1000, 'Note ID segment'),
('comment', 1, 1000, 'Comment ID segment')
ON DUPLICATE KEY UPDATE `max_id` = VALUES(`max_id`);
