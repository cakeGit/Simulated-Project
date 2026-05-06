package dev.simulated_team.simulated.content.physics_staff.client;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.Direction;
import org.joml.Quaterniond;
import org.joml.Vector2d;
import org.joml.Vector3dc;

public final class ClientDragSession {
    private final SubLevel draggedSubLevel;
    private final Vector3dc localAnchor;

    private final Quaterniond orientation;

    private Direction horizontalSnapRotationAxis = Direction.NORTH;
    private final Vector2d snapOrientation;

    private DragRotationType lastInputState = DragRotationType.FREE;

    private double distance;

    public ClientDragSession(final SubLevel draggedSubLevel, final Vector3dc localAnchor,
                             final Quaterniond dragOrientation, final double distance) {
        this.draggedSubLevel = draggedSubLevel;
        this.localAnchor = localAnchor;
        this.orientation = dragOrientation;
        this.distance = distance;

        this.snapOrientation = new Vector2d();
    }

    public DragRotationType getLastInputState() {
        return lastInputState;
    }

    public void setLastInputState(DragRotationType lastInputState) {
        this.lastInputState = lastInputState;
    }

    public Direction getHorizontalSnapRotationAxis() {
        return this.horizontalSnapRotationAxis;
    }

    public void setHorizontalSnapRotationAxis(Direction horizontalSnapRotationAxis) {
        this.horizontalSnapRotationAxis = horizontalSnapRotationAxis;
    }

    public SubLevel getDraggedSubLevel() {
        return this.draggedSubLevel;
    }

    public Vector3dc getLocalAnchor() {
        return this.localAnchor;
    }

    public Quaterniond getOrientation() {
        return this.orientation;
    }

    public Vector2d getSnapOrientation() {
        return this.snapOrientation;
    }

    public double getDistance() {
        return this.distance;
    }

    public void setDistance(final double distance) {
        this.distance = distance;
    }

    @Override
    public String toString() {
        return "ClientDragSession[" +
                "dragSubLevel=" + this.draggedSubLevel + ", " +
                "dragLocalAnchor=" + this.localAnchor + ", " +
                "dragOrientation=" + this.orientation + ", " +
                "distance=" + this.distance + ']';
    }

    public enum DragRotationType {
        FREE, SNAPPED
    }
}
