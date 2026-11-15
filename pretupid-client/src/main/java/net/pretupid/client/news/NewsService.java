package net.pretupid.client.news;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.network.ApiClient;
import net.pretupid.client.network.Json;

public final class NewsService {
	private static final long REFRESH_MS = 15 * 60_000L;

	private final ApiClient api;
	private volatile List<Post> posts = List.of();
	private volatile boolean loaded;
	private volatile String error;
	private volatile boolean loading;
	private long nextRefresh;

	public record Post(String id, String title, String summary, String category, boolean pinned, long publishedAt, String body) {
	}

	public NewsService(ApiClient api) {
		this.api = api;
	}

	public List<Post> posts() {
		return posts;
	}

	public boolean loaded() {
		return loaded;
	}

	public boolean loading() {
		return loading;
	}

	public String error() {
		return error;
	}

	public void tick() {
		if (System.currentTimeMillis() >= nextRefresh) {
			refresh();
		}
	}

	public void refresh() {
		if (loading) {
			return;
		}
		loading = true;
		nextRefresh = System.currentTimeMillis() + REFRESH_MS;
		api.json("GET", "/news", null, null).whenComplete((response, failure) -> Minecraft.getInstance().execute(() -> {
			loading = false;
			if (failure != null) {
				error = ApiClient.describe(failure);
				nextRefresh = System.currentTimeMillis() + 60_000L;
				return;
			}
			List<Post> parsed = new ArrayList<>();
			for (JsonElement element : Json.array(response, "posts")) {
				if (!element.isJsonObject() || parsed.size() >= 25) {
					continue;
				}
				JsonObject object = element.getAsJsonObject();
				String id = Json.string(object, "id", 64);
				String title = Json.string(object, "title", 120);
				String body = Json.string(object, "body", 20_000);
				if (id == null || title == null || body == null) {
					continue;
				}
				String summary = Json.string(object, "summary", 240);
				String category = Json.string(object, "category", 16);
				parsed.add(new Post(id, title, summary == null ? "" : summary, category == null ? "news" : category,
					Json.bool(object, "pinned", false), Json.number(object, "publishedAt", 0L), body));
			}
			posts = Collections.unmodifiableList(parsed);
			loaded = true;
			error = null;
		}));
	}

	public long latestPublished() {
		long latest = 0L;
		for (Post post : posts) {
			latest = Math.max(latest, post.publishedAt());
		}
		return latest;
	}

	public long seenUntil() {
		try {
			return Long.parseLong(PretupidClient.get().settings().newsSeen.get());
		} catch (NumberFormatException exception) {
			return 0L;
		}
	}

	public int unreadCount() {
		long seen = seenUntil();
		int count = 0;
		for (Post post : posts) {
			if (post.publishedAt() > seen) {
				count++;
			}
		}
		return count;
	}

	public boolean isUnread(Post post) {
		return post.publishedAt() > seenUntil();
	}

	public void markAllSeen() {
		long latest = latestPublished();
		if (latest > seenUntil()) {
			PretupidClient.get().settings().newsSeen.set(Long.toString(latest));
		}
	}
}
