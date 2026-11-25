package dev.project.anatorius.customsky.client.sky;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public final class SkyboxEntry {
   public static final int NO_COLOR = -1;
   private final Identifier texture;
   private final boolean isGif;
   private final int color;
   private final long appliedAtTick;
   private final SkyParams params;

   private SkyboxEntry(Identifier texture, boolean isGif, int color, long appliedAtTick, SkyParams params) {
      this.texture = texture;
      this.isGif = isGif;
      this.color = color;
      this.appliedAtTick = appliedAtTick;
      this.params = params == null ? SkyParams.EMPTY : params;
   }

   public static SkyboxEntry ofTexture(Identifier texture, boolean isGif, SkyParams params) {
      return new SkyboxEntry(texture, isGif, -1, 0L, params);
   }

   public static SkyboxEntry ofColor(int color, SkyParams params) {
      return new SkyboxEntry((Identifier)null, false, color, 0L, params);
   }

   public static SkyboxEntry ofVanilla(SkyParams params) {
      return new SkyboxEntry((Identifier)null, false, -1, 0L, params);
   }

   public Identifier texture() {
      return this.texture;
   }

   public boolean isGif() {
      return this.isGif;
   }

   public int color() {
      return this.color;
   }

   public long appliedAtTick() {
      return this.appliedAtTick;
   }

   public SkyParams params() {
      return this.params;
   }

   public boolean isVanilla() {
      return this.texture == null && this.color == -1;
   }

   public float scale() {
      return this.params.getFloat(SkyParam.SCALE);
   }

   public float opacity() {
      return this.params.getFloat(SkyParam.OPACITY);
   }

   public int fadeInTicks() {
      return this.params.getInt(SkyParam.FADE_IN);
   }

   public int fadeOutTicks() {
      return this.params.getInt(SkyParam.FADE_OUT);
   }

   public float speed() {
      return this.params.getFloat(SkyParam.SPEED);
   }

   public float gifSpeed() {
      return this.params.getFloat(SkyParam.GIF_SPEED);
   }

   public float rotation() {
      return this.params.getFloat(SkyParam.ROTATION);
   }

   public int starAmount() {
      return Math.max(1, Math.min(10, this.params.getInt(SkyParam.AMOUNT)));
   }

   public boolean dayCycle() {
      return this.params.getBool(SkyParam.DAY_CYCLE);
   }

   public boolean skyColor() {
      return this.params.getBool(SkyParam.SKY_COLOR);
   }

   public boolean smoothEdges() {
      return this.params.getBool(SkyParam.SMOOTH_EDGES);
   }

   public int layer() {
      return Math.max(1, Math.min(8, this.params.getInt(SkyParam.LAYER)));
   }

   public boolean hasPosX() {
      return this.params.has(SkyParam.POS_X);
   }

   public boolean hasPosY() {
      return this.params.has(SkyParam.POS_Y);
   }

   public float posX() {
      return this.params.getFloat(SkyParam.POS_X);
   }

   public float posY() {
      return this.params.getFloat(SkyParam.POS_Y);
   }

   public float weatherDirection() {
      return this.params.has(SkyParam.DIRECTION) ? this.params.getFloat(SkyParam.DIRECTION) : Float.NaN;
   }

   public float weatherTilt() {
      return this.params.getFloat(SkyParam.TILT);
   }

   public SkyAnim animIn() {
      return this.params.getAnim(SkyParam.ANIM_IN);
   }

   public int animInTime() {
      return this.params.getInt(SkyParam.ANIM_IN_TIME);
   }

   public SkyAnim animOut() {
      return this.params.getAnim(SkyParam.ANIM_OUT);
   }

   public int animOutTime() {
      return this.params.getInt(SkyParam.ANIM_OUT_TIME);
   }

   public CelestialPreset preset() {
      return this.params.getPreset();
   }

   public RiftShape riftShape() {
      return this.params.getRiftShape();
   }

   public float riftRadius() {
      return this.params.getFloat(SkyParam.RADIUS);
   }

   public float riftLength() {
      return this.params.getFloat(SkyParam.LENGTH);
   }

   public RiftAnim riftAnimIn() {
      return this.params.getRiftAnim(SkyParam.ANIM_IN);
   }

   public RiftAnim riftAnimOut() {
      return this.params.getRiftAnim(SkyParam.ANIM_OUT);
   }

   public String background() {
      return this.params.getText(SkyParam.BACKGROUND);
   }

   public String model() {
      return this.params.getText(SkyParam.MODEL);
   }

   public float modelScale() {
      return this.params.getFloat(SkyParam.MODEL_SCALE);
   }

   public float modelPosX() {
      return this.params.getFloat(SkyParam.MODEL_POS_X);
   }

   public float modelPosY() {
      return this.params.getFloat(SkyParam.MODEL_POS_Y);
   }

   public String modelTexture() {
      return this.params.getText(SkyParam.MODEL_TEXTURE);
   }

   public int riftColor() {
      return this.params.has(SkyParam.COLOR) ? Math.round(this.params.getFloat(SkyParam.COLOR)) & 16777215 : 16728096;
   }

   public SkyOvercome overcome() {
      return this.params.getOvercome();
   }

   public int animationTime() {
      return this.params.getInt(SkyParam.ANIMATION_TIME);
   }

   public SkyboxEntry withAppliedTick(long tick) {
      return new SkyboxEntry(this.texture, this.isGif, this.color, tick, this.params);
   }

   public SkyboxEntry withParams(SkyParams newParams) {
      return new SkyboxEntry(this.texture, this.isGif, this.color, this.appliedAtTick, newParams);
   }

   public SkyboxEntry withOpacity(float opacity) {
      return this.withParams(this.params.with(SkyParam.OPACITY, opacity).with(SkyParam.FADE_IN, 0.0F).with(SkyParam.FADE_OUT, 0.0F));
   }
}
