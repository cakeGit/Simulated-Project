package dev.simulated_team.simulated.content.physics_staff.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllKeys;
import com.simibubi.create.CreateClient;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.SimulatedClient;
import dev.simulated_team.simulated.config.client.items.SimItemConfigs;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffAction;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffItem;
import dev.simulated_team.simulated.index.SimKeys;
import dev.simulated_team.simulated.index.SimSoundEvents;
import dev.simulated_team.simulated.network.packets.physics_staff.PhysicsStaffActionPacket;
import dev.simulated_team.simulated.network.packets.physics_staff.PhysicsStaffDragPacket;
import dev.simulated_team.simulated.service.SimConfigService;
import dev.simulated_team.simulated.util.SimDistUtil;
import dev.simulated_team.simulated.util.click_interactions.InteractCallback;
import foundry.veil.api.network.VeilPacketManager;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.*;
import org.lwjgl.glfw.GLFW;

import java.lang.Math;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Manages client logic for the physics staff locking & dragging
 */
public class PhysicsStaffClientHandler {
    protected final Object2ObjectMap<UUID, PhysicsBeam> beams = new Object2ObjectOpenHashMap<>();
    /**
     * All locks per dimension
     */
    private final Map<ResourceKey<Level>, List<UUID>> locks = new Object2ObjectOpenHashMap<>();
    private final Map<ResourceKey<Level>, Object2ObjectOpenHashMap<UUID, Vector3dc>> serverDragSessions = new Object2ObjectOpenHashMap<>();

    public float tilt = 0;
    public float previousTilt = 0;
    public float extension = 0;
    public float previousExtension = 0;
    public float targetExtension = 0;
    public float cubeScale = 0;
    public float previousCubeScale = 0;
    public Quaternionf lastCubeOrientation = new Quaternionf();
    protected State state = State.PASSIVE;
    protected boolean holdingStaff;

    @Nullable
    protected ClientDragSession dragSession;
    @Nullable
    protected LoopingSoundInstance sound;

    public static Vec3 getStaffFocusPos(final Player player, final boolean mainHand, final float pt) {
        final Minecraft minecraft = Minecraft.getInstance();
        final Camera camera = minecraft.gameRenderer.getMainCamera();

        if (player.isLocalPlayer() && !camera.isDetached()) {
            return PhysicsStaffItemRenderer.getFirstPersonFocusPos(pt)
                    .add(player.getPosition(pt)).add(0, Mth.lerp(pt, camera.eyeHeightOld, camera.eyeHeight), 0);
        }

        final Vec3 viewDirection = player.calculateViewVector(0.0f, player.getPreciseBodyRotation(pt));
        final Vec3 handDirection = player.calculateViewVector(0.0f, player.getPreciseBodyRotation(pt) + 90.0f);
        return player.getPosition(pt).add(0.0, 1.28, 0.0).add(viewDirection.scale(1.275)).add(handDirection.scale(0.325 * (mainHand ? 1 : -1)));
    }

    private static void spawnParticles(final InteractionHand hand, final SubLevel subLevel, final Vec3 hitLocation, final Level level) {
        CreateClient.ZAPPER_RENDER_HANDLER.shoot(hand, subLevel.logicalPose().transformPosition(hitLocation));

        final RandomSource random = level.getRandom();
        final Supplier<Double> randomSpeed = () -> (random.nextDouble() - .5d) * .2f;

        for (int i = 0; i < 10; i++) {
            level.addParticle(ParticleTypes.END_ROD, hitLocation.x, hitLocation.y, hitLocation.z, randomSpeed.get(), randomSpeed.get(), randomSpeed.get());
        }
    }

    public void onItemPunched() {
        this.onItemUsed(PhysicsStaffAction.LOCK);
    }

    public boolean isHoldingStaff() {
        return this.holdingStaff;
    }

