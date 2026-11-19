package net.pretupid.client.util;

public final class VersionOrder {
	private VersionOrder() {
	}

	public static int compare(String left, String right) {
		String[] a = left.split("[^0-9]+");
		String[] b = right.split("[^0-9]+");
		int length = Math.max(a.length, b.length);
		for (int i = 0; i < length; i++) {
			long x = i < a.length && !a[i].isEmpty() ? parse(a[i]) : 0;
			long y = i < b.length && !b[i].isEmpty() ? parse(b[i]) : 0;
			if (x != y) {
				return Long.compare(x, y);
			}
		}
		return 0;
	}

	private static long parse(String value) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException exception) {
			return 0;
		}
	}
}
