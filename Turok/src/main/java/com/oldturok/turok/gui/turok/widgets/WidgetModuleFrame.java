package com.oldturok.turok.gui.turok.widgets;

import com.oldturok.turok.gui.rgui.component.listen.UpdateListener;
import com.oldturok.turok.gui.rgui.component.listen.MouseListener;
import com.oldturok.turok.gui.rgui.component.container.use.Frame;
import com.oldturok.turok.gui.rgui.component.container.Container;
import com.oldturok.turok.gui.rgui.component.AlignedComponent;
import com.oldturok.turok.gui.rgui.render.AbstractComponentUI;
import com.oldturok.turok.gui.rgui.render.font.FontRenderer;
import com.oldturok.turok.gui.rgui.util.ContainerHelper;
import com.oldturok.turok.gui.rgui.component.Component;
import com.oldturok.turok.gui.rgui.poof.use.FramePoof;
import com.oldturok.turok.gui.rgui.util.Docking;
import com.oldturok.turok.module.ModuleManager;
import com.oldturok.turok.util.ColourHolder;
import com.oldturok.turok.gui.rgui.GUI;
import com.oldturok.turok.util.Wrapper;
import com.oldturok.turok.TurokMessage;
import com.oldturok.turok.gui.turok.*;
import com.oldturok.turok.TurokMod;

import org.lwjgl.opengl.GL11;

import com.oldturok.turok.util.TurokGL; // TurokGL.
import com.oldturok.turok.util.TurokColor; // TurokColor.

// Rina.
// Modfify.
public class WidgetModuleFrame <T extends Frame> extends AbstractComponentUI<Frame> {
    ColourHolder frameColour   = TurokGUI.primaryColour.setA(100);
    ColourHolder outlineColour = frameColour.darker();

    Component yLineComponent   = null;
    Component xLineComponent   = null;
    Component centerXComponent = null;
    Component centerYComponent = null;

    boolean centerX = false;
    boolean centerY = false;

    boolean effect_pinned_one = true;
    Boolean effect_pinned_r   = false;

    boolean effect_module_one = true;
    Boolean effect_module_r   = false;

    public static boolean effect = true;

    public static float color_pinned_r = 105.0f;

    public static int color_module_r = 105;

    public static int speed_effect = 1;

    private static final RootFontRenderer ff = new RootFontRenderer(0.90f);

    @Override
    public void renderComponent(Frame component, FontRenderer fontRenderer) {
        if (component.getOpacity() == 0) return;

        if (effect_pinned_r) {
            color_pinned_r += 0.25f;
        } else {
            color_pinned_r -= 0.25f;
        }

        if (color_pinned_r >= 150) {
            effect_pinned_r = false;
        }

        if (color_pinned_r <= 50) {
            effect_pinned_r = true;
        }

        TurokGL.DisableGL(GL11.GL_TEXTURE_2D);

        if (fontRenderer.getStringWidth(component.getTitle()) > component.getWidth()) {
            component.setWidth(fontRenderer.getStringWidth(component.getTitle()) + 1);
        }

        if (component.isPinneable()) {
            draw_pinned(component);
        } else {
            draw_module(component, fontRenderer);
        }

        TurokGL.FixRefreshColor();
    }

    public void draw_module(Frame component, FontRenderer fontRenderer) {
        if (effect_module_one) {
            if (effect_module_r) {
                color_module_r += 1;
            } else {
                color_module_r -= 1;
            }

            if (color_module_r >= 255) {
                effect_module_r = false;
            }

            if (color_module_r <= 105) {
                effect_module_r = true;
            }
        }

        TurokGL.refresh_color(0, 0, 0, 150);
        RenderHelper.drawFilledRectangle(0, 0, component.getWidth(), component.getHeight());

        TurokGL.refresh_color(0, 0, 0, 255);
        RenderHelper.drawFilledRectangle(0, 0, component.getWidth(), ff.getStringHeight(component.getTitle()) + 2);

        TurokColor color = new TurokColor(color_module_r, 0, 0);

        fontRenderer.drawString(1, 1, color.hex(), component.getTitle());
    }

    public void draw_pinned(Frame component) {
        if (component.isPinned()) {
            TurokGL.refresh_color(effect ? color_pinned_r : 0, 0, 0, 150);
        } else {
            TurokGL.refresh_color(0, 0, 0, 150);
        }

        RenderHelper.drawFilledRectangle(0, 0, component.getWidth(), component.getHeight());

        ff.drawString(1, 1, component.getTitle());
    }


    @Override
    public void handleMouseRelease(Frame component, int x, int y, int button) {
        yLineComponent   = null;
        xLineComponent   = null;
        centerXComponent = null;
        centerYComponent = null;
    }

    @Override
    public void handleMouseDrag(Frame component, int x, int y, int button) {
        super.handleMouseDrag(component, x, y, button);
    }

