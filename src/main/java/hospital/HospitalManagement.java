package hospital;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/** CareDesk: web server for the Hospital Management project (REST API + the frontend in /static). */
public class HospitalManagement {
    private static final Set<String> ROLES = Set.of("Receptionist", "Doctor", "Nurse");
    private static final Set<String> GENDERS = Set.of("Male", "Female", "Other");
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9_.]{3,24}$");
    private static final String CSP = "default-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
            + "font-src https://fonts.gstatic.com; img-src 'self' data:; frame-ancestors 'none'";

    private static Db db;
    private static Auth auth;
    private static Gemini gemini;

    static class ApiError extends RuntimeException {
        final int status;
        ApiError(int status, String message) { super(message); this.status = status; }
    }

    public static void main(String[] args) throws Exception {
        String dbUrl = Env.get("DATABASE_URL");
        if (dbUrl == null) die("DATABASE_URL is not set. Copy .env.example to .env and add your database connection string.");
        String adminUser = Env.get("ADMIN_USER", "admin").trim().toLowerCase();
        String adminPass = Env.get("ADMIN_PASSWORD");
        if (adminPass == null || adminPass.length() < 8) die("ADMIN_PASSWORD must be set and at least 8 characters long.");

        db = new Db(dbUrl);
        db.ensureAdmin(adminUser, Auth.hash(adminPass));
        auth = new Auth(Env.get("SESSION_SECRET"));
        gemini = new Gemini(Env.get("GEMINI_API_KEY"), Env.get("GEMINI_MODEL", "gemini-flash-latest"));

        int port = Integer.parseInt(Env.get("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", HospitalManagement::handle);
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();
        System.out.println("CareDesk is running on http://localhost:" + port
                + (gemini.enabled() ? "" : "  (AI is off: GEMINI_API_KEY is not set)"));
    }

    private static void die(String message) {
        System.err.println("Cannot start: " + message);
        System.exit(1);
    }

    // ---------------------------------------------------------------- routing
    private static void handle(HttpExchange ex) throws IOException {
        try {
            ex.getResponseHeaders().add("X-Content-Type-Options", "nosniff");
            ex.getResponseHeaders().add("X-Frame-Options", "DENY");
            ex.getResponseHeaders().add("Referrer-Policy", "same-origin");
            ex.getResponseHeaders().add("Content-Security-Policy", CSP);
            String path = ex.getRequestURI().getPath();
            if (path.equals("/healthz")) send(ex, 200, "text/plain; charset=utf-8", "ok".getBytes(StandardCharsets.UTF_8));
            else if (path.startsWith("/api/")) api(ex, path.substring(5));
            else staticFile(ex, path);
        } catch (ApiError e) {
            json(ex, e.status, Map.of("error", e.getMessage()));
        } catch (NumberFormatException e) {
            json(ex, 400, Map.of("error", "Enter a valid number."));
        } catch (DateTimeException e) {
            json(ex, 400, Map.of("error", "Enter a valid date (YYYY-MM-DD)."));
        } catch (IllegalArgumentException e) {
            json(ex, 400, Map.of("error", e.getMessage() == null ? "Invalid request." : e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            json(ex, 500, Map.of("error", "Something went wrong on the server. Please try again."));
        } finally {
            ex.close();
        }
    }

    private static void api(HttpExchange ex, String route) throws Exception {
        String m = ex.getRequestMethod();
        boolean get = m.equals("GET"), post = m.equals("POST");

        if (route.equals("login") && post) { login(ex); return; }
        if (route.equals("logout") && post) {
            ex.getResponseHeaders().add("Set-Cookie", cookie(ex, "", 0));
            json(ex, 200, Map.of("ok", true));
            return;
        }

        User me = currentUser(ex);
        if (me == null) throw new ApiError(401, "Please sign in to continue.");
        if (post) requireJson(ex);

        switch (route) {
            case "me":
                json(ex, 200, me);
                return;
            case "patients":
                if (get) { json(ex, 200, db.patients()); return; }
                if (post) {
                    Map<String, Object> b = body(ex);
                    String gender = str(b, "gender", 1, 10);
                    if (!GENDERS.contains(gender)) throw new IllegalArgumentException("Choose a valid gender.");
                    db.addPatient(str(b, "name", 1, 60), num(b, "age", 0, 120), gender);
                    json(ex, 201, Map.of("ok", true));
                    return;
                }
                break;
            case "doctors":
                if (get) { json(ex, 200, db.doctors()); return; }
                if (post) {
                    Map<String, Object> b = body(ex);
                    db.addDoctor(str(b, "name", 1, 60), str(b, "specialty", 1, 40));
                    json(ex, 201, Map.of("ok", true));
                    return;
                }
                break;
            case "appointments":
                if (get) { json(ex, 200, db.appointments()); return; }
                if (post) {
                    Map<String, Object> b = body(ex);
                    LocalDate date = LocalDate.parse(str(b, "date", 10, 10));
                    if (!db.addAppointment(num(b, "patientId", 1, Integer.MAX_VALUE), num(b, "doctorId", 1, Integer.MAX_VALUE), date))
                        throw new ApiError(400, "That patient or doctor does not exist.");
                    json(ex, 201, Map.of("ok", true));
                    return;
                }
                break;
            case "ai":
                if (post) { ai(ex, me); return; }
                break;
            default:
                if (route.equals("users") || route.startsWith("users/")) { users(ex, me, route); return; }
                throw new ApiError(404, "Not found.");
        }
        throw new ApiError(405, "Method not allowed.");
    }

    // ------------------------------------------------------------------ login
    private static void login(HttpExchange ex) throws Exception {
        requireJson(ex);
        Map<String, Object> b = body(ex);
        String username = str(b, "username", 1, 40).toLowerCase(), password = raw(b, "password");
        String ipKey = "ip:" + clientIp(ex), userKey = "user:" + username;
        if (auth.blocked(ipKey) || auth.blocked(userKey))
            throw new ApiError(429, "Too many failed attempts. Try again in 10 minutes.");

        Db.UserRow row = db.findUser(username);
        boolean ok = Auth.verify(password, row != null ? row.hash() : Auth.DUMMY_HASH) && row != null;
        if (!ok) {
            auth.failed(ipKey);
            auth.failed(userKey);
            throw new ApiError(401, "Wrong username or password. Check both and try again.");
        }
        auth.succeeded(ipKey);
        auth.succeeded(userKey);
        ex.getResponseHeaders().add("Set-Cookie", cookie(ex, auth.token(row.user()), 8 * 3600));
        json(ex, 200, row.user());
    }

    private static User currentUser(HttpExchange ex) throws Exception {
        String header = ex.getRequestHeaders().getFirst("Cookie");
        if (header == null) return null;
        for (String part : header.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals("hms_session")) {
                int id = auth.userId(kv[1]);
                return id < 0 ? null : db.userById(id);
            }
        }
        return null;
    }

    private static String cookie(HttpExchange ex, String value, int maxAge) {
        boolean https = "https".equalsIgnoreCase(ex.getRequestHeaders().getFirst("X-Forwarded-Proto"));
        return "hms_session=" + value + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + maxAge + (https ? "; Secure" : "");
    }

    private static String clientIp(HttpExchange ex) {
        String xff = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return ex.getRemoteAddress().getAddress().getHostAddress();
    }

    // ------------------------------------------------------------ staff (admin)
    private static void users(HttpExchange ex, User me, String route) throws Exception {
        if (!"Admin".equals(me.getRole())) throw new ApiError(403, "Only admins can manage staff accounts.");
        String m = ex.getRequestMethod();
        if (route.equals("users") && m.equals("GET")) { json(ex, 200, db.users()); return; }
        if (route.equals("users") && m.equals("POST")) {
            Map<String, Object> b = body(ex);
            String username = str(b, "username", 1, 40).toLowerCase(), role = str(b, "role", 1, 20), password = raw(b, "password");
            if (!USERNAME.matcher(username).matches()) throw new IllegalArgumentException("Username needs 3 to 24 letters or numbers.");
            if (!ROLES.contains(role)) throw new IllegalArgumentException("Choose a valid role.");
            if (password.length() < 6 || password.length() > 100) throw new IllegalArgumentException("Password needs 6 to 100 characters.");
            if (!db.addUser(str(b, "name", 1, 60), username, Auth.hash(password), role))
                throw new ApiError(409, "That username is already taken.");
            json(ex, 201, Map.of("ok", true));
            return;
        }
        if (route.startsWith("users/") && m.equals("DELETE")) {
            if (!db.deleteUser(Integer.parseInt(route.substring(6)))) throw new ApiError(404, "Account not found, or it is an admin account.");
            json(ex, 200, Map.of("ok", true));
            return;
        }
        throw new ApiError(405, "Method not allowed.");
    }

    // --------------------------------------------------------------------- AI
    private static void ai(HttpExchange ex, User me) throws Exception {
        if (!gemini.enabled()) throw new ApiError(503, "AI is not set up yet. Ask the administrator to add a GEMINI_API_KEY.");
        if (!auth.rate("ai:" + me.getId(), 12)) throw new ApiError(429, "You are asking too fast. Wait a minute and try again.");
        Map<String, Object> b = body(ex);
        String mode = str(b, "mode", 2, 6), text = b.get("text") instanceof String ? ((String) b.get("text")).trim() : "";
        if (!mode.equals("ins") && (text.isEmpty() || text.length() > 1000)) throw new IllegalArgumentException("Write between 1 and 1000 characters.");

        // Only the data each feature needs is sent to the AI service.
        String sys = "You are CareDesk AI, the assistant inside a hospital management app. Reply in the same language and script "
                + "the user wrote in (English, Hindi or Hinglish). Be concise and plain. Today is " + LocalDate.now() + ". ";
        String all = Json.str(Map.of("patients", db.patients(), "doctors", db.doctors(), "appointments", db.appointments()));
        String prompt;
        switch (mode) {
            case "ask":
                prompt = sys + "Answer the user's question using only this hospital data (JSON): " + all + "\nQuestion: " + text;
                break;
            case "sym":
                prompt = sys + "The user describes symptoms. Suggest the most suitable specialty and name a matching doctor from this list if one exists: "
                        + Json.str(db.doctors()) + ". Give urgency as Low, Medium or High with one reason. Do not diagnose. "
                        + "If symptoms sound like an emergency (chest pain, trouble breathing, stroke signs, heavy bleeding), say to seek emergency care now. "
                        + "End with one line saying this is guidance, not a diagnosis, and to see a doctor.\nSymptoms: " + text;
                break;
            case "note":
                prompt = sys + "Turn the doctor's rough notes into a clean visit summary with short lines for Complaint, Findings, Advice, Follow-up. "
                        + "Do not invent facts that are not in the notes.\nNotes: " + text;
                break;
            case "ins":
                prompt = sys + "Write 3 short insight bullets about this hospital data: busiest doctor, upcoming load, and one suggestion. Data: " + all;
                break;
            case "book":
                prompt = sys + "Extract the booking from the request. Return only JSON: {\"patientId\":number|null,\"doctorId\":number|null,"
                        + "\"date\":\"YYYY-MM-DD\"|null,\"reply\":\"one short sentence\"}. Match names loosely against this data: " + all + "\nRequest: " + text;
                break;
            default:
                throw new IllegalArgumentException("Unknown AI mode.");
        }

        String answer;
        try {
            answer = gemini.ask(prompt, mode.equals("book"));
        } catch (IOException | InterruptedException e) {
            System.err.println("AI error: " + e.getMessage());
            throw new ApiError(502, "CareDesk AI could not answer right now. Try again in a moment.");
        }
        if (!mode.equals("book")) { json(ex, 200, Map.of("text", answer)); return; }

        try {
            Map<String, Object> r = Json.obj(answer.replaceAll("^```(json)?|```$", "").trim());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("patientId", r.get("patientId") instanceof Number ? ((Number) r.get("patientId")).intValue() : null);
            out.put("doctorId", r.get("doctorId") instanceof Number ? ((Number) r.get("doctorId")).intValue() : null);
            String date = null;
            try { date = LocalDate.parse(String.valueOf(r.get("date"))).toString(); } catch (DateTimeException ignored) { }
            out.put("date", date);
            String reply = r.get("reply") instanceof String ? (String) r.get("reply") : "";
            out.put("reply", reply.length() > 300 ? reply.substring(0, 300) : reply);
            json(ex, 200, out);
        } catch (IllegalArgumentException e) {
            throw new ApiError(502, "CareDesk AI could not understand that booking. Try again with the patient, doctor and date.");
        }
    }

    // ---------------------------------------------------------------- helpers
    private static void requireJson(HttpExchange ex) {
        String type = ex.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.toLowerCase().startsWith("application/json")) throw new ApiError(415, "Send JSON.");
    }

    private static Map<String, Object> body(HttpExchange ex) throws IOException {
        byte[] bytes = ex.getRequestBody().readNBytes(20_000);
        if (bytes.length >= 20_000) throw new ApiError(413, "Request is too large.");
        return Json.obj(new String(bytes, StandardCharsets.UTF_8));
    }

    private static String raw(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (!(v instanceof String)) throw new IllegalArgumentException("Missing field: " + key + ".");
        return (String) v;
    }

    private static String str(Map<String, Object> m, String key, int min, int max) {
        String s = raw(m, key).trim();
        if (s.length() < min || s.length() > max) throw new IllegalArgumentException("Check the " + key + " field (" + min + " to " + max + " characters).");
        return s;
    }

    private static int num(Map<String, Object> m, String key, int min, int max) {
        Object v = m.get(key);
        if (!(v instanceof Number)) throw new IllegalArgumentException("Missing field: " + key + ".");
        double d = ((Number) v).doubleValue();
        if (d != Math.floor(d) || d < min || d > max) throw new IllegalArgumentException("Check the " + key + " field (" + min + " to " + max + ").");
        return (int) d;
    }

    private static void json(HttpExchange ex, int status, Object body) throws IOException {
        send(ex, status, "application/json; charset=utf-8", Json.str(body).getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int status, String type, byte[] bytes) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static void staticFile(HttpExchange ex, String path) throws IOException {
        if (path.equals("/")) path = "/index.html";
        if (path.contains("..") || path.contains("\\")) { send(ex, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8)); return; }
        try (InputStream in = HospitalManagement.class.getResourceAsStream("/static" + path)) {
            if (in == null) { send(ex, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8)); return; }
            String type = path.endsWith(".html") ? "text/html; charset=utf-8" : path.endsWith(".css") ? "text/css; charset=utf-8"
                    : path.endsWith(".js") ? "text/javascript; charset=utf-8" : path.endsWith(".svg") ? "image/svg+xml"
                    : path.endsWith(".png") ? "image/png" : "application/octet-stream";
            ex.getResponseHeaders().set("Cache-Control", path.endsWith(".html") ? "no-cache" : "public, max-age=300");
            send(ex, 200, type, in.readAllBytes());
        }
    }
}
