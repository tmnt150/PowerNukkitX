package cn.nukkit.entity.item;

import cn.nukkit.api.PowerNukkitOnly;
import cn.nukkit.api.Since;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.Particle;
import cn.nukkit.level.particle.SpellParticle;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @author xtypr
 */
public class EntityExpBottle extends EntityProjectile {

    public static final int NETWORK_ID = 68;

    public EntityExpBottle(FullChunk chunk, CompoundTag nbt) {
        this(chunk, nbt, null);
    }

    public EntityExpBottle(FullChunk chunk, CompoundTag nbt, Entity shootingEntity) {
        super(chunk, nbt, shootingEntity);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.25f;
    }

    @Override
    public float getLength() {
        return 0.25f;
    }

    @Override
    public float getHeight() {
        return 0.25f;
    }

    @Override
    protected float getGravity() {
        return 0.1f;
    }

    @Override
    protected float getDrag() {
        return 0.01f;
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        this.timing.startTiming();

        boolean hasUpdate = super.onUpdate(currentTick);

        if (this.age > 1200) {
            this.kill();
            hasUpdate = true;
        }

        if (this.isCollided) {
            this.kill();
            this.dropXp();
            hasUpdate = true;
        }

        this.timing.stopTiming();

        return hasUpdate;
    }

    @Override
    public void onCollideWithEntity(Entity entity) {
        this.kill();
        this.dropXp();
    }

    public void dropXp() {
        Particle particle2 = new SpellParticle(this, 0x00385dc6);
        this.getLevel().addParticle(particle2);

        this.getLevel().addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_GLASS);

        this.getLevel().dropExpOrb(this, ThreadLocalRandom.current().nextInt(3, 12));
    }

    @PowerNukkitOnly
    @Override
    protected void addHitEffect() {
        this.getLevel().addSound(this, Sound.RANDOM_GLASS);
    }


    @PowerNukkitOnly
    @Since("1.5.1.0-PN")
    @Override
    public String getOriginalName() {
        return "Bottle o' Enchanting";
    }
}
