package net.pretupid.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.pretupid.client.network.Json;
import net.pretupid.client.network.ServerCompatibility;
import net.pretupid.client.network.ServerRulesPayload;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionResult;
import net.pretupid.client.config.ClientSettings;
import net.pretupid.client.config.ConfigStore;
import net.pretupid.client.hud.HudLayer;
import net.pretupid.client.hud.Notifications;
import net.pretupid.client.input.ClickTracker;
import net.pretupid.client.input.CombatTracker;
import net.pretupid.client.input.Keybinds;
import net.pretupid.client.module.ModuleManager;
import net.pretupid.client.module.hud.ArmorHud;
import net.pretupid.client.module.hud.ClockHud;
import net.pretupid.client.module.hud.ComboHud;
import net.pretupid.client.module.hud.ItemCounterHud;
import net.pretupid.client.module.hud.LightLevelHud;
import net.pretupid.client.module.hud.SaturationHud;
import net.pretupid.client.module.hud.CoordinatesHud;
import net.pretupid.client.module.hud.CpsHud;
import net.pretupid.client.module.hud.DirectionHud;
import net.pretupid.client.module.hud.FpsHud;
import net.pretupid.client.module.hud.KeystrokesHud;
import net.pretupid.client.module.hud.MemoryHud;
import net.pretupid.client.module.hud.PingHud;
import net.pretupid.client.module.hud.PotionHud;
import net.pretupid.client.module.hud.ReachHud;
import net.pretupid.client.module.hud.ServerHud;
import net.pretupid.client.module.hud.SessionHud;
import net.pretupid.client.module.hud.SpeedHud;
import net.pretupid.client.module.performance.DynamicFpsModule;
import net.pretupid.client.module.performance.EntityCullingModule;
import net.pretupid.client.module.performance.FpsBenchmark;
import net.pretupid.client.module.performance.FpsBoostModule;
import net.pretupid.client.module.performance.ParticleLimiterModule;
import net.pretupid.client.module.utility.ChatMacrosModule;
import net.pretupid.client.module.utility.ChatTimestampsModule;
import net.pretupid.client.module.utility.DeathCoordinatesModule;
import net.pretupid.client.module.utility.DurabilityAlertModule;
import net.pretupid.client.module.utility.LowHealthWarningModule;
import net.pretupid.client.module.utility.TabPingModule;
import net.pretupid.client.module.utility.ToggleSprintModule;
import net.pretupid.client.module.visual.ClearWeatherModule;
import net.pretupid.client.module.visual.CrosshairModule;
import net.pretupid.client.module.visual.FullbrightModule;
import net.pretupid.client.module.visual.MotionBlurModule;
import net.pretupid.client.module.visual.LowFireModule;
import net.pretupid.client.module.visual.NoHurtCamModule;
import net.pretupid.client.module.visual.OwnNametagModule;
import net.pretupid.client.module.visual.FreelookModule;
import net.pretupid.client.module.visual.ZoomModule;
import net.pretupid.client.mods.ModCatalog;
import net.pretupid.client.mods.ModIcons;
import net.pretupid.client.nametag.Nametags;
import net.pretupid.client.skins.AccountSkin;
import net.pretupid.client.skins.LocalSkinOverride;
import net.pretupid.client.skins.SkinLibrary;
import net.pretupid.client.network.ApiClient;
import net.pretupid.client.network.AuthSession;
import net.pretupid.client.network.PresenceService;
import net.pretupid.client.network.RealtimeClient;
import net.pretupid.client.cosmetic.CosmeticsService;
import net.pretupid.client.cosmetic.EmoteKeys;
import net.pretupid.client.cosmetic.EmotePlayer;
import net.pretupid.client.cosmetic.TrailRenderer;
import net.pretupid.client.news.NewsService;
import net.pretupid.client.social.LocalHosting;
import net.pretupid.client.social.SocialService;
import net.pretupid.client.update.UpdateService;
import net.pretupid.client.ui.screen.ClientMenuScreen;
import net.pretupid.client.ui.screen.ScreenIntegration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PretupidClient implements ClientModInitializer {
	public static final String MOD_ID = "pretupid";
	public static final Logger LOGGER = LoggerFactory.getLogger("Pretupid Client");

	private static PretupidClient instance;

	private final ClientSettings settings = new ClientSettings();
	private final ModuleManager modules = new ModuleManager();
	private final Notifications notifications = new Notifications();
	private final ClickTracker clicks = new ClickTracker();
	private final CombatTracker combat = new CombatTracker();
	private final ApiClient api = new ApiClient();
	private final AuthSession session = new AuthSession(api);
	private final PresenceService presence = new PresenceService(api, session);
	private final RealtimeClient realtime = new RealtimeClient(session);
	private final CosmeticsService cosmetics = new CosmeticsService(api, session, FabricLoader.getInstance().getConfigDir().resolve(MOD_ID));
	private final EmotePlayer emotes = new EmotePlayer();
	private EmoteKeys emoteKeys;
	private final TrailRenderer trails = new TrailRenderer();
	private final NewsService news = new NewsService(api);
	private ServerCompatibility compatibility;
	private final UpdateService updates = new UpdateService(api);
	private final SocialService social = new SocialService(api, session);
	private final LocalHosting hosting = new LocalHosting();
	private final ModIcons modIcons = new ModIcons();
	private final SkinLibrary skins = new SkinLibrary(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID));
	private final AccountSkin accountSkin = new AccountSkin();
	private final LocalSkinOverride localSkin = new LocalSkinOverride();
	private final FpsBenchmark benchmark = new FpsBenchmark();
	private ConfigStore config;
	private Keybinds keybinds;
	private Nametags nametags;
	private ModCatalog modCatalog;
	private boolean openMenuNextTick;

	public static PretupidClient get() {
		if (instance == null) {
			throw new IllegalStateException("Pretupid Client is not initialized");
		}
		return instance;
	}

	public static PretupidClient getOrNull() {
		return instance;
	}

	@Override
	public void onInitializeClient() {
		registerModules();
		config = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID), ConfigStore.defaultSharedDirectory(), settings, modules);
		nametags = new Nametags(this);
		modCatalog = new ModCatalog();
		keybinds = new Keybinds(this);
		emoteKeys = new EmoteKeys(settings.emoteKeys);
		compatibility = new ServerCompatibility(api, settings.restrictedServers);
		instance = this;
		config.load();
		keybinds.register();
		new HudLayer(this).register();
		ScreenIntegration.register(this);
		registerEvents();
		registerCommands();
		LOGGER.info("Pretupid Client {} initialized with {} modules{}", PretupidInfo.version(), modules.all().size(),
			PretupidInfo.launchedByPretupidLauncher() ? " (Pretupid Launcher)" : "");
	}

	private void registerModules() {
		modules.register(new FpsHud());
		modules.register(new CpsHud());
		modules.register(new PingHud());
		modules.register(new CoordinatesHud());
		modules.register(new KeystrokesHud());
		modules.register(new ArmorHud());
		modules.register(new PotionHud());
		modules.register(new ClockHud());
		modules.register(new DirectionHud());
		modules.register(new SpeedHud());
		modules.register(new MemoryHud());
		modules.register(new ServerHud());
		modules.register(new SessionHud());
		modules.register(new ReachHud());
		modules.register(new ComboHud());
		modules.register(new SaturationHud());
		modules.register(new ItemCounterHud());
		modules.register(new LightLevelHud());
		modules.register(new DynamicFpsModule());
		modules.register(new EntityCullingModule());
		modules.register(new ParticleLimiterModule());
		modules.register(new FpsBoostModule());
		modules.register(new ZoomModule());
		modules.register(new FreelookModule());
		modules.register(new NoHurtCamModule());
		modules.register(new LowFireModule());
		modules.register(new ClearWeatherModule());
		modules.register(new MotionBlurModule());
		modules.register(new FullbrightModule());
		modules.register(new CrosshairModule());
		modules.register(new ToggleSprintModule());
		modules.register(new DeathCoordinatesModule());
		modules.register(new DurabilityAlertModule());
		modules.register(new ChatTimestampsModule());
		modules.register(new ChatMacrosModule());
		modules.register(new LowHealthWarningModule());
		modules.register(new TabPingModule());
		modules.register(new OwnNametagModule());
	}

	private void registerEvents() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> {
			config.shutdown();
			realtime.disconnect();
			presence.shutdown();
			api.shutdown();
		});
		presence.onProfile(emotes::onRemoteProfile);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> minecraft.execute(() -> {
			modules.get(SessionHud.class).onWorldJoined();
			ServerData server = minecraft.getCurrentServer();
			if (server != null && !minecraft.hasSingleplayerServer()) {
				config.onServerJoined(server.ip);
				if (settings.serverCompatibility.enabled()) {
					compatibility.onJoined(server.ip);
				}
				if (server.type() == ServerData.Type.OTHER && server.ip != null && !server.ip.isBlank()) {
					settings.lastServerAddress.set(server.ip);
					settings.lastServerName.set(server.name == null || server.name.isBlank() ? server.ip : server.name);
				}
			}
		}));
		PayloadTypeRegistry.clientboundPlay().register(ServerRulesPayload.TYPE, ServerRulesPayload.CODEC);
		ClientPlayNetworking.registerGlobalReceiver(ServerRulesPayload.TYPE, (payload, context) -> context.client().execute(() -> {
			if (settings.serverCompatibility.enabled()) {
				compatibility.onServerRules(parseRuleModules(payload.json()));
			}
		}));
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof DisconnectedScreen) {
				StringBuilder text = new StringBuilder();
				for (AbstractWidget widget : Screens.getWidgets(screen)) {
					text.append(widget.getMessage().getString()).append(' ');
				}
				compatibility.onDisconnectMessage(text.toString());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> minecraft.execute(() -> {
			compatibility.onDisconnected();
			config.onServerLeft();
			presence.onWorldLeft();
			emotes.clear();
			hosting.onWorldLeft();
		}));
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide() && player instanceof LocalPlayer local) {
				combat.onAttack(local, entity);
			}
			return InteractionResult.PASS;
		});
	}

	private void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
			ClientCommands.literal("pretupid")
				.executes(context -> {
					openMenuNextTick = true;
					return 1;
				})
				.then(ClientCommands.literal("benchmark")
					.executes(context -> {
						if (!benchmark.start(Minecraft.getInstance())) {
							context.getSource().sendError(net.minecraft.network.chat.Component.translatable("pretupid.benchmark.unavailable"));
							return 0;
						}
						return 1;
					}))
				.then(ClientCommands.literal("reportcape")
					.then(ClientCommands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word())
						.executes(context -> reportCape(context.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(context, "player")))))
		));
	}

	private int reportCape(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, String name) {
		Minecraft minecraft = Minecraft.getInstance();
		var connection = minecraft.getConnection();
		java.util.UUID target = null;
		if (connection != null) {
			for (var info : connection.getOnlinePlayers()) {
				if (info.getProfile().name().equalsIgnoreCase(name)) {
					target = info.getProfile().id();
				}
			}
		}
		if (target == null) {
			source.sendError(net.minecraft.network.chat.Component.translatable("pretupid.report.not_found", name));
			return 0;
		}
		cosmetics.reportCape(target).whenComplete((ignored, error) -> minecraft.execute(() -> {
			if (error != null) {
				source.sendError(net.minecraft.network.chat.Component.literal(ApiClient.describe(error)));
			} else {
				source.sendFeedback(net.minecraft.network.chat.Component.translatable("pretupid.report.sent", name));
			}
		}));
		return 1;
	}

	private void tick(Minecraft minecraft) {
		if (openMenuNextTick) {
			openMenuNextTick = false;
			minecraft.gui.setScreen(new ClientMenuScreen(null));
		}
		keybinds.tick(minecraft);
		emoteKeys.tick(minecraft);
		combat.tick(minecraft);
		modules.tick(minecraft);
		boolean online = settings.sharePresence.enabled();
		if (!online && session.token() != null) {
			presence.signOut();
		}
		session.tick(minecraft, online);
		presence.tick(minecraft);
		realtime.tick(minecraft);
		cosmetics.tick(minecraft);
		emotes.tick(minecraft);
		trails.tick(minecraft);
		social.tick(minecraft);
		news.tick();
		if (settings.serverCompatibility.enabled()) {
			compatibility.tick();
		}
		updates.tick(minecraft);
		config.tick();
	}

	public void onFrame(Minecraft minecraft) {
		modules.get(EntityCullingModule.class).culler().beginFrame();
		benchmark.onFrame(minecraft);
	}

	public FpsBenchmark benchmark() {
		return benchmark;
	}

	public ClientSettings settings() {
		return settings;
	}

	public ModuleManager modules() {
		return modules;
	}

	public ConfigStore config() {
		return config;
	}

	public Notifications notifications() {
		return notifications;
	}

	public ClickTracker clicks() {
		return clicks;
	}

	public CombatTracker combat() {
		return combat;
	}

	public PresenceService presence() {
		return presence;
	}

	public RealtimeClient realtime() {
		return realtime;
	}

	public AuthSession session() {
		return session;
	}

	public CosmeticsService cosmetics() {
		return cosmetics;
	}

	public ServerCompatibility compatibility() {
		return compatibility;
	}

	private static java.util.Set<String> parseRuleModules(String json) {
		java.util.Set<String> modules = new java.util.LinkedHashSet<>();
		try {
			com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
			for (com.google.gson.JsonElement element : Json.array(root, "disable")) {
				if (element.isJsonPrimitive() && modules.size() < 64) {
					modules.add(element.getAsString().toLowerCase(java.util.Locale.ROOT));
				}
			}
		} catch (RuntimeException exception) {
			LOGGER.debug("Ignoring malformed server rules", exception);
		}
		return modules;
	}

	public EmoteKeys emoteKeys() {
		return emoteKeys;
	}

	public EmotePlayer emotes() {
		return emotes;
	}

	public NewsService news() {
		return news;
	}

	public UpdateService updates() {
		return updates;
	}

	public SocialService social() {
		return social;
	}

	public LocalHosting hosting() {
		return hosting;
	}

	public Nametags nametags() {
		return nametags;
	}

	public Keybinds keybinds() {
		return keybinds;
	}

	public ModCatalog modCatalog() {
		return modCatalog;
	}

	public AccountSkin accountSkin() {
		return accountSkin;
	}

	public LocalSkinOverride localSkin() {
		return localSkin;
	}

	public SkinLibrary skins() {
		return skins;
	}

	public ModIcons modIcons() {
		return modIcons;
	}
}
