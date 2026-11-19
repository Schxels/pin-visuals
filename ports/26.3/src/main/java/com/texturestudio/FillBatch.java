package com.texturestudio;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/**
 * Many plain colour rectangles submitted to the GUI as ONE element.
 *
 * Minecraft's GUI searches backwards through everything already drawn for every single element it receives (to put
 * it on the right layer). The Pin Visuals menu draws thousands of small rectangles (rounded corners, gradients,
 * glows), which made that search dominate the frame time. Collecting them here keeps the element count small.
 * The batch is flushed before anything else (text, images, items) is drawn, so the drawing order stays the same.
 */
final class FillBatch implements GuiElementRenderState {

	private static final Matrix3x2fc IDENTITY = new Matrix3x2f();

	private float[] data = new float[4 * 256]; // x0, y0, x1, y1
	private int[] cols = new int[256];
	private int count;
	private float minX = Float.MAX_VALUE;
	private float minY = Float.MAX_VALUE;
	private float maxX = -Float.MAX_VALUE;
	private float maxY = -Float.MAX_VALUE;

	boolean isEmpty() {
		return count == 0;
	}

	void add(float x0, float y0, float x1, float y1, int color) {
		if (x0 == x1 || y0 == y1) {
			return;
		}
		if (count + 1 > cols.length) {
			data = java.util.Arrays.copyOf(data, data.length * 2);
			cols = java.util.Arrays.copyOf(cols, cols.length * 2);
		}
		int i = count * 4;
		data[i] = x0;
		data[i + 1] = y0;
		data[i + 2] = x1;
		data[i + 3] = y1;
		cols[count] = color;
		count++;
		minX = Math.min(minX, x0);
		minY = Math.min(minY, y0);
		maxX = Math.max(maxX, x1);
		maxY = Math.max(maxY, y1);
	}

	@Override
	public void buildVertices(VertexConsumer v) {
		for (int q = 0; q < count; q++) {
			int i = q * 4;
			float x0 = data[i];
			float y0 = data[i + 1];
			float x1 = data[i + 2];
			float y1 = data[i + 3];
			int c = cols[q];
			v.addVertexWith2DPose(IDENTITY, x0, y0).setColor(c);
			v.addVertexWith2DPose(IDENTITY, x0, y1).setColor(c);
			v.addVertexWith2DPose(IDENTITY, x1, y1).setColor(c);
			v.addVertexWith2DPose(IDENTITY, x1, y0).setColor(c);
		}
	}

	@Override
	public RenderPipeline pipeline() {
		return RenderPipelines.GUI;
	}

	@Override
	public TextureSetup textureSetup() {
		return TextureSetup.noTexture();
	}

	@Override
	public ScreenRectangle scissorArea() {
		return null;
	}

	@Override
	public ScreenRectangle bounds() {
		int bx = (int) Math.floor(minX);
		int by = (int) Math.floor(minY);
		return new ScreenRectangle(bx, by, (int) Math.ceil(maxX) - bx, (int) Math.ceil(maxY) - by);
	}

	// ------------------------------------------------------------------ collector used by the screen

	private static FillBatch open;

	/** Starts collecting fills for this frame. */
	static void begin() {
		open = new FillBatch();
	}

	/** Submits what was collected so far (call before drawing anything that is not a plain fill). */
	static GuiGraphicsExtractor flush(GuiGraphicsExtractor g) {
		FillBatch b = open;
		if (b != null && !b.isEmpty()) {
			((com.texturestudio.mixin.GuiGraphicsAccess) g).pinvisuals$renderState().addGuiElement(b);
			open = new FillBatch();
		}
		return g;
	}

	/** Submits the rest and stops collecting. */
	static void end(GuiGraphicsExtractor g) {
		flush(g);
		open = null;
	}

	// ---- software clip and window transform (used by the new menu: scrolling lists, open/close animation)
	private static boolean clipOn;
	private static int clipX0;
	private static int clipY0;
	private static int clipX1;
	private static int clipY1;
	private static boolean xfOn;
	private static float xfScale = 1f;
	private static float xfPx;
	private static float xfPy;
	private static float xfAlpha = 1f;

	/** Fills are cut to this rectangle until {@link #noClip()}. */
	static void clip(int x0, int y0, int x1, int y1) {
		clipOn = true;
		clipX0 = x0;
		clipY0 = y0;
		clipX1 = x1;
		clipY1 = y1;
	}

	static void noClip() {
		clipOn = false;
	}

	/** Batched fills are scaled around (px, py) and faded (alpha 0..1) until {@link #noTransform()}. */
	static void transform(float scale, float px, float py, float alpha) {
		xfOn = true;
		xfScale = scale;
		xfPx = px;
		xfPy = py;
		xfAlpha = Math.max(0f, Math.min(1f, alpha));
	}

	static void noTransform() {
		xfOn = false;
	}

	/** Like g.fill, but batched while a batch is open and nothing is transformed or clipped. */
	static void fill(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
		if (x0 > x1) {
			int t = x0;
			x0 = x1;
			x1 = t;
		}
		if (y0 > y1) {
			int t = y0;
			y0 = y1;
			y1 = t;
		}
		if (clipOn) {
			x0 = Math.max(x0, clipX0);
			y0 = Math.max(y0, clipY0);
			x1 = Math.min(x1, clipX1);
			y1 = Math.min(y1, clipY1);
		}
		if (x0 >= x1 || y0 >= y1) {
			return;
		}
		FillBatch b = open;
		if (b != null && plain(g)) {
			if (xfOn) {
				int a = Math.round((color >>> 24) * xfAlpha);
				if (a <= 0) {
					return;
				}
				b.add(xfPx + (x0 - xfPx) * xfScale, xfPy + (y0 - xfPy) * xfScale, xfPx + (x1 - xfPx) * xfScale,
						xfPy + (y1 - xfPy) * xfScale, (a << 24) | (color & 0xFFFFFF));
			} else {
				b.add(x0, y0, x1, y1, color);
			}
		} else {
			flush(g).fill(x0, y0, x1, y1, color);
		}
	}

	private static boolean plain(GuiGraphicsExtractor g) {
		Matrix3x2fc m = g.pose();
		// (the menu never clips with scissors, so only the transform has to be checked)
		return m.m00() == 1f && m.m11() == 1f && m.m01() == 0f && m.m10() == 0f && m.m20() == 0f && m.m21() == 0f;
	}
}
