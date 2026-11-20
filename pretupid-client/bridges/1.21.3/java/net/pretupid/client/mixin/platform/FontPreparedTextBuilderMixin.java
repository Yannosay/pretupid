package net.pretupid.client.mixin.platform;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import net.pretupid.client.nametag.NameEffect;
import net.pretupid.client.nametag.NametagFormatter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
public abstract class FontPreparedTextBuilderMixin {
	@Unique
	private NameEffect.Offset pretupid$offset;

	@Inject(method = "accept(ILnet/minecraft/network/chat/Style;I)Z", at = @At("HEAD"))
	private void pretupid$resolveOffset(int position, Style style, int codepoint, CallbackInfoReturnable<Boolean> cir) {
		pretupid$offset = NameEffect.decode(style.getInsertion(), NametagFormatter.seconds());
	}

	@WrapOperation(method = "accept(ILnet/minecraft/network/chat/Style;I)Z",
		at = @At(value = "NEW", target = "(FFILnet/minecraft/client/gui/font/glyphs/BakedGlyph;Lnet/minecraft/network/chat/Style;F)Lnet/minecraft/client/gui/font/glyphs/BakedGlyph$GlyphInstance;"))
	private BakedGlyph.GlyphInstance pretupid$shiftGlyph(float x, float y, int color, BakedGlyph glyph, Style style, float boldOffset,
		Operation<BakedGlyph.GlyphInstance> original) {
		NameEffect.Offset offset = pretupid$offset;
		if (offset == null) {
			return original.call(x, y, color, glyph, style, boldOffset);
		}
		return original.call(x + offset.x(), y + offset.y(), color, glyph, style, boldOffset);
	}
}
