package hospital;

import java.util.Map;

/** Anything that can describe itself as a JSON object. */
public interface JsonItem {
    Map<String, Object> toMap();
}
