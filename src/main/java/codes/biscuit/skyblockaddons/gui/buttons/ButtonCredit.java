package codes.biscuit.skyblockaddons.gui.buttons;

import codes.biscuit.skyblockaddons.SkyblockAddons;
import codes.biscuit.skyblockaddons.utils.EnumUtils;
import codes.biscuit.skyblockaddons.core.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

public class ButtonCredit extends ButtonFeature {

    private static final ResourceLocation WEB = new ResourceLocation("skyblockaddons", "gui/web.png");

    private final SkyblockAddons main = SkyblockAddons.getInstance();

    private EnumUtils.FeatureCredit credit;
    private boolean smaller;

    // Used to calculate the transparency when fading in.
    private long timeOpened = System.currentTimeMillis();

    public ButtonCredit(double x, double y, String buttonText, EnumUtils.FeatureCredit credit, Feature feature, boolean smaller) {
        super(0, (int)x, (int)y, buttonText, feature);
        this.feature = feature;
        this.width = 12;
        this.height = 12;
        this.credit = credit;
        this.smaller = smaller;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (visible) {
            float alphaMultiplier = 1F;
            if (main.getUtils().isFadingIn()) {
                long timeSinceOpen = System.currentTimeMillis() - timeOpened;
                int fadeMilis = 500;
                if (timeSinceOpen <= fadeMilis) {
                    alphaMultiplier = (float) timeSinceOpen / fadeMilis;
                }
            }
            float scale = 0.8F;
            if (smaller) {
                scale = 0.6F;
            }

            hovered = mouseX >= this.xPosition*scale && mouseY >= this.yPosition*scale && mouseX < this.xPosition*scale +
                    this.width*scale && mouseY < this.yPosition*scale + this.height*scale;
            GlStateManager.enableBlend();

            if (hovered) {
                GlStateManager.color(1,1,1,alphaMultiplier*1);
            } else {
                GlStateManager.color(1,1,1,alphaMultiplier*0.7F);
            }
            if (main.getConfigValues().isRemoteDisabled(feature)) {
                GlStateManager.color(0.3F,0.3F,0.3F,0.7F);
            }
            GlStateManager.pushMatrix();
            GlStateManager.scale(scale,scale,1);
            mc.getTextureManager().bindTexture(WEB);
            drawModalRectWithCustomSizedTexture(xPosition,
                    yPosition, 0, 0, 12, 12, 12, 12);
            GlStateManager.popMatrix();
        }
    }

    public EnumUtils.FeatureCredit getCredit() {
        return credit;
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        float scale = 0.8F;
        return mouseX >= this.xPosition*scale && mouseY >= this.yPosition*scale && mouseX < this.xPosition*scale +
                this.width*scale && mouseY < this.yPosition*scale + this.height*scale;
    }
}
