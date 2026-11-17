package net.pretupid.client.cosmetic.model;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.pretupid.client.PretupidClient;

public final class CosmeticModels {
	private static final int WHITE = 0xFFF4F5FA;
	private static final int FEATHER_TIP = 0xFFC9D2E3;
	private static final int GOLD = 0xFFE8BA34;
	private static final int GOLD_DARK = 0xFFB0801E;
	private static final int MEMBRANE = 0xFF6A2E9C;
	private static final int VEIN = 0xFF41195F;
	private static final int BONE = 0xFF2B2235;
	private static final int CLAW = 0xFFD9CFE6;
	private static final int MONARCH = 0xFFF28A26;
	private static final int MONARCH_LIGHT = 0xFFFFC064;
	private static final int INK = 0xFF1E1A22;
	private static final int SHIELD_BLUE = 0xFF3557C8;
	private static final int SHIELD_WHITE = 0xFFE9EDF7;
	private static final int STEEL = 0xFFB4BAC6;
	private static final int GRAPHITE = 0xFF2C3038;
	private static final int FLAME = 0xFFFF7A1C;
	private static final int FLAME_CORE = 0xFFFFE070;
	private static final int GHOST = 0xFFF1F4FF;
	private static final int GHOST_SHADE = 0xFFC8D0EE;
	private static final int PENGUIN = 0xFF23262E;
	private static final int BEAK = 0xFFF2A230;
	private static final int OWL = 0xFF8A5A36;
	private static final int OWL_LIGHT = 0xFFD9B88A;
	private static final int EYE_YELLOW = 0xFFFFD84A;
	private static final int DRONE_EYE = 0xFF5CE6FF;

	public static final String BACK = "back";
	public static final String PET = "pet";

	private record Definition(Supplier<Shape> shape, CosmeticModel.Animator animator) {
	}

	public record Entry(CosmeticModel model, Identifier texture) {
	}

	private static final Map<String, Definition> BACK_ITEMS = new LinkedHashMap<>();
	private static final Map<String, Definition> PETS = new LinkedHashMap<>();
	private static final Map<String, Entry> BAKED = new HashMap<>();

	static {
		BACK_ITEMS.put("angel_wings", new Definition(CosmeticModels::angelWings, CosmeticModels::animateFeatheredWings));
		BACK_ITEMS.put("dragon_wings", new Definition(CosmeticModels::dragonWings, CosmeticModels::animateDragonWings));
		BACK_ITEMS.put("butterfly_wings", new Definition(CosmeticModels::butterflyWings, CosmeticModels::animateButterflyWings));
		BACK_ITEMS.put("knight_shield", new Definition(CosmeticModels::knightShield, (model, state) -> {
		}));
		BACK_ITEMS.put("jetpack", new Definition(CosmeticModels::jetpack, CosmeticModels::animateJetpack));
		PETS.put("ghost", new Definition(CosmeticModels::ghost, CosmeticModels::animateGhost));
		PETS.put("penguin", new Definition(CosmeticModels::penguin, CosmeticModels::animatePenguin));
		PETS.put("owl", new Definition(CosmeticModels::owl, CosmeticModels::animateOwl));
		PETS.put("drone", new Definition(CosmeticModels::drone, CosmeticModels::animateDrone));
	}

	private CosmeticModels() {
	}

	public static List<String> backItems() {
		return List.copyOf(BACK_ITEMS.keySet());
	}

	public static List<String> pets() {
		return List.copyOf(PETS.keySet());
	}

	public static Entry back(String id) {
		return entry(BACK, id, BACK_ITEMS.get(id));
	}

	public static Entry pet(String id) {
		return entry(PET, id, PETS.get(id));
	}

