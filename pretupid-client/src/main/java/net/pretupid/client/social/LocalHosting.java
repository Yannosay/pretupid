package net.pretupid.client.social;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.HttpUtil;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.PretupidInfo;
import net.pretupid.client.social.tunnel.TunnelGuest;
import net.pretupid.client.social.tunnel.TunnelHost;

public final class LocalHosting {
	public static final String TUNNEL_PREFIX = "tunnel:";

	public record Share(String address, boolean lan) {
	}

	private final Set<UUID> invited = ConcurrentHashMap.newKeySet();
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private volatile boolean friendsOnly;
	private volatile int port = -1;
	private volatile TunnelHost tunnel;
	private volatile TunnelGuest guest;

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

	public CompletableFuture<Share> openToFriends() {
		Minecraft minecraft = Minecraft.getInstance();
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server == null) {
			return CompletableFuture.failedFuture(new IllegalStateException("Not in a singleplayer world"));
		}
		friendsOnly = true;
		if (!server.isPublished()) {
			int chosen = HttpUtil.getAvailablePort();
			if (!server.publishServer(MinecraftServer.MultiplayerScope.LAN, false, chosen)) {
				friendsOnly = false;
				return CompletableFuture.failedFuture(new IllegalStateException("The world could not be opened"));
			}
			port = chosen;
		}
		int serverPort = server.getPort();
		PretupidClient client = PretupidClient.get();
		UUID self = minecraft.getUser().getProfileId();
		Optional<Share> lan = lanAddress().map(address -> new Share(address, true));
		if (!client.session().online() || self == null) {
			return lan.map(CompletableFuture::completedFuture)
				.orElseGet(() -> CompletableFuture.failedFuture(new IllegalStateException("No network address found")));
		}
		Share relayed = new Share(TUNNEL_PREFIX + self, false);
		TunnelHost current = tunnel;
		if (current != null && current.running() && current.port() == serverPort) {
			return CompletableFuture.completedFuture(relayed);
		}
		if (current != null) {
			current.stop();
		}
		TunnelHost host = new TunnelHost(http, PretupidInfo.realtimeUri(), () -> client.session().token(), this::mayJoin, serverPort);
		tunnel = host;
		return host.start().handle((result, error) -> {
			if (error == null) {
				return relayed;
			}
			PretupidClient.LOGGER.warn("Could not share the world through Pretupid: {}", error.getMessage());
			return lan.orElseThrow(() -> new IllegalStateException("Could not share the world"));
		});
	}

	public static Optional<UUID> tunnelHost(String address) {
		if (address == null || !address.startsWith(TUNNEL_PREFIX)) {
			return Optional.empty();
		}
		try {
			return Optional.of(UUID.fromString(address.substring(TUNNEL_PREFIX.length())));
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}

	public String joinThroughTunnel(UUID host) throws IOException {
		TunnelGuest previous = guest;
		if (previous != null) {
			previous.close();
		}
		TunnelGuest opened = TunnelGuest.open(http, PretupidInfo.realtimeUri(), () -> PretupidClient.get().session().token(), host);
		guest = opened;
		return opened.address();
	}

	public Optional<String> tunnelRoom(String connectedAddress) {
		TunnelHost host = tunnel;
		UUID self = Minecraft.getInstance().getUser().getProfileId();
		if (host != null && host.running() && self != null) {
			return Optional.of(TUNNEL_PREFIX + self);
		}
		TunnelGuest joined = guest;
		if (joined != null && joined.address().equals(connectedAddress)) {
			return Optional.of(TUNNEL_PREFIX + joined.host());
		}
		return Optional.empty();
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
		return invited.contains(player) || PretupidClient.get().social().friendIds().contains(player);
	}

	public void onWorldLeft() {
		friendsOnly = false;
		invited.clear();
		port = -1;
		TunnelHost current = tunnel;
		tunnel = null;
		if (current != null) {
			current.stop();
		}
	}

	public void shutdown() {
		onWorldLeft();
		TunnelGuest current = guest;
		guest = null;
		if (current != null) {
			current.close();
		}
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
