package com.musicunbound.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/** Text drawing for the Music Unbound screens: cut with "..." when too wide, optionally centred. */
final class UiText {
    private static final FormattedText ELLIPSIS = FormattedText.of("...");

    private UiText() {
    }

    static void draw(GuiGraphics graphics, Font font, FormattedText text, int left, int top, int maxWidth, int color, boolean centered) {
        if (maxWidth <= 0) {
            return;
        }
        FormattedText shown = text;
        if (font.width(text) > maxWidth) {
            int room = maxWidth - font.width(ELLIPSIS);
            if (room <= 0) {
                return;
            }
            shown = FormattedText.composite(font.substrByWidth(text, room), ELLIPSIS);
        }
        FormattedCharSequence sequence = Language.getInstance().getVisualOrder(shown);
        int x = centered ? left + (maxWidth - font.width(sequence)) / 2 : left;
        graphics.drawString(font, sequence, x, top, color, false);
    }
}