	private static Entry entry(String kind, String id, Definition definition) {
		if (definition == null) {
			return null;
		}
		return BAKED.computeIfAbsent(kind + "/" + id, key -> {
			Shape shape = definition.shape().get();
			ShapeBaker.Baked baked = ShapeBaker.bake(shape);
			Identifier texture = Identifier.fromNamespaceAndPath(PretupidClient.MOD_ID, "cosmetic/" + key);
			Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(() -> "pretupid cosmetic " + key, baked.texture()));
			return new Entry(new CosmeticModel(baked.root(), shape, definition.animator()), texture);
		});
	}

	public static void clear() {
		for (Entry entry : BAKED.values()) {
			Minecraft.getInstance().getTextureManager().release(entry.texture());
		}
		BAKED.clear();
	}

	private static Shape angelWings() {
		Shape shape = Shape.on(Shape.Anchor.BODY);
		featheredWing(shape.node("left_wing", 1.5f, 1.5f, 3.6f), false);
		featheredWing(shape.node("right_wing", -1.5f, 1.5f, 3.6f), true);
		return shape;
	}

	private static void featheredWing(Shape.Node wing, boolean mirrored) {
		int[][] rows = {{0, -1, 11, 3}, {0, 2, 10, 3}, {1, 5, 8, 3}, {2, 8, 6, 2}, {3, 10, 4, 2}};
		for (int[] row : rows) {
			if (mirrored) {
				wing.mirroredBox(row[0], row[1], 0f, row[2], row[3], 1, WHITE, FEATHER_TIP, Pattern.TIPPED);
			} else {
				wing.box(row[0], row[1], 0f, row[2], row[3], 1, WHITE, FEATHER_TIP, Pattern.TIPPED);
			}
		}
		if (mirrored) {
			wing.mirroredBox(0f, -2f, -0.2f, 6, 1, 1, GOLD, GOLD, Pattern.SOLID);
		} else {
			wing.box(0f, -2f, -0.2f, 6, 1, 1, GOLD);
		}
	}

	private static Shape dragonWings() {
		Shape shape = Shape.on(Shape.Anchor.BODY);
		dragonWing(shape.node("left_wing", 1.5f, 1f, 3.6f), false);
		dragonWing(shape.node("right_wing", -1.5f, 1f, 3.6f), true);
		return shape;
	}

	private static void dragonWing(Shape.Node wing, boolean mirrored) {
		int[][] membrane = {{0, 0, 12, 2}, {1, 2, 10, 2}, {2, 4, 9, 2}, {3, 6, 7, 2}, {4, 8, 5, 2}, {6, 10, 2, 2}};
		for (int[] row : membrane) {
			add(wing, mirrored, row[0], row[1], 0.2f, row[2], row[3], 1, MEMBRANE, VEIN, Pattern.VEINED);
		}
		add(wing, mirrored, 0, -1, -0.1f, 13, 1, 1, BONE, BONE, Pattern.SOLID);
		add(wing, mirrored, 13, -2, -0.1f, 1, 2, 1, CLAW, CLAW, Pattern.SOLID);
	}

	private static Shape butterflyWings() {
		Shape shape = Shape.on(Shape.Anchor.BODY);
		butterflyWing(shape.node("left_wing", 0.5f, 3f, 3.4f), false);
		butterflyWing(shape.node("right_wing", -0.5f, 3f, 3.4f), true);
		return shape;
	}

	private static void butterflyWing(Shape.Node wing, boolean mirrored) {
		add(wing, mirrored, 0, -5, 0f, 8, 7, 1, MONARCH, INK, Pattern.RIM);
		add(wing, mirrored, 2, -3, -0.3f, 3, 3, 1, MONARCH_LIGHT, MONARCH_LIGHT, Pattern.SOLID);
		add(wing, mirrored, 0, 2, 0f, 6, 5, 1, MONARCH, INK, Pattern.RIM);
		add(wing, mirrored, 2, 3, -0.3f, 2, 2, 1, MONARCH_LIGHT, MONARCH_LIGHT, Pattern.SOLID);
	}

	private static Shape knightShield() {
		Shape shape = Shape.on(Shape.Anchor.BODY);
		shape.node("shield", 0f, 1.5f, 3.2f).rotated(0f, 0f, 0.18f)
			.box(-4f, 0f, 0f, 8, 10, 1, SHIELD_BLUE, SHIELD_WHITE, Pattern.QUARTERED)
			.box(-5f, 1f, 0f, 1, 8, 1, GOLD)
			.box(4f, 1f, 0f, 1, 8, 1, GOLD)
			.box(-3f, -1f, 0f, 6, 1, 1, GOLD)
			.box(-3f, 10f, 0f, 6, 1, 1, GOLD)
			.box(-2f, 11f, 0f, 4, 1, 1, GOLD_DARK)
			.box(-1.5f, 3.5f, 0.6f, 3, 3, 1, GOLD, GOLD_DARK, Pattern.RIM);
		return shape;
	}

	private static Shape jetpack() {
		Shape shape = Shape.on(Shape.Anchor.BODY);
		Shape.Node pack = shape.node("pack", 0f, 1f, 3f)
			.box(0.5f, 0f, 0f, 3, 8, 3, STEEL, GRAPHITE, Pattern.BANDED)
			.box(-3.5f, 0f, 0f, 3, 8, 3, STEEL, GRAPHITE, Pattern.BANDED)
			.box(-0.5f, 1f, 0.5f, 1, 5, 2, GRAPHITE)
			.box(1f, 8f, 0.5f, 2, 1, 2, GRAPHITE)
			.box(-3f, 8f, 0.5f, 2, 1, 2, GRAPHITE)
			.box(1f, -1f, 1f, 1, 1, 1, FLAME)
			.box(-2f, -1f, 1f, 1, 1, 1, DRONE_EYE, DRONE_EYE, Pattern.GLOW);
		pack.child("left_flame", 2f, 9f, 1.5f).box(-1f, 0f, -1f, 2, 3, 2, FLAME_CORE, FLAME, Pattern.TIPPED);
		pack.child("right_flame", -2f, 9f, 1.5f).box(-1f, 0f, -1f, 2, 3, 2, FLAME_CORE, FLAME, Pattern.TIPPED);
		return shape;
	}

	private static Shape ghost() {
		Shape shape = Shape.on(Shape.Anchor.ROOT);
		shape.node("pet", 0f, 0f, 0f)
			.box(-3f, -3.5f, -3f, 6, 6, 6, GHOST, GHOST_SHADE, Pattern.SOLID)
			.box(-3f, 2.5f, -3f, 2, 2, 6, GHOST, GHOST_SHADE, Pattern.TIPPED)
			.box(-1f, 2.5f, -3f, 2, 1, 6, GHOST, GHOST_SHADE, Pattern.TIPPED)
			.box(1f, 2.5f, -3f, 2, 2, 6, GHOST, GHOST_SHADE, Pattern.TIPPED)
			.box(-2f, -1.5f, -3.4f, 1, 2, 1, INK, INK, Pattern.GLOW)
			.box(1f, -1.5f, -3.4f, 1, 2, 1, INK, INK, Pattern.GLOW)
			.box(-0.5f, 1f, -3.3f, 1, 1, 1, GHOST_SHADE);
		return shape;
	}

	private static Shape penguin() {
		Shape shape = Shape.on(Shape.Anchor.ROOT);
		Shape.Node body = shape.node("pet", 0f, 0f, 0f)
			.box(-2.5f, -3.5f, -2f, 5, 7, 4, PENGUIN)
			.box(-1.5f, -1.5f, -2.4f, 3, 4, 1, WHITE)
			.box(-1.5f, -3f, -2.4f, 1, 1, 1, WHITE, WHITE, Pattern.GLOW)
			.box(0.5f, -3f, -2.4f, 1, 1, 1, WHITE, WHITE, Pattern.GLOW)
			.box(-0.5f, -2f, -3f, 1, 1, 1, BEAK)
			.box(-2f, 3.5f, -2.5f, 2, 1, 2, BEAK)
			.box(0f, 3.5f, -2.5f, 2, 1, 2, BEAK);
		body.child("left_flipper", 2.5f, -1.5f, 0f).box(0f, 0f, -1f, 1, 4, 2, PENGUIN);
		body.child("right_flipper", -2.5f, -1.5f, 0f).box(-1f, 0f, -1f, 1, 4, 2, PENGUIN);
		return shape;
	}

	private static Shape owl() {
		Shape shape = Shape.on(Shape.Anchor.ROOT);
		Shape.Node body = shape.node("pet", 0f, 0f, 0f)
			.box(-2.5f, -3f, -2.5f, 5, 6, 5, OWL, OWL_LIGHT, Pattern.SPOTTED)
			.box(-2f, -2.5f, -2.9f, 4, 3, 1, OWL_LIGHT)
			.box(-2f, -2f, -3.3f, 2, 2, 1, EYE_YELLOW, INK, Pattern.RIM)
			.box(0f, -2f, -3.3f, 2, 2, 1, EYE_YELLOW, INK, Pattern.RIM)
			.box(-0.5f, -0.5f, -3.4f, 1, 1, 1, BEAK)
			.box(-2.5f, -4f, -1f, 1, 1, 1, OWL)
			.box(1.5f, -4f, -1f, 1, 1, 1, OWL)
			.box(-1.5f, 3f, -2f, 1, 1, 1, BEAK)
			.box(0.5f, 3f, -2f, 1, 1, 1, BEAK);
		body.child("left_flipper", 2.5f, -2f, 0f).box(0f, 0f, -2f, 1, 4, 4, OWL, OWL_LIGHT, Pattern.TIPPED);
		body.child("right_flipper", -2.5f, -2f, 0f).box(-1f, 0f, -2f, 1, 4, 4, OWL, OWL_LIGHT, Pattern.TIPPED);
		return shape;
	}

	private static Shape drone() {
		Shape shape = Shape.on(Shape.Anchor.ROOT);
		Shape.Node body = shape.node("pet", 0f, 0f, 0f)
			.box(-2.5f, -2f, -2.5f, 5, 3, 5, STEEL, GRAPHITE, Pattern.RIM)
			.box(-1f, -1.5f, -2.9f, 2, 1, 1, DRONE_EYE, DRONE_EYE, Pattern.GLOW)
			.box(-1.5f, 1f, -1.5f, 3, 1, 3, GRAPHITE)
			.box(-0.5f, -3f, -0.5f, 1, 1, 1, GRAPHITE);
		body.child("rotor", 0f, -3.5f, 0f)
			.box(-4f, -0.5f, -0.5f, 8, 1, 1, GRAPHITE)
			.box(-0.5f, -0.5f, -4f, 1, 1, 8, GRAPHITE);
		return shape;
	}

	private static void add(Shape.Node node, boolean mirrored, float x, float y, float z, int width, int height, int depth, int color, int accent, Pattern pattern) {
		if (mirrored) {
			node.mirroredBox(x, y, z, width, height, depth, color, accent, pattern);
		} else {
			node.box(x, y, z, width, height, depth, color, accent, pattern);
		}
	}

	private static float time(AvatarRenderState state) {
		return state.ageInTicks;
	}

	private static float motion(AvatarRenderState state) {
		return Mth.clamp(state.walkAnimationSpeed, 0f, 1f);
	}

	private static void spreadWings(CosmeticModel model, float sweep, float lift) {
		ModelPart left = model.part("left_wing");
		ModelPart right = model.part("right_wing");
		left.yRot = -sweep;
		right.yRot = sweep;
		left.zRot = -lift;
		right.zRot = lift;
	}

	private static void animateFeatheredWings(CosmeticModel model, AvatarRenderState state) {
		float motion = motion(state);
		float flap = Mth.sin(time(state) * 0.12f) * (0.06f + motion * 0.16f);
		float sweep = state.isCrouching ? 0.75f : 0.22f - motion * 0.08f;
		spreadWings(model, sweep, 0.22f + flap);
	}

	private static void animateDragonWings(CosmeticModel model, AvatarRenderState state) {
		float motion = motion(state);
		float flap = Mth.sin(time(state) * 0.08f) * (0.08f + motion * 0.2f);
		float sweep = state.isCrouching ? 0.85f : 0.28f - motion * 0.1f;
		spreadWings(model, sweep, 0.18f + flap);
	}

	private static void animateButterflyWings(CosmeticModel model, AvatarRenderState state) {
		float flutter = 0.55f + Mth.sin(time(state) * 0.4f) * 0.45f;
		spreadWings(model, state.isCrouching ? 1.1f : flutter, 0.05f);
	}

	private static void animateJetpack(CosmeticModel model, AvatarRenderState state) {
		float time = time(state);
		float boost = 0.55f + motion(state) * 0.6f;
		model.part("left_flame").yScale = boost * (0.8f + 0.2f * Mth.sin(time * 1.9f));
		model.part("right_flame").yScale = boost * (0.8f + 0.2f * Mth.sin(time * 2.3f + 1.3f));
	}

	private static ModelPart hover(CosmeticModel model, AvatarRenderState state, float height, float speed) {
		ModelPart pet = model.part("pet");
		pet.y += Mth.sin(time(state) * speed) * height;
		return pet;
	}

	private static void animateGhost(CosmeticModel model, AvatarRenderState state) {
		ModelPart pet = hover(model, state, 1.4f, 0.11f);
		float time = time(state);
		pet.yRot = Mth.sin(time * 0.05f) * 0.35f;
		pet.zRot = Mth.sin(time * 0.08f) * 0.08f;
	}

	private static void animatePenguin(CosmeticModel model, AvatarRenderState state) {
		ModelPart pet = hover(model, state, 0.8f, 0.14f);
		float time = time(state);
		float waddle = Mth.sin(time * 0.25f) * (0.6f + motion(state) * 0.4f);
		pet.zRot = waddle * 0.12f;
		model.part("left_flipper").zRot = -0.15f - Math.abs(waddle) * 0.35f;
		model.part("right_flipper").zRot = 0.15f + Math.abs(waddle) * 0.35f;
	}

	private static void animateOwl(CosmeticModel model, AvatarRenderState state) {
		ModelPart pet = hover(model, state, 1.1f, 0.09f);
		float time = time(state);
		float glance = Mth.sin(time * 0.03f);
		pet.yRot = glance * glance * glance * 0.9f;
		float stretch = Mth.clamp((Mth.sin(time * 0.045f) - 0.6f) / 0.4f, 0f, 1f);
		float flap = Math.max(0f, Mth.sin(time * 0.6f)) * (0.1f + 0.8f * stretch);
		model.part("left_flipper").zRot = -flap;
		model.part("right_flipper").zRot = flap;
	}

	private static void animateDrone(CosmeticModel model, AvatarRenderState state) {
		ModelPart pet = hover(model, state, 1.2f, 0.16f);
		float time = time(state);
		pet.xRot = motion(state) * 0.25f;
		model.part("rotor").yRot = time * 1.4f;
	}
}
