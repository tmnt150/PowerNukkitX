package cn.nukkit.entity.ai.executor;

import cn.nukkit.api.PowerNukkitXOnly;
import cn.nukkit.api.Since;
import cn.nukkit.entity.EntityIntelligent;
import cn.nukkit.entity.ai.memory.PlayerMemory;
import cn.nukkit.math.Vector3;

@PowerNukkitXOnly
@Since("1.6.0.0-PNX")
public class TestBaseMoveExecutor extends BaseMoveExecutor {

    @Override
    public boolean execute(EntityIntelligent entity) {
//        move(entity, new Vector3(0, 1, 0));
        lookAt(entity,(Vector3) entity.getBehaviorGroup().getMemory().get(PlayerMemory.class).getData());
        return false;
    }
}
