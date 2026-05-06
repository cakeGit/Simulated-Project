package dev.simulated_team.simulated.content.physics_staff.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public class LoopingSoundInstance extends AbstractTickableSoundInstance {
    private final LocalPlayer player;

    protected LoopingSoundInstance(final LocalPlayer player, final SoundEvent event, final RandomSource random) {
        super(event, SoundSource.PLAYERS, random);
        this.player = player;

    }

    public void setVolume(final float volume) {
        this.volume = volume;
    }

    public void setPitch(final float pitch) {
        this.pitch = pitch;
    }

    @Override
    public double getX() {
        return this.player.position().x();
    }

    @Override
    public double getY() {
        return this.player.position().y();
    }

    @Override
    public double getZ() {
        return this.player.position().z();
    }

    @Override
    public void tick() {

    }
}
