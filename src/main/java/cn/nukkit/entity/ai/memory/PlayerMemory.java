package cn.nukkit.entity.ai.memory;

import cn.nukkit.Player;
import cn.nukkit.api.PowerNukkitXOnly;
import cn.nukkit.api.Since;
import cn.nukkit.math.Vector3;

@PowerNukkitXOnly
@Since("1.6.0.0-PNX")
public abstract class PlayerMemory<P extends Player> extends Vector3Memory<P>{

    public PlayerMemory(P player) {
        super(player);
    }

    @Override
    public P getData() {
        return super.getData();
    }
}
