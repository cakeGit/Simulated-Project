package dev.simulated_team.simulated.content.physics_staff.client;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.createmod.catnip.outliner.LineOutline;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class PhysicsBeam {
    private static final float TARGET_SPACING = 1.5f;
    private static final int MIN_POINTS = 8;
    private final LineOutline line;
    private final double targetNodeRadius = 0.2;
    private final List<BeamNode> nodes = new ObjectArrayList<>();
    protected float extension;
    protected float previousExtension;
    protected float cubeScale;
    protected float previousCubeScale;
    protected float intensity;
    protected Vec3 start;
    protected Vec3 end;
    protected Vec3 previousStart;
    protected Vec3 previousEnd;
    protected Vec3 serverStart;
    protected Vec3 serverEnd;
    protected double length;
    protected double currentNodeRadius = 0;

    public PhysicsBeam(final Vec3 start, final Vec3 end, final double length) {
        this.start = start;
        this.previousStart = start;
        this.serverStart = start;
        this.end = end;
        this.previousEnd = end;
        this.serverEnd = end;
        this.intensity = 1;
        this.line = new LineOutline();
        this.line.getParams().colored(0xffffff).disableLineNormals().lineWidth(0.6f / 16f);
        this.length = length;
        this.extension = 0;
        this.update();
    }

    protected void update() {
        final double scaledLength = this.length / TARGET_SPACING;
        final double targetCount = MIN_POINTS * MIN_POINTS / (scaledLength + MIN_POINTS) + scaledLength;

        if (targetCount > 4096.0)
            return;

        this.currentNodeRadius = this.targetNodeRadius * Math.sqrt(scaledLength / targetCount);

        while (this.nodes.size() < targetCount - 0.7) {
            this.nodes.add(new BeamNode());
        }
        while (this.nodes.size() > targetCount + 0.7) {
            this.nodes.remove(0);
        }
        for (int i = 1; i < this.nodes.size() - 1; i++) {
            this.nodes.get(i).update();
        }

        this.previousExtension = this.extension;
        this.previousCubeScale = this.cubeScale;
        if (this.intensity < 0.4) {
            this.extension = Mth.lerp(0.5f, this.extension, 0);
        } else {
            this.extension = Mth.lerp(0.5f, this.extension, 1);
        }
        this.cubeScale = this.extension;
    }

    protected void render(final Vec3 start, final Vec3 end, final PoseStack ms, final SuperRenderTypeBuffer buffer, final Vec3 camera, final float pt) {
        final Vec3 relative = end.subtract(start);
        this.length = relative.length();

        Vec3 lastPos = start;

        for (int i = 1; i < this.nodes.size(); i++) {
            final Vec3 offset = this.nodes.get(i).previousPosition.lerp(this.nodes.get(i).position, pt);
            final Vec3 currentPos = start.add(relative.scale(i / (float) this.nodes.size()).add(offset.scale(this.currentNodeRadius)));
            this.line.set(lastPos, currentPos).render(ms, buffer, camera, pt);
            lastPos = currentPos;
        }
    }

    private static class BeamNode {
        Vec3 position = new Vec3(0, 0, 0);
        Vec3 previousPosition = new Vec3(0, 0, 0);

        void update() {
            final RandomSource random = Minecraft.getInstance().level.random;
            this.previousPosition = this.position;
            this.position = this.position.offsetRandom(random, 3).scale(0.5);
        }
    }

}
