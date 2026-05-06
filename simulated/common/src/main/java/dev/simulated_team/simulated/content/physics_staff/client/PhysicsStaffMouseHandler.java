package dev.simulated_team.simulated.content.physics_staff.client;

import com.simibubi.create.AllKeys;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.simulated_team.simulated.SimulatedClient;
import dev.simulated_team.simulated.config.client.items.SimItemConfigs;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffAction;
import dev.simulated_team.simulated.service.SimConfigService;
import dev.simulated_team.simulated.util.click_interactions.InteractCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

public class PhysicsStaffMouseHandler implements InteractCallback {

    @Override
    public Result onAttack(final int modifiers, final int action, final KeyMapping leftKey) {
        if (SimulatedClient.PHYSICS_STAFF_CLIENT_HANDLER.holdingStaff && action == GLFW.GLFW_PRESS) {
            SimulatedClient.PHYSICS_STAFF_CLIENT_HANDLER.onItemPunched();
            return new Result(true);
        }

        return InteractCallback.super.onAttack(modifiers, action, leftKey);
    }

    @Override
    public Result onUse(final int modifiers, final int action, final KeyMapping rightKey) {
        if (SimulatedClient.PHYSICS_STAFF_CLIENT_HANDLER.holdingStaff && action == GLFW.GLFW_PRESS) {
            SimulatedClient.PHYSICS_STAFF_CLIENT_HANDLER.onItemUsed(PhysicsStaffAction.START_DRAG);
            return new Result(true);
        }
        return InteractCallback.super.onUse(modifiers, action, rightKey);
    }

    @Override
    public Result onMouseMove(final double yaw, final double pitch) {
        final Minecraft mc = Minecraft.getInstance();
        final PhysicsStaffClientHandler handler = SimulatedClient.PHYSICS_STAFF_CLIENT_HANDLER;

        if (handler.isRotating()) {
            final SimItemConfigs config = SimConfigService.INSTANCE.client().itemConfig;

            ClientDragSession dragSession = handler.dragSession;
            assert dragSession != null;

            boolean isSnapped = AllKeys.CTRL_MODIFIER.isPressed();
            if (isSnapped) {
                rotateSnapped(yaw, pitch, config, dragSession, mc);
            } else {
                rotateFreely(yaw, pitch, config, dragSession, mc);
            }
            dragSession.setLastInputState(isSnapped ? ClientDragSession.DragRotationType.SNAPPED : ClientDragSession.DragRotationType.FREE);

            return new Result(true);
        }

        return InteractCallback.super.onMouseMove(yaw, pitch);
    }

    private void rotateSnapped(double yaw, double pitch, SimItemConfigs config, ClientDragSession dragSession, Minecraft mc) {
        final double rotationSensitivity = config.physicsStaffRotateSensitivity.get();
        assert mc.player != null;

        double snappingangletempreplacewithconfig = Math.PI / 8;

        if (dragSession.getLastInputState() == ClientDragSession.DragRotationType.FREE) {
            //Update horizontal rotation axis
            final Vector3d forward = JOMLConversion.toJOML(mc.player.getLookAngle());
            dragSession.getOrientation().transformInverse(forward);
            dragSession.setHorizontalSnapRotationAxis(Direction.getNearest(forward.x, 0, forward.z));
        }

        final Direction horizontalRotationAxis = dragSession.getHorizontalSnapRotationAxis();
        final int horizontalSnapRotationSign =
                (horizontalRotationAxis.getAxisDirection() == Direction.AxisDirection.POSITIVE
                        ? 1 : -1);

        if (dragSession.getLastInputState() == ClientDragSession.DragRotationType.FREE) {
            //Take the current orientation and snap it into the local snapOrientation coordinates,
            // discarding the axis that isn't the horizontal snap axis
            final Vector3d eulerYXZ = dragSession.getOrientation().getEulerAnglesYXZ(new Vector3d());

            eulerYXZ.setComponent(horizontalRotationAxis.getAxis().ordinal(), 0);

            eulerYXZ.set(
                    Math.round(eulerYXZ.x() / snappingangletempreplacewithconfig) * snappingangletempreplacewithconfig,
                    Math.round(eulerYXZ.y() / snappingangletempreplacewithconfig) * snappingangletempreplacewithconfig,
                    Math.round(eulerYXZ.z() / snappingangletempreplacewithconfig) * snappingangletempreplacewithconfig
            );

            dragSession.getSnapOrientation()
                    .set(eulerYXZ.x(), eulerYXZ.y())
                    .div(horizontalSnapRotationSign, 1);
        }

        final double pitchChange = Math.toRadians(pitch) * rotationSensitivity;
        final double yawChange = Math.toRadians(yaw) * rotationSensitivity;

        dragSession.getSnapOrientation().add(pitchChange, yawChange);

        double yawSnapped = Math.round(dragSession.getSnapOrientation().y() / snappingangletempreplacewithconfig) * snappingangletempreplacewithconfig;
        double pitchSnapped = Math.round(dragSession.getSnapOrientation().x() / snappingangletempreplacewithconfig) * snappingangletempreplacewithconfig;

        final Quaterniond orientation = dragSession.getOrientation();
        orientation.identity().rotateY(yawSnapped);

        if (horizontalRotationAxis.getAxis() == Direction.Axis.Z) {
            orientation.rotateX(pitchSnapped * horizontalSnapRotationSign);
        } else {
            orientation.rotateZ(pitchSnapped * horizontalSnapRotationSign);
        }
    }

    private void rotateFreely(double yaw, double pitch, SimItemConfigs config, ClientDragSession dragSession, Minecraft mc) {
        assert mc.player != null;
        final double rotationSensitivity = config.physicsStaffRotateSensitivity.get();

        final Vec3 axis = mc.player.calculateViewVector(0.0f, mc.player.getYRot() - 90.0f);
        final Quaterniond orientation = dragSession.getOrientation();

        final double yawChange = Math.toRadians(yaw) * rotationSensitivity;

        orientation.rotateLocalY(yawChange);
        orientation.premul(new Quaterniond(new AxisAngle4d(Math.toRadians(-pitch) * rotationSensitivity, axis.x, axis.y, axis.z)));
    }

    @Override
    public Result onScroll(final double deltaX, final double deltaY) {
        final PhysicsStaffClientHandler handler = SimulatedClient.PHYSICS_STAFF_CLIENT_HANDLER;
        final ClientDragSession dragSession = handler.dragSession;

        final SimItemConfigs config = SimConfigService.INSTANCE.client().itemConfig;
        final double scrollSensitivity = config.physicsStaffScrollSensitivity.get();

        if (handler.holdingStaff && dragSession != null) {
            final double currentDistance = dragSession.getDistance();
            final boolean sprint = Minecraft.getInstance().options.keySprint.isDown();
            final double sensMultiplier = Mth.clamp(Math.pow(currentDistance / 10.0, 0.5), 1.0, 5) * (sprint ? 4 : 1);
            dragSession.setDistance(handler.clampDistance(currentDistance + deltaY * scrollSensitivity * sensMultiplier));
            return new Result(true);
        }
        return InteractCallback.super.onScroll(deltaX, deltaY);
    }
}