    public void onItemUsed(final PhysicsStaffAction action) {
        if (!this.holdingStaff)
            return;

        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;

        if (action == PhysicsStaffAction.START_DRAG && this.state == State.DRAGGING) {
            if (player != null) {
                player.playSound(SimSoundEvents.STAFF_EXTINGUISH.event());
            }
            this.stopDragging();
            return;
        }

        final Level level = player.level();
        final InteractionHand hand = InteractionHand.MAIN_HAND;

        // lock currently holding sub-level
        if (this.dragSession != null && action == PhysicsStaffAction.LOCK) {
            final Vec3 hitLocation = JOMLConversion.toMojang(this.dragSession.getLocalAnchor());
            this.lockSubLevel(this.dragSession.getDraggedSubLevel(), hitLocation, player, hand);
            spawnParticles(hand, this.dragSession.getDraggedSubLevel(), hitLocation, level);

            if (this.state == State.DRAGGING) {
                this.stopDragging();
            }
            this.state = State.LOCKING;
            return;
        }

        final HitResult hit = player.pick(PhysicsStaffItem.RANGE, 1.0f, false);

        if (!(hit instanceof final BlockHitResult blockHitResult) || blockHitResult.getType() == HitResult.Type.MISS) {
            this.state = State.OPENING;
            return;
        }

        final Vec3 hitLocation = hit.getLocation();
        final SubLevel subLevel = Sable.HELPER.getContainingClient(hitLocation);

        if (subLevel == null) {
            this.state = State.OPENING;
            return;
        }

        if (action == PhysicsStaffAction.START_DRAG) {
            if (this.state == State.DRAGGING) {
                this.stopDragging();
            }

            this.startDraggingSubLevel(subLevel, blockHitResult.getBlockPos(), player, hand);
            this.state = State.DRAGGING;
        }

        if (action == PhysicsStaffAction.LOCK) {
            this.lockSubLevel(subLevel, hitLocation, player, hand);

            if (this.state == State.DRAGGING) {
                this.stopDragging();
            }

            final Vec3 focusPos = getStaffFocusPos(player, hand == InteractionHand.MAIN_HAND, 1.0f);
            this.updateBeam(level, player.getUUID(), focusPos, hitLocation);

            this.state = State.LOCKING;
        }

        spawnParticles(hand, subLevel, hitLocation, level);
    }

    private void startDraggingSubLevel(final SubLevel subLevel, final BlockPos blockPos, final LocalPlayer player, final InteractionHand hand) {
        final Vector3d localAnchor = JOMLConversion.atCenterOf(blockPos);

        this.dragSession = new ClientDragSession(
                subLevel,
                localAnchor,
                new Quaterniond(subLevel.logicalPose().orientation()),
                this.clampDistance(player.getEyePosition().distanceTo(subLevel.logicalPose().transformPosition(JOMLConversion.toMojang(localAnchor))))
        );

        final SoundEvent soundEvent = this.isLocked(subLevel) ? SimSoundEvents.STAFF_UNLOCK.event() : SimSoundEvents.STAFF_IGNITE.event();
        player.playSound(soundEvent);
    }

    private void lockSubLevel(final SubLevel subLevel, final Vec3 hitLocation, final LocalPlayer player, final InteractionHand hand) {
        VeilPacketManager.server().sendPacket(new PhysicsStaffActionPacket(PhysicsStaffAction.LOCK, subLevel.getUniqueId(), JOMLConversion.toJOML(hitLocation)));

        final SoundEvent soundEvent = this.isLocked(subLevel) ? SimSoundEvents.STAFF_UNLOCK.event() : SimSoundEvents.STAFF_LOCK.event();
        player.playSound(soundEvent);
    }

    private boolean isLocked(final SubLevel subLevel) {
        final List<UUID> locks = this.locks.get(subLevel.getLevel().dimension());
        return locks.contains(subLevel.getUniqueId());
    }

