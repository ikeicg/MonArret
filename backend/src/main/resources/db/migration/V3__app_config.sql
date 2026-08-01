-- V3__app_config.sql
-- Application configuration and state (key-value pairs)

CREATE TABLE app_config (
  key VARCHAR(100) PRIMARY KEY,
  value TEXT NOT NULL,
  description VARCHAR(500),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Initial config entries
INSERT INTO app_config (key, value, description) VALUES
  ('gtfs_last_reload', '2026-01-01', 'Timestamp of last successful GTFS reload'),
  ('gtfs_version', 'unknown', 'Current GTFS feed version from feed_info'),
  ('gtfs_next_check', '2026-01-01', 'Scheduled time from feed end date'),
  ('app_version', '1.0.0', 'Application version'),
  ('maintenance_mode', 'false', 'Is app in maintenance mode');