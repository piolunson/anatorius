package dev.project.anatorius.gui.impl;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.gui.ClickGuiScreen;
import dev.project.anatorius.gui.api.Button;
import dev.project.anatorius.gui.api.Frame;
import dev.project.anatorius.gui.style.GuiStyle;
import dev.project.anatorius.modules.impl.core.ClickGuiModule;
import dev.project.anatorius.settings.impl.StringSetting;
import dev.project.anatorius.utils.graphics.Renderer2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public class StringButton extends Button {
    private final StringSetting setting;
    private String currentString = "";
    private boolean listening = false;
    private boolean selecting = false;
    private int cursorIndex = 0;

    public StringButton(StringSetting setting, Frame parent, int height) {
        super(setting, parent, height, setting.getDescription());
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        GuiStyle style = GuiStyle.current();
        if (style.themed()) {
            StyledRows.box(context, this, style, getHeight(), StyledRows.hovered(this, style, getHeight(), mouseX, mouseY));
            int textX = StyledRows.boxTextX(this, style);
            if (listening) {
                String shown = currentString;
                if (!selecting) {
                    int clamped = Math.max(0, Math.min(cursorIndex, currentString.length()));
                    shown = currentString.substring(0, clamped) + (Anatorius.CLICK_GUI.isShowLine() ? "|" : " ")
                            + currentString.substring(clamped);
                }
                StyledRows.text(context, shown, textX, getY(), getHeight(), selecting ? style.accentHover() : Color.WHITE);
            } else {
                StyledRows.labelValue(context, this, style, getHeight(), textX, setting.getTag() + " ", setting.getValue());
            }
            return;
        }

        ClickGuiModule clickGui = Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class);
        Renderer2D.renderQuad(context, getX() + getPadding() + 1, getY(),
                getX() + getWidth() - getPadding() - 1, getY() + getHeight() - 1,
                ClickGuiScreen.getModuleButtonColor(getY(), 100, true));

        String displayText;
        if (listening) {
            if (selecting) {
                displayText = currentString;
            } else {
                int clampedIndex = Math.max(0, Math.min(cursorIndex, currentString.length()));
                String before = currentString.substring(0, clampedIndex);
                String after = currentString.substring(clampedIndex);
                String cursorChar = Anatorius.CLICK_GUI.isShowLine() ? "|" : " ";
                displayText = before + cursorChar + after;
            }
        } else {
            displayText = setting.getTag() + " " + Formatting.GRAY + setting.getValue();
        }

        Anatorius.FONT_MANAGER.drawTextWithShadow(context, displayText, getX() + getTextPadding() + 1, getY() + 2,
                selecting ? clickGui.enabledTextColor.getColor() : clickGui.textColor.getColor());
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (isHovering(mouseX, mouseY) && !listening) {
                listening = true;
                currentString = setting.getValue();
                cursorIndex = currentString.length();
                selecting = false;
                playClickSound();
            } else if (!isHovering(mouseX, mouseY)) {
                listening = false;
                selecting = false;
            }
        }
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!listening)
            return;

        var handle = mc.getWindow();
        boolean ctrl = InputUtil.isKeyPressed(handle,
                (net.minecraft.util.Util.getOperatingSystem() == net.minecraft.util.Util.OperatingSystem.OSX) ? GLFW.GLFW_KEY_LEFT_SUPER : GLFW.GLFW_KEY_LEFT_CONTROL);

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            selecting = false;
            return;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            setting.setValue(currentString);
            selecting = false;
            listening = false;
            return;
        }

        if (keyCode == GLFW.GLFW_KEY_LEFT && !currentString.isEmpty()) {
            if (selecting) {
                selecting = false;
                cursorIndex = 0;
            } else {
                if (cursorIndex > 0)
                    cursorIndex--;
            }
            return;
        }

        if (keyCode == GLFW.GLFW_KEY_RIGHT && !currentString.isEmpty()) {
            if (selecting) {
                selecting = false;
                cursorIndex = currentString.length();
            } else {
                if (cursorIndex < currentString.length())
                    cursorIndex++;
            }
            return;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (selecting) {
                currentString = "";
                cursorIndex = 0;
                selecting = false;
            } else {
                if (cursorIndex > 0) {
                    currentString = currentString.substring(0, cursorIndex - 1) + currentString.substring(cursorIndex);
                    cursorIndex--;
                }
            }
            return;
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (selecting) {
                currentString = "";
                cursorIndex = 0;
                selecting = false;
            } else {
                if (cursorIndex < currentString.length()) {
                    currentString = currentString.substring(0, cursorIndex) + currentString.substring(cursorIndex + 1);
                }
            }
            return;
        }

        if (ctrl) {
            if (keyCode == GLFW.GLFW_KEY_V) {
                try {
                    String clipboard = mc.keyboard.getClipboard();
                    if (selecting) {
                        currentString = clipboard;
                        cursorIndex = currentString.length();
                        selecting = false;
                    } else {
                        currentString = currentString.substring(0, cursorIndex) + clipboard
                                + currentString.substring(cursorIndex);
                        cursorIndex += clipboard.length();
                    }
                } catch (Exception exception) {
                    Anatorius.LOGGER.error("{}: Failed to process clipboard paste", exception.getClass().getName(),
                            exception);
                }
                return;
            }

            if (keyCode == GLFW.GLFW_KEY_C && selecting) {
                try {
                    mc.keyboard.setClipboard(currentString);
                } catch (Exception exception) {
                    Anatorius.LOGGER.error("{}: Failed to process clipboard change", exception.getClass().getName(),
                            exception);
                }
                return;
            }

            if (keyCode == GLFW.GLFW_KEY_A) {
                if (!currentString.isEmpty()) {
                    selecting = true;
                    cursorIndex = currentString.length();
                }
            }
        }
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        if (!listening)
            return;
        if (Character.isISOControl(chr))
            return;

        if (selecting) {
            currentString = String.valueOf(chr);
            cursorIndex = 1;
            selecting = false;
        } else {
            currentString = currentString.substring(0, cursorIndex) + chr + currentString.substring(cursorIndex);
            cursorIndex++;
        }
    }
}