    public void tick() {
        final Player player = SimDistUtil.getClientPlayer();

        if (player == null) {
            this.reset();
            return;
        }

        this.holdingStaff = PhysicsStaffItem.isHolding(player);

        if (!this.holdingStaff) {
            this.state = State.PASSIVE;
            this.dragSession = null;
        }

        switch (this.state) {
            case LOCKING, OPENING -> {
                if (this.extension > 0.97) {
                    this.state = State.PASSIVE;
                }
            }
            case DRAGGING -> {
                assert this.dragSession != null;
                final SubLevel draggingSubLevel = this.dragSession.getDraggedSubLevel();

                if (draggingSubLevel != null && draggingSubLevel.isRemoved()) {
                    this.stopDragging();
                } else {
                    final Vec3 focusPos = getStaffFocusPos(player, player.getMainHandItem().getItem() instanceof PhysicsStaffItem, 1.0f);
                    this.updateBeam(player.level(), player.getUUID(), focusPos, JOMLConversion.toMojang(this.dragSession.getLocalAnchor()));
                    this.sendDraggingData(player);

                    if (this.sound == null) {
                        this.sound = new LoopingSoundInstance((LocalPlayer) player, SimSoundEvents.STAFF_IDLE.event(), player.level().getRandom());
                    }

                    if (!Minecraft.getInstance().getSoundManager().isActive(this.sound)) {
                        Minecraft.getInstance().getSoundManager().play(this.sound);
                    }

                    this.sound.setVolume(1.0f);
                }
            }
            case PASSIVE -> {
                if (this.sound != null) {
                    this.sound.setVolume(0.0f);
                }
            }
        }

        final Object2ObjectOpenHashMap<UUID, Vector3dc> sessions = this.serverDragSessions.get(player.level().dimension());

        if (sessions != null) {
            for (final Map.Entry<UUID, Vector3dc> draggingEntry : sessions.entrySet()) {
                if (draggingEntry.getKey().equals(player.getUUID())) {
                    // Skip the player's own drag session
                    continue;
                }

                final UUID playerId = draggingEntry.getKey();
                final Vector3dc localAnchor = draggingEntry.getValue();

                final SubLevel draggingSubLevel = Sable.HELPER.getContaining(player.level(), localAnchor);

                if (draggingSubLevel == null) {
                    continue;
                }

                final Player otherPlayer = player.level().getPlayerByUUID(playerId);
                if (otherPlayer == null) {
                    // If the player is not online, we can skip this
                    continue;
                }

                final Vec3 focusPos = getStaffFocusPos(otherPlayer, otherPlayer.getMainHandItem().getItem() instanceof PhysicsStaffItem, 1.0f);
                this.updateBeam(player.level(), playerId, focusPos, JOMLConversion.toMojang(localAnchor));
            }
        }

        final boolean isUsing = this.state != State.PASSIVE;
        this.targetExtension = isUsing ? 1.0f : 0.0f;
        final float targetCubeScale = (isUsing && this.state != State.OPENING) ? 1.0f : 0.0f;

        this.previousExtension = this.extension;
        this.extension = Mth.lerp(0.65f, this.extension, this.targetExtension);

        this.previousCubeScale = this.cubeScale;
        this.cubeScale = Mth.lerp(0.65f, this.cubeScale, targetCubeScale);

        this.previousTilt = this.tilt;
        this.tilt = Mth.lerp(0.65f, this.tilt, this.state == State.DRAGGING ? 1.0f : 0.0f);

        this.beams.values().removeIf(beam -> beam.intensity < .4f);
        if (this.beams.isEmpty())
            return;

        this.beams.forEach((uuid, beam) -> {
            beam.previousStart = beam.start;
            beam.previousEnd = beam.end;
            beam.start = beam.serverStart;
            beam.end = beam.serverEnd;
            beam.intensity *= .6f;
            beam.update();
        });
    }

    private void reset() {
        this.tilt = 0;
        this.previousTilt = 0;
        this.extension = 0;
        this.previousExtension = 0;
        this.targetExtension = 0;
        this.lastCubeOrientation.identity();
        this.state = State.PASSIVE;
        this.holdingStaff = false;
        this.dragSession = null;
        this.sound = null;
    }

