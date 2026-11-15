package net.pretupid.client.input;

import java.util.ArrayDeque;

public final class ClickTracker {
	private static final long WINDOW_MS = 1000L;

	private final ArrayDeque<Long> left = new ArrayDeque<>();
	private final ArrayDeque<Long> right = new ArrayDeque<>();

	public void recordLeft() {
		record(left);
	}

	public void recordRight() {
		record(right);
	}

	public int leftCps() {
		return count(left);
	}

	public int rightCps() {
		return count(right);
	}

	private static void record(ArrayDeque<Long> clicks) {
		clicks.addLast(System.currentTimeMillis());
		while (clicks.size() > 64) {
			clicks.removeFirst();
		}
	}

	private static int count(ArrayDeque<Long> clicks) {
		long threshold = System.currentTimeMillis() - WINDOW_MS;
		while (!clicks.isEmpty() && clicks.peekFirst() < threshold) {
			clicks.removeFirst();
		}
		return clicks.size();
	}
}