    @Override
    public void handleAddComponent(Frame component, Container container) {
        super.handleAddComponent(component, container);
        component.setOriginOffsetY(component.getTheme().getFontRenderer().getFontHeight() + 3);
        component.setOriginOffsetX(3);

        component.addMouseListener(new MouseListener() {
            @Override
            public void onMouseDown(MouseButtonEvent event) {
                int y = event.getY();
                int x = event.getX();

                if (y < 0) {
                    if (x < component.getWidth() && x > ff.getStringWidth(component.getTitle()) - component.getWidth()) {
                        if (component.isPinneable()) {
                            component.setPinned(!component.isPinned());
                        }
                    }
                }
            }

            @Override
            public void onMouseRelease(MouseButtonEvent event) {}

            @Override
            public void onMouseDrag(MouseButtonEvent event) {}

            @Override
            public void onMouseMove(MouseMoveEvent event) {}

            @Override
            public void onScroll(MouseScrollEvent event) {}
        });

        component.addUpdateListener(new UpdateListener() {
            @Override
            public void updateSize(Component component, int oldWidth, int oldHeight) {
                if (component instanceof Frame) {
                    TurokGUI.dock((Frame) component);
                }
            }
            @Override
            public void updateLocation(Component component, int oldX, int oldY) { }
        });

        component.addPoof(new Frame.FrameDragPoof<Frame, Frame.FrameDragPoof.DragInfo>() {
            @Override
            public void execute(Frame component, DragInfo info) {
                int x = info.getX();
                int y = info.getY();
                yLineComponent = null;
                xLineComponent = null;

                component.setDocking(Docking.NONE);

                if (x < 5) {
                    x = 0;
                    ContainerHelper.setAlignment(component, AlignedComponent.Alignment.LEFT);
                    component.setDocking(Docking.LEFT);
                }

                int diff = (x+component.getWidth()) * DisplayGuiScreen.getScale() - Wrapper.getMinecraft().displayWidth;
                if (-diff < 5){
                    x = (Wrapper.getMinecraft().displayWidth / DisplayGuiScreen.getScale())-component.getWidth();
                    ContainerHelper.setAlignment(component, AlignedComponent.Alignment.RIGHT);
                    component.setDocking(Docking.RIGHT);
                }

                if (y < 5) {
                    y = 0;
                    if (component.getDocking().equals(Docking.RIGHT))
                        component.setDocking(Docking.TOPRIGHT);
                    else if (component.getDocking().equals(Docking.LEFT))
                        component.setDocking(Docking.TOPLEFT);
                    else
                        component.setDocking(Docking.TOP);
                }

                diff = (y+component.getHeight()) * DisplayGuiScreen.getScale() - Wrapper.getMinecraft().displayHeight;
                if (-diff < 5) {
                    y = (Wrapper.getMinecraft().displayHeight / DisplayGuiScreen.getScale()) - component.getHeight();

                    if (component.getDocking().equals(Docking.RIGHT))
                        component.setDocking(Docking.BOTTOMRIGHT);
                    else if (component.getDocking().equals(Docking.LEFT))
                        component.setDocking(Docking.BOTTOMLEFT);
                    else
                        component.setDocking(Docking.BOTTOM);
                }

                if (Math.abs(((x + component.getWidth() / 2) * DisplayGuiScreen.getScale() * 2) - Wrapper.getMinecraft().displayWidth) < 5) { // Component is center-aligned on the x axis
                    xLineComponent = null;
                    centerXComponent = component;
                    centerX = true;
                    x = (Wrapper.getMinecraft().displayWidth / (DisplayGuiScreen.getScale() * 2)) - component.getWidth() / 2;
                    if (component.getDocking().isTop()) {
                        component.setDocking(Docking.CENTERTOP);
                    } else if (component.getDocking().isBottom()){
                        component.setDocking(Docking.CENTERBOTTOM);
                    } else {
                        component.setDocking(Docking.CENTERVERTICAL);
                    }
                    ContainerHelper.setAlignment(component, AlignedComponent.Alignment.CENTER);
                } else {
                    centerX = false;
                }

                if (Math.abs(((y + component.getHeight() / 2) * DisplayGuiScreen.getScale() * 2) - Wrapper.getMinecraft().displayHeight) < 5) { // Component is center-aligned on the y axis
                    yLineComponent = null;
                    centerYComponent = component;
                    centerY = true;
                    y = (Wrapper.getMinecraft().displayHeight / (DisplayGuiScreen.getScale() * 2)) - component.getHeight() / 2;
                    if (component.getDocking().isLeft()) {
                        component.setDocking(Docking.CENTERLEFT);
                    } else if (component.getDocking().isRight()) {
                        component.setDocking(Docking.CENTERRIGHT);
                    } else if (component.getDocking().isCenterHorizontal()) {
                        component.setDocking(Docking.CENTER);
                    } else {
                        component.setDocking(Docking.CENTERHOIZONTAL);
                    }
                } else {
                    centerY = false;
                }

                info.setX(x);
                info.setY(y);
            }
        });
    }
}
