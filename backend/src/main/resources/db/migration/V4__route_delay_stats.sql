CREATE TABLE route_delay_stats (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    route_id VARCHAR(255) NOT NULL,
    direction_id INTEGER NOT NULL,
    hour_bucket SMALLINT NOT NULL,

    sample_count BIGINT NOT NULL DEFAULT 0,
    mean_delay DOUBLE PRECISION NOT NULL DEFAULT 0,
    m2 DOUBLE PRECISION NOT NULL DEFAULT 0,

    last_reset_date DATE NOT NULL,
    
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT route_delay_stats_hour_check
        CHECK (hour_bucket >= 0 AND hour_bucket <= 23),

    CONSTRAINT route_delay_stats_unique_bucket
        UNIQUE (route_id, direction_id, hour_bucket)
);