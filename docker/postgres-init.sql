-- Runs once, when the data volume is empty. One Postgres server, one database
-- per service: no service can read another's tables.
CREATE DATABASE gateway;
CREATE DATABASE carrier;
