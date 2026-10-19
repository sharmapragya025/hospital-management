package hospital;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** All database access. Talks to PostgreSQL (for example Neon) over JDBC. */
final class Db {
    record UserRow(User user, String hash) { }

    private interface Mapper<T> { T map(ResultSet rs) throws SQLException; }

    private final String url;
    private final Properties props = new Properties();
    private Connection conn;

    Db(String databaseUrl) throws SQLException {
        String u = databaseUrl.trim();
        if (u.startsWith("jdbc:")) {
            url = u;
        } else {
            URI uri = URI.create(u.replaceFirst("^postgres(ql)?://", "http://"));
            if (uri.getUserInfo() != null) {
                String[] ui = uri.getUserInfo().split(":", 2);
                props.setProperty("user", ui[0]);
                if (ui.length > 1) props.setProperty("password", ui[1]);
            }
            url = "jdbc:postgresql://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "")
                    + uri.getPath() + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
        }
        createTables();
    }

    private synchronized Connection connection() throws SQLException {
        if (conn == null || conn.isClosed() || !conn.isValid(3)) conn = DriverManager.getConnection(url, props);
        return conn;
    }

    private synchronized <T> List<T> query(String sql, Mapper<T> mapper, Object... params) throws SQLException {
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) out.add(mapper.map(rs));
                return out;
            }
        }
    }

    private synchronized int exec(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
    }

    private synchronized void createTables() throws SQLException {
        try (Statement st = connection().createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS users(id SERIAL PRIMARY KEY, name TEXT NOT NULL, "
                    + "username TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL, role TEXT NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS patients(id SERIAL PRIMARY KEY, name TEXT NOT NULL, "
                    + "age INT NOT NULL, gender TEXT NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS doctors(id SERIAL PRIMARY KEY, name TEXT NOT NULL, "
                    + "specialty TEXT NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS appointments(id SERIAL PRIMARY KEY, "
                    + "patient_id INT NOT NULL REFERENCES patients(id), "
                    + "doctor_id INT NOT NULL REFERENCES doctors(id), appt_date DATE NOT NULL)");
        }
    }

    // ---- patients ----
    List<Patient> patients() throws SQLException {
        return query("SELECT id, name, age, gender FROM patients ORDER BY id",
                rs -> new Patient(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getString(4)));
    }

    void addPatient(String name, int age, String gender) throws SQLException {
        exec("INSERT INTO patients(name, age, gender) VALUES(?,?,?)", name, age, gender);
    }

    // ---- doctors ----
    List<Doctor> doctors() throws SQLException {
        return query("SELECT id, name, specialty FROM doctors ORDER BY id",
                rs -> new Doctor(rs.getInt(1), rs.getString(2), rs.getString(3)));
    }

    void addDoctor(String name, String specialty) throws SQLException {
        exec("INSERT INTO doctors(name, specialty) VALUES(?,?)", name, specialty);
    }

    // ---- appointments ----
    List<Appointment> appointments() throws SQLException {
        return query("SELECT a.id, a.appt_date, p.id, p.name, p.age, p.gender, d.id, d.name, d.specialty "
                        + "FROM appointments a JOIN patients p ON p.id = a.patient_id "
                        + "JOIN doctors d ON d.id = a.doctor_id ORDER BY a.appt_date, a.id",
                rs -> new Appointment(rs.getInt(1),
                        new Patient(rs.getInt(3), rs.getString(4), rs.getInt(5), rs.getString(6)),
                        new Doctor(rs.getInt(7), rs.getString(8), rs.getString(9)),
                        rs.getDate(2).toLocalDate().toString()));
    }

    /** Returns false when the patient or the doctor does not exist. */
    boolean addAppointment(int patientId, int doctorId, LocalDate date) throws SQLException {
        try {
            exec("INSERT INTO appointments(patient_id, doctor_id, appt_date) VALUES(?,?,?)",
                    patientId, doctorId, java.sql.Date.valueOf(date));
            return true;
        } catch (SQLException e) {
            if ("23503".equals(e.getSQLState())) return false;
            throw e;
        }
    }

    // ---- users ----
    private static User user(ResultSet rs) throws SQLException {
        return new User(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4));
    }

    List<User> users() throws SQLException {
        return query("SELECT id, name, username, role FROM users ORDER BY id", Db::user);
    }

    User userById(int id) throws SQLException {
        List<User> l = query("SELECT id, name, username, role FROM users WHERE id = ?", Db::user, id);
        return l.isEmpty() ? null : l.get(0);
    }

    UserRow findUser(String username) throws SQLException {
        List<UserRow> l = query("SELECT id, name, username, role, password_hash FROM users WHERE username = ?",
                rs -> new UserRow(user(rs), rs.getString(5)), username);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Returns false when the username is already taken. */
    boolean addUser(String name, String username, String hash, String role) throws SQLException {
        try {
            exec("INSERT INTO users(name, username, password_hash, role) VALUES(?,?,?,?)", name, username, hash, role);
            return true;
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) return false;
            throw e;
        }
    }

    /** The admin account always matches the ADMIN_USER and ADMIN_PASSWORD settings. */
    void ensureAdmin(String username, String hash) throws SQLException {
        exec("INSERT INTO users(name, username, password_hash, role) VALUES('Admin', ?, ?, 'Admin') "
                + "ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash, role = 'Admin'",
                username, hash);
    }

    /** Admin accounts cannot be removed. */
    boolean deleteUser(int id) throws SQLException {
        return exec("DELETE FROM users WHERE id = ? AND role <> 'Admin'", id) > 0;
    }
}
