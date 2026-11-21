-- Runs once on an empty volume; secrets are read by the bootstrap role.
SELECT format('CREATE ROLE belsen_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L',
              btrim(pg_read_file('/run/secrets/db_password'), E'\r\n')) \gexec
SELECT format('CREATE ROLE belsen_backup LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L',
              btrim(pg_read_file('/run/secrets/postgres_backup_password'), E'\r\n')) \gexec
ALTER DATABASE belsen OWNER TO belsen_app;
ALTER SCHEMA public OWNER TO belsen_app;
REVOKE ALL ON DATABASE belsen FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE belsen TO belsen_backup;
GRANT USAGE ON SCHEMA public TO belsen_backup;
ALTER DEFAULT PRIVILEGES FOR ROLE belsen_app IN SCHEMA public GRANT SELECT ON TABLES TO belsen_backup;
ALTER DEFAULT PRIVILEGES FOR ROLE belsen_app IN SCHEMA public GRANT SELECT ON SEQUENCES TO belsen_backup;
