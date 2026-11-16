package net.pretupid.client.mixin;

import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import net.pretupid.client.nametag.NameEffect;
import net.pretupid.client.nametag.NametagFormatter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder")
public abstract class FontPreparedTextBuilderMixin {
	@Shadow
	private float x;

	@Shadow
	private float y;

	@Unique
	private float pretupid$offsetX;

	@Unique
	private float pretupid$offsetY;

	@Inject(method = "accept(ILnet/minecraft/network/chat/Style;Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;)Z", at = @At("HEAD"))
	private void pretupid$shiftAnimatedGlyph(int position, Style style, BakedGlyph glyph, CallbackInfoReturnable<Boolean> cir) {
		NameEffect.Offset offset = NameEffect.decode(style.getInsertion(), NametagFormatter.seconds());
		if (offset == null) {
			pretupid$offsetX = 0f;
			pretupid$offsetY = 0f;
			return;
		}
		pretupid$offsetX = offset.x();
		pretupid$offsetY = offset.y();
		x += pretupid$offsetX;
		y += pretupid$offsetY;
	}

	@Inject(method = "accept(ILnet/minecraft/network/chat/Style;Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;)Z", at = @At("RETURN"))
	private void pretupid$restoreBaseline(int position, Style style, BakedGlyph glyph, CallbackInfoReturnable<Boolean> cir) {
		x -= pretupid$offsetX;
		y -= pretupid$offsetY;
		pretupid$offsetX = 0f;
		pretupid$offsetY = 0f;
	}
}
