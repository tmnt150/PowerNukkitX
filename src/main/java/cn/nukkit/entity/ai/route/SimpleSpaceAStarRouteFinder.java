package cn.nukkit.entity.ai.route;

import cn.nukkit.api.PowerNukkitXOnly;
import cn.nukkit.api.Since;
import cn.nukkit.entity.EntityIntelligent;
import cn.nukkit.entity.ai.route.blockevaluator.IBlockEvaluator;
import cn.nukkit.entity.ai.route.data.Node;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * 务必注意，三维标准A*寻路的代价十分高昂(比原版的洪水填充低得多)，切忌将最大寻路深度设置得太大！
 * TODO: 用BA*、JPS或者势能场寻路代替
 */
@PowerNukkitXOnly
@Since("1.6.0.0-PNX")
public class SimpleSpaceAStarRouteFinder extends SimpleFlatAStarRouteFinder {
    //直接移动成本
    protected final static int DIRECT_MOVE_COST = 10;
    //倾斜移动成本
    protected final static int OBLIQUE_2D_MOVE_COST = 14;
    protected final static int OBLIQUE_3D_MOVE_COST = 17;

    public SimpleSpaceAStarRouteFinder(IBlockEvaluator blockEvaluator, EntityIntelligent entity) {
        super(blockEvaluator, entity);
    }

    @Override
    protected int getBlockMoveCostAt(@NotNull Level level, Vector3 pos) {
        return level.getTickCachedBlock(pos.add(0, -1, 0)).getWalkThroughExtraCost();
    }

    @Override
    protected void putNeighborNodeIntoOpen(@NotNull Node node) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    var vec = node.getVector3().add(dx, dy, dz);
                    if (!existInCloseList(vec) && evalBlock(entity.level.getTickCachedBlock(vec))) {
//                        if (vec.y % 1 + entity.getHeight() > 1 && evalBlock(entity.level.getTickCachedBlock(vec.add(0, 1,0)))) {
//                            continue;
//                        }
                        // 计算移动1格的开销
                        var cost = switch (Math.abs(dx) + Math.abs(dy) + Math.abs(dz)) {
                            case 1 -> DIRECT_MOVE_COST;
                            case 2 -> OBLIQUE_2D_MOVE_COST;
                            case 3 -> OBLIQUE_3D_MOVE_COST;
                            default -> Integer.MIN_VALUE;
                        } + getBlockMoveCostAt(level, vec) + node.getG() - dy; // -dy是为了倾向于从空中飞而不是贴地飞
                        if (cost < 0) continue;
                        var nodeNear = getOpenNode(vec);
                        if (nodeNear == null) {
                            this.openList.offer(new Node(vec, node, cost, calH(vec, target)));
                        } else {
                            if (cost < nodeNear.getG()) {
                                nodeNear.setParent(node);
                                nodeNear.setG(cost);
                                nodeNear.setF(nodeNear.getG() + nodeNear.getH());
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected ArrayList<Node> FloydSmooth(ArrayList<Node> array) {
        return super.FloydSmooth(array);
    }
}
