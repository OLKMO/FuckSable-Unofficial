package dev.fucksable.mixin;

import dev.fucksable.fix.FixRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * 优化 CNA 的 NetworkPathManager.findConductiblePath 性能。
 * <p>
 * 原始实现的问题：
 * 1. visited 使用 ArrayList.contains()，时间复杂度 O(n)
 * 2. 使用 LinkedList 作为 BFS 队列，内存开销大
 * <p>
 * 优化方式：
 * 1. 使用 HashSet 替代 ArrayList 作为 visited 集合（O(1) 查找）
 * 2. 使用 ArrayDeque 替代 LinkedList 作为 BFS 队列
 * 3. 通过反射访问 CNA 类（因为 CNA 不在编译 classpath 中）
 * <p>
 * 使用 @Pseudo 注解允许在 CNA 不存在时也不报错。
 */
@Pseudo
@Mixin(targets = "org.antarcticgardens.cna.content.electricity.network.NetworkPathManager", remap = false)
public abstract class NetworkPathManagerMixin {

    // 缓存反射结果
    private static boolean reflectionInitialized = false;
    private static Method getConnectedConnectors;
    private static Method calculatePathConductivity;
    private static Method getConnectionConductivity;
    private static Method addNodeToBeginning;
    private static Method getLength;
    private static Method getFirstNode;
    private static Constructor<?> pathConstructor;
    private static Constructor<?> pairConstructor;
    private static Field contextField;
    private static Object maxDepthConfigValue;
    private static Method getMaxDepth;

    /**
     * @author FuckSable
     * @reason 优化寻路性能：HashSet 替代 ArrayList，ArrayDeque 替代 LinkedList
     */
    @Overwrite(remap = false)
    protected Object findConductiblePath(Object a, Object b) {
        if (!FixRegistry.isEnabled("cna-path-optimization")) {
            return fucksable$originalFindPath(a, b);
        }

        try {
            initReflection();
            return fucksable$optimizedFindPath(a, b);
        } catch (Throwable t) {
            return fucksable$originalFindPath(a, b);
        }
    }

    private static synchronized void initReflection() throws Exception {
        if (reflectionInitialized) return;

        Class<?> connectorClass = Class.forName("org.antarcticgardens.cna.content.electricity.connector.AbstractElectricalConnector");
        Class<?> contextClass = Class.forName("org.antarcticgardens.cna.content.electricity.network.NetworkPathConductivityContext");
        Class<?> pathClass = Class.forName("org.antarcticgardens.cna.content.electricity.network.NetworkPath");
        Class<?> pairClass = Class.forName("org.antarcticgardens.cna.util.HashSortedPair");
        Class<?> managerClass = Class.forName("org.antarcticgardens.cna.content.electricity.network.NetworkPathManager");
        Class<?> configClass = Class.forName("org.antarcticgardens.cna.config.CNAConfig");

        getConnectedConnectors = connectorClass.getMethod("getConnectedConnectors");
        calculatePathConductivity = contextClass.getMethod("calculatePathConductivity", pathClass);
        getConnectionConductivity = contextClass.getMethod("getConnectionConductivity", pairClass);
        addNodeToBeginning = pathClass.getMethod("addNodeToBeginning", connectorClass);
        getLength = pathClass.getMethod("getLength");
        getFirstNode = pathClass.getMethod("getFirstNode");
        pathConstructor = pathClass.getDeclaredConstructor();
        pathConstructor.setAccessible(true);
        pairConstructor = pairClass.getDeclaredConstructor(connectorClass, connectorClass);
        pairConstructor.setAccessible(true);

        contextField = managerClass.getDeclaredField("context");
        contextField.setAccessible(true);

        // 缓存 maxPathfindingDepth 的 ConfigValue 对象
        Method getServer = configClass.getMethod("getServer");
        Object serverConfig = getServer.invoke(null);
        Field maxDepthField = serverConfig.getClass().getField("maxPathfindingDepth");
        maxDepthConfigValue = maxDepthField.get(serverConfig);
        getMaxDepth = maxDepthConfigValue.getClass().getMethod("get");

        reflectionInitialized = true;
    }

    private Object fucksable$optimizedFindPath(Object a, Object b) throws Exception {
        Object self = (Object) this;
        Object context = contextField.get(self);
        int maxDepth = (Integer) getMaxDepth.invoke(maxDepthConfigValue);

        // 优化版 BFS：HashSet + ArrayDeque
        Set<Object> visited = new HashSet<>();
        // 使用 Object[] 代替 QueueElement record: [connector, parentElement, depth]
        Deque<Object[]> queue = new ArrayDeque<>();
        queue.add(new Object[]{a, null, 0});
        visited.add(a);

        while (!queue.isEmpty()) {
            Object[] element = queue.poll();
            Object connector = element[0];
            int depth = (int) element[2];

            if (connector.equals(b)) {
                Object path = unwrapConductiblePath(element, context);
                if (path != null && (long) calculatePathConductivity.invoke(context, path) > 0) {
                    return path;
                }
            }

            if (depth < maxDepth) {
                Object connectedMap = getConnectedConnectors.invoke(connector);
                for (Object nextConnector : (Iterable<?>) connectedMap.getClass().getMethod("keySet").invoke(connectedMap)) {
                    if (visited.add(nextConnector)) {
                        queue.add(new Object[]{nextConnector, element, depth + 1});
                    }
                }
            }
        }

        return null;
    }

    /**
     * 等价于原始的 unwrapConductiblePath 方法。
     * 从终点回溯到起点，边回溯边构建路径并检查导电性。
     */
    private Object unwrapConductiblePath(Object[] element, Object context) throws Exception {
        Object path = pathConstructor.newInstance();

        Object[] current = element;
        while (current != null) {
            Object connector = current[0];

            // 如果路径非空，检查当前节点与路径第一个节点之间的导电性
            if ((int) getLength.invoke(path) != 0) {
                Object firstNode = getFirstNode.invoke(path);
                Object pair = pairConstructor.newInstance(connector, firstNode);
                if ((long) getConnectionConductivity.invoke(context, pair) <= 0) {
                    return null;
                }
            }

            addNodeToBeginning.invoke(path, connector);
            current = (Object[]) current[1]; // parent
        }

        if ((int) getLength.invoke(path) < 2) {
            return null;
        }

        return path;
    }

    /**
     * 原始实现作为回退（使用 ArrayList + LinkedList + 反射）
     */
    private Object fucksable$originalFindPath(Object a, Object b) {
        try {
            if (!reflectionInitialized) initReflection();

            Object self = (Object) this;
            Object context = contextField.get(self);
            int maxDepth = (Integer) getMaxDepth.invoke(maxDepthConfigValue);

            List<Object> visited = new ArrayList<>();
            Queue<Object[]> queue = new LinkedList<>();
            queue.add(new Object[]{a, null, 0});
            visited.add(a);

            while (!queue.isEmpty()) {
                Object[] element = queue.poll();
                Object connector = element[0];
                int depth = (int) element[2];

                if (connector.equals(b)) {
                    Object path = unwrapConductiblePath(element, context);
                    if (path != null && (long) calculatePathConductivity.invoke(context, path) > 0) {
                        return path;
                    }
                }

                if (depth < maxDepth) {
                    Object connectedMap = getConnectedConnectors.invoke(connector);
                    for (Object nextConnector : (Iterable<?>) connectedMap.getClass().getMethod("keySet").invoke(connectedMap)) {
                        if (!visited.contains(nextConnector)) {
                            visited.add(nextConnector);
                            queue.add(new Object[]{nextConnector, element, depth + 1});
                        }
                    }
                }
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
