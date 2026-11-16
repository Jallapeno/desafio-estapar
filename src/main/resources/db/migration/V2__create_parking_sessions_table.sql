CREATE TABLE parking_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    license_plate VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    entry_time DATETIME(3) NOT NULL,
    exit_time DATETIME(3) NULL,
    spot_id BIGINT NULL,
    sector_code VARCHAR(20) NULL,
    occupancy_rate_at_entry DECIMAL(5, 4) NOT NULL,
    dynamic_multiplier DECIMAL(5, 2) NOT NULL,
    charged_amount DECIMAL(10, 2) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_parking_sessions_spot
        FOREIGN KEY (spot_id) REFERENCES parking_spots (id),
    CONSTRAINT fk_parking_sessions_sector
        FOREIGN KEY (sector_code) REFERENCES sectors (sector),
    INDEX idx_parking_sessions_plate_status (license_plate, status),
    INDEX idx_parking_sessions_status (status),
    INDEX idx_parking_sessions_revenue (sector_code, status, exit_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
