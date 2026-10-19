package hospital;

import java.util.LinkedHashMap;
import java.util.Map;

public class Appointment implements JsonItem {
    private final int id;
    private final Patient patient;
    private final Doctor doctor;
    private final String date;

    public Appointment(int id, Patient patient, Doctor doctor, String date) {
        this.id = id;
        this.patient = patient;
        this.doctor = doctor;
        this.date = date;
    }

    public int getId() { return id; }
    public Patient getPatient() { return patient; }
    public Doctor getDoctor() { return doctor; }
    public String getDate() { return date; }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("patientId", patient.getId());
        m.put("doctorId", doctor.getId());
        m.put("date", date);
        return m;
    }

    @Override
    public String toString() {
        return "Appointment: [Patient: " + patient + ", Doctor: " + doctor + ", Date: " + date + "]";
    }
}
