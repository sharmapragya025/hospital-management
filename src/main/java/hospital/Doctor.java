package hospital;

import java.util.LinkedHashMap;
import java.util.Map;

public class Doctor implements JsonItem {
    private final int id;
    private final String name;
    private final String specialty;

    public Doctor(int id, String name, String specialty) {
        this.id = id;
        this.name = name;
        this.specialty = specialty;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getSpecialty() { return specialty; }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("specialty", specialty);
        return m;
    }

    @Override
    public String toString() {
        return "Doctor ID: " + id + ", Name: " + name + ", Specialty: " + specialty;
    }
}
