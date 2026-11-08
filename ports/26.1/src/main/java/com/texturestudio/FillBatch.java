package com.texturestudio;

import com.mojang.blaze3d.pipeline.RenderPipeline;
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

	private int[] data = new int[6 * 256]; // x0, y0, x1, y1, colTop, colBottom
	private int count;
	private int minX = Integer.MAX_VALUE;
	private int minY = Integer.MAX_VALUE;
	private int maxX = Integer.MIN_VALUE;
	private int maxY = Integer.MIN_VALUE;

	boolean isEmpty() {
		return count == 0;
	}

	void add(int x0, int y0, int x1, int y1, int color) {
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
		if (x0 == x1 || y0 == y1) {
			return;
		}
		if ((count + 1) * 6 > data.length) {
			data = java.util.Arrays.copyOf(data, data.length * 2);
		}
		int i = count * 6;
		data[i] = x0;
		data[i + 1] = y0;
		data[i + 2] = x1;
		data[i + 3] = y1;
		data[i + 4] = color;
		data[i + 5] = color;
		count++;
		minX = Math.min(minX, x0);
		minY = Math.min(minY, y0);
		maxX = Math.max(maxX, x1);
		maxY = Math.max(maxY, y1);
	}

	@Override
	public void buildVertices(VertexConsumer v) {
		for (int q = 0; q < count; q++) {
			int i = q * 6;
			float x0 = data[i];
			float y0 = data[i + 1];
			float x1 = data[i + 2];
			float y1 = data[i + 3];
			int c1 = data[i + 4];
			int c2 = data[i + 5];
			v.addVertexWith2DPose(IDENTITY, x0, y0).setColor(c1);
			v.addVertexWith2DPose(IDENTITY, x0, y1).setColor(c2);
			v.addVertexWith2DPose(IDENTITY, x1, y1).setColor(c2);
			v.addVertexWith2DPose(IDENTITY, x1, y0).setColor(c1);
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
		return new ScreenRectangle(minX, minY, maxX - minX, maxY - minY);
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

	/** Like g.fill, but batched while a batch is open and nothing is transformed or clipped. */
	static void fill(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
		FillBatch b = open;
		if (b != null && plain(g)) {
			b.add(x0, y0, x1, y1, color);
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
