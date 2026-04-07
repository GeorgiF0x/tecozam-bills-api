-- =============================================================================
-- V1__create_schema.sql
-- Tecozam Bills Manager - Schema setup
-- Target: Microsoft SQL Server (T-SQL)
-- =============================================================================

-- Create the default schema if it does not already exist.
-- Flyway runs migrations within the dbo schema by default.
-- This migration ensures the baseline is in place.

IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = N'dbo')
BEGIN
    EXEC('CREATE SCHEMA [dbo] AUTHORIZATION [dbo]');
END
GO

-- =============================================================================
-- Shared setup: enable snapshot isolation for better concurrency on reads
-- =============================================================================
-- (Uncomment below if your DBA approves snapshot isolation)
-- ALTER DATABASE CURRENT SET ALLOW_SNAPSHOT_ISOLATION ON;
-- ALTER DATABASE CURRENT SET READ_COMMITTED_SNAPSHOT ON;
-- GO
