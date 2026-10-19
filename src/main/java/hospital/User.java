package hospital;

import java.util.LinkedHashMap;
import java.util.Map;

/** A staff account. The password hash is never part of this object. */
public class User implements JsonItem {
    private final int id;
    private final String name;
    private final String username;
    private final String role;

    public User(int id, String name, String username, String role) {
        this.id = id;
        this.name = name;
        this.username = username;
        this.role = role;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getUsername() { return username; }
    public String getRole() { return role; }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("username", username);
        m.put("role", role);
        return m;
    }
}
