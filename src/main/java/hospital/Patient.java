package hospital;

import java.util.LinkedHashMap;
import java.util.Map;

public class Patient implements JsonItem {
    private final int id;
    private final String name;
    private final int age;
    private final String gender;

    public Patient(int id, String name, int age, String gender) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.gender = gender;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public int getAge() { return age; }
    public String getGender() { return gender; }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("age", age);
        m.put("gender", gender);
        return m;
    }

    @Override
    public String toString() {
        return "Patient ID: " + id + ", Name: " + name + ", Age: " + age + ", Gender: " + gender;
    }
}
