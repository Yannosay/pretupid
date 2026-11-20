package net.pretupid.client.mixin.platform;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import net.pretupid.client.PretupidClient;
import net.pretupid.client.nametag.NameEffect;
import net.pretupid.client.nametag.NametagFormatter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder")
public abstract class FontPreparedTextBuilderMixin {
	@Unique
	private NameEffect.Offset pretupid$offset;

	@Inject(method = "accept(ILnet/minecraft/network/chat/Style;I)Z", at = @At("HEAD"))
	private void pretupid$resolveOffset(int position, Style style, int codepoint, CallbackInfoReturnable<Boolean> cir) {
		pretupid$offset = NameEffect.decode(style.getInsertion(), NametagFormatter.seconds());
	}

	@WrapOperation(method = "accept(ILnet/minecraft/network/chat/Style;I)Z",
		at = @At(value = "NEW", target = "(FFIILnet/minecraft/client/gui/font/glyphs/BakedGlyph;Lnet/minecraft/network/chat/Style;FF)Lnet/minecraft/client/gui/font/glyphs/BakedGlyph$GlyphInstance;"))
	private BakedGlyph.GlyphInstance pretupid$shiftGlyph(float x, float y, int color, int shadowColor, BakedGlyph glyph, Style style, float boldOffset, float shadowOffset,
		Operation<BakedGlyph.GlyphInstance> original) {
		NameEffect.Offset offset = pretupid$offset;
		if (offset == null) {
			return original.call(x, y, color, shadowColor, glyph, style, boldOffset, shadowOffset);
		}
		return original.call(x + offset.x(), y + offset.y(), color, shadowColor, glyph, style, boldOffset, shadowOffset);
	}

	@ModifyArg(method = "accept(ILnet/minecraft/network/chat/Style;I)Z",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font$PreparedTextBuilder;markBackground(FFF)V"), index = 0)
	private float pretupid$backgroundX(float x) {
		NameEffect.Offset offset = pretupid$movingBackgroundOffset();
		return offset == null ? x : x + offset.x();
	}

	@ModifyArg(method = "accept(ILnet/minecraft/network/chat/Style;I)Z",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font$PreparedTextBuilder;markBackground(FFF)V"), index = 1)
	private float pretupid$backgroundY(float y) {
		NameEffect.Offset offset = pretupid$movingBackgroundOffset();
		return offset == null ? y : y + offset.y();
	}

	@Unique
	private NameEffect.Offset pretupid$movingBackgroundOffset() {
		NameEffect.Offset offset = pretupid$offset;
		if (offset == null) {
			return null;
		}
		PretupidClient client = PretupidClient.getOrNull();
		return client != null && client.settings().nametagMovingBackground.enabled() ? offset : null;
	}
}
