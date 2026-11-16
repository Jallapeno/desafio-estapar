CREATE TABLE sectors (
    sector VARCHAR(20) NOT NULL,
    base_price DECIMAL(10, 2) NOT NULL,
    max_capacity INT NOT NULL,
    PRIMARY KEY (sector)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE parking_spots (
    id BIGINT NOT NULL,
    sector_code VARCHAR(20) NOT NULL,
    lat DECIMAL(10, 6) NOT NULL,
    lng DECIMAL(10, 6) NOT NULL,
    occupied BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_parking_spots_sector
        FOREIGN KEY (sector_code) REFERENCES sectors (sector),
    INDEX idx_parking_spots_coordinates (lat, lng),
    INDEX idx_parking_spots_sector_occupied (sector_code, occupied)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
