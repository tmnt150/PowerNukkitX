package cn.nukkit.entity.ai.memory;

import cn.nukkit.api.PowerNukkitXOnly;
import cn.nukkit.api.Since;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 实体记忆对象，表示单个实体记忆数据
 */
@PowerNukkitXOnly
@Since("1.6.0.0-PNX")
public interface IMemory<T> {
    default String getName() {return this.getClass().getSimpleName();};

    @Nullable
    T getData();

    default boolean equals(IMemory<T> memory) {
        return getName().equals(memory.getName());
    }
}
