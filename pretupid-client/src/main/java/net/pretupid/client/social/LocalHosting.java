package net.pretupid.client.social;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.HttpUtil;

public final class LocalHosting {
	private final Set<UUID> invited = ConcurrentHashMap.newKeySet();
	private volatile boolean friendsOnly;
	private volatile int port = -1;

	public boolean friendsOnly() {
		return friendsOnly;
	}

	public boolean hosting() {
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		return server != null && server.isPublished();
	}

	public Optional<String> lanAddress() {
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server == null || !server.isPublished()) {
			return Optional.empty();
		}
		return localIpv4().map(ip -> ip + ":" + server.getPort());
	}

	public Optional<String> openToFriends() {
		Minecraft minecraft = Minecraft.getInstance();
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server == null) {
			return Optional.empty();
		}
		friendsOnly = true;
		if (!server.isPublished()) {
			int chosen = HttpUtil.getAvailablePort();
			if (!server.publishServer(MinecraftServer.MultiplayerScope.LAN, false, chosen)) {
				friendsOnly = false;
				return Optional.empty();
			}
			port = chosen;
		}
		return lanAddress();
	}

	public void invite(UUID friend) {
		invited.add(friend);
	}

	public boolean mayJoin(UUID player) {
		if (!friendsOnly || player == null) {
			return true;
		}
		if (player.equals(Minecraft.getInstance().getUser().getProfileId())) {
			return true;
		}
		return invited.contains(player) || net.pretupid.client.PretupidClient.get().social().friendIds().contains(player);
	}

	public void onWorldLeft() {
		friendsOnly = false;
		invited.clear();
		port = -1;
	}

	public int port() {
		return port;
	}

	private static Optional<String> localIpv4() {
		try {
			for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (!network.isUp() || network.isLoopback() || network.isVirtual()) {
					continue;
				}
				for (InetAddress address : Collections.list(network.getInetAddresses())) {
					if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
						return Optional.of(address.getHostAddress());
					}
				}
			}
		} catch (SocketException exception) {
			return Optional.empty();
		}
		return Optional.empty();
	}
}
