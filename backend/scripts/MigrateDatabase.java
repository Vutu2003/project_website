import org.flywaydb.core.Flyway;

// Local setup uses the same Flyway libraries, checksums and transaction setting as Spring Boot.
class MigrateDatabase {
    public static void main(String[] args) {
        var env = System.getenv();
        String database = env.get("DB_NAME");
        if (!"127.0.0.1".equals(env.get("DB_HOST")) || env.get("DB_PORT") == null
                || !env.get("DB_PORT").matches("[0-9]+"))
            throw new IllegalArgumentException("Explicit local PostgreSQL host/port required");
        if (!("medical_maintenance_v2".equals(database)
                || (database != null && database.matches("medical_maintenance_[a-z0-9_]+_test"))))
            throw new IllegalArgumentException("Canonical V2 or explicitly named project test database required");
        Flyway.configure().dataSource("jdbc:postgresql://" + env.get("DB_HOST") + ":"
                + env.get("DB_PORT") + "/" + database, env.get("DB_USERNAME"), env.get("DB_PASSWORD"))
                .locations("filesystem:" + args[0]).executeInTransaction(false)
                .cleanDisabled(true).validateMigrationNaming(true).load().migrate();
    }
}
