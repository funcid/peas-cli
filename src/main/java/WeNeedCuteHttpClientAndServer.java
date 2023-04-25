import java.util.HashMap;
import java.util.Map;

public final class WeNeedCuteHttpClientAndServer {
	private WeNeedCuteHttpClientAndServer() {}

	static Map<String, String> queryToMap(String query) {
		if (query == null) {
			return null;
		}
		Map<String, String> result = new HashMap<>();
		for (String param : query.split("&")) {
			String[] entry = param.split("=");
			if (entry.length > 1) {
				result.put(entry[0], entry[1]);
			} else {
				result.put(entry[0], "");
			}
		}
		return result;
	}
}