    private void sendDraggingData(final Player player) {
        final ClientDragSession session = this.dragSession;
        assert session != null;

        final Vec3 goalPosition = player.getLookAngle().scale(session.getDistance());

        VeilPacketManager.server().sendPacket(new PhysicsStaffDragPacket(
                session.getDraggedSubLevel().getUniqueId(),
                JOMLConversion.toJOML(goalPosition),
                session.getLocalAnchor(),
                session.getOrientation()
        ));
    }

    private void stopDragging() {
        final ClientDragSession session = this.dragSession;
        assert session != null;

        VeilPacketManager.server().sendPacket(new PhysicsStaffActionPacket(
                PhysicsStaffAction.STOP_DRAG,
                session.getDraggedSubLevel().getUniqueId(),
                session.getLocalAnchor()
        ));
        this.dragSession = null;
        this.state = State.PASSIVE;

        final LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            this.beams.remove(player.getUUID());
        }
    }

    public void onRender(final PoseStack ms) {
        final SuperRenderTypeBuffer buffer = DefaultSuperRenderTypeBuffer.getInstance();
        final float pt = AnimationTickHolder.getPartialTicks();
        final Minecraft client = Minecraft.getInstance();
        final Vec3 camera = client.gameRenderer.getMainCamera().getPosition();

        this.beams.forEach((uuid, beam) -> {
            final Player player = client.level.getPlayerByUUID(uuid);

            if (player != null) {
                boolean mainHand = true;
                if (!(player.getMainHandItem().getItem() instanceof PhysicsStaffItem)) {
                    if (player.getOffhandItem().getItem() instanceof PhysicsStaffItem) {
                        mainHand = false;
                    }
                }

                final Vec3 focusPos = getStaffFocusPos(player, mainHand, pt);
                Vec3 interpolatedBeamEnd = beam.previousEnd.lerp(beam.end, pt);

                final ClientSubLevel subLevel = Sable.HELPER.getContainingClient(interpolatedBeamEnd);

                if (subLevel == null)
                    return;

                interpolatedBeamEnd = subLevel.renderPose(pt).transformPosition(interpolatedBeamEnd);

                beam.render(focusPos, interpolatedBeamEnd, ms, buffer, camera, pt);
            }
        });

        buffer.draw();
        RenderSystem.enableCull();
    }

    public void updateBeam(final Level level, final UUID uuid, final Vec3 start, final Vec3 end) {
        final PhysicsBeam beam = this.beams.get(uuid);
        if (beam == null) {
            this.beams.put(uuid, new PhysicsBeam(start, end, Math.sqrt(Sable.HELPER.distanceSquaredWithSubLevels(level, start, end))));
        } else {
            beam.serverStart = start;
            beam.serverEnd = end;
            beam.intensity = 1;
        }
    }

    public void setLocks(final ResourceKey<Level> dimension, final List<UUID> locks) {
        this.locks.put(dimension, locks);
    }

    protected List<UUID> getLocks(final Level level) {
        return this.locks.get(level.dimension());
    }

    public ClientDragSession getDragSession() {
        return this.dragSession;
    }

    public void setServerDragSessions(final ResourceKey<Level> dimension, final List<Pair<UUID, Vector3d>> newSessions) {
        final Object2ObjectOpenHashMap<UUID, Vector3dc> dragSessions = this.serverDragSessions.computeIfAbsent(dimension, (x) -> new Object2ObjectOpenHashMap<>());

        dragSessions.clear();
        for (final Pair<UUID, Vector3d> pair : newSessions) {
            dragSessions.put(pair.getFirst(), pair.getSecond());
        }
    }

    protected double clampDistance(final double distance) {
        return Math.clamp(distance, 2.0, PhysicsStaffItem.RANGE);
    }

    protected boolean isRotating() {
        return this.holdingStaff && this.dragSession != null && SimKeys.ROTATE_MODE.isPressed();
    }

    protected enum State {
        PASSIVE,
        LOCKING,
        DRAGGING,
        OPENING
    }

}