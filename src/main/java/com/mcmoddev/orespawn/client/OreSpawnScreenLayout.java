package com.mcmoddev.orespawn.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;

/** Shared dimensions for the compact world-creation screens. */
final class OreSpawnScreenLayout {
	private static final int COMPACT_HEIGHT = 260;

	private OreSpawnScreenLayout() { }

	static int mainTop(int height) {
		return height < COMPACT_HEIGHT ? 28 : 42;
	}

	static int mainRowSpacing(int height) {
		return height < COMPACT_HEIGHT ? 22 : 24;
	}

	static int mainTitleY(int height) {
		return height < COMPACT_HEIGHT ? 7 : 16;
	}

	static int mainErrorY(int height) {
		return height < COMPACT_HEIGHT ? 18 : 30;
	}

	static int footerY(int height) {
		return height - 28;
	}

	static Component fit(Font font, Component message, int width) {
		if (font.width(message) <= width) {
			return message;
		}
		String suffix = "...";
		int available = Math.max(0, width - font.width(suffix));
		return new TextComponent(font.plainSubstrByWidth(message.getString(), available) + suffix);
	}

	static Button button(Screen screen, Font font, int x, int y, int width, int height,
			Component message, Button.OnPress onPress) {
		Component fitted = fit(font, message, Math.max(0, width - 8));
		if (fitted == message) {
			return new Button(x, y, width, height, message, onPress);
		}
		return new Button(x, y, width, height, fitted, onPress,
				(button, poseStack, mouseX, mouseY) -> screen.renderTooltip(poseStack,
						font.split(message, Math.max(180, Math.min(310, screen.width - 20))), mouseX, mouseY));
	}
}
