package net.pretupid.client.platform.input;

import net.minecraft.util.StringUtil;

public record CharacterEvent(int codepoint, int modifiers) {
	public String codepointAsString() {
		return Character.toString(codepoint);
	}

	public boolean isAllowedChatCharacter() {
		return StringUtil.isAllowedChatCharacter((char) codepoint);
	}
}
