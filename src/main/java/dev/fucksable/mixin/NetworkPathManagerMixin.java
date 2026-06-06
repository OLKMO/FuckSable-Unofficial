package dev.fucksable.mixin;

import dev.fucksable.fix.FixRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Optimization: Replaces ArrayList with HashSet and LinkedList with ArrayDeque
 * in CNA's findConductiblePath BFS algorithm.
 * <p>
 * The original implementation uses ArrayList.contains() for visited checks (O(n))
 * and LinkedList as BFS queue (high memory overhead). This optimization uses:
 * - HashSet for O(1) visited lookups
 * - ArrayDeque for better cache locality and lower memory overhead
 * <p>
 * Uses @Inject at HEAD to intercept and replace the entire method when optimization
 * is enabled. Falls through to original if disabled or on error.
 * Uses @Pseudo so the mixin is silently skipped if CNA is not installed.
 */
@Pseudo
@Mixin(targets = "org.antarcticgardens.cna.content.electricity.network.NetworkPathManager", remap = false)
public class NetworkPathManagerMixin {

    @Shadow(remap = false)
    private Object context;

    /**
     * Intercept findConductiblePath at HEAD and replace with optimized BFS.
     * Method signature in target: protected NetworkPath findConductiblePath(AbstractElectricalConnector a, AbstractElectricalConnector b)
     * We use Object for all CNA-specific types since they're not on compile classpath.
     */
    @Inject(method = "findConductiblePath", at = @At("HEAD"), cancellable = true, remap = false)
    private void fucksable$optimizedFindPath(Object a, Object b, CallbackInfoReturnable<Object> cir) {
        if (!FixRegistry.isEnabled("cna-path-optimization")) return;

        try {
            Object result = fucksable$doOptimizedFindPath(a, b);
            cir.setReturnValue(result);
        } catch (Throwable t) {
            // On any error, fall through to original method
        }
    }

    private Object fucksable$doOptimizedFindPath(Object a, Object b) throws Exception {
        Class<?> connectorClass = Class.forName("org.antarcticgardens.cna.content.electricity.connector.AbstractElectricalConnector");
        Class<?> pathClass = Class.forName("org.antarcticgardens.cna.content.electricity.network.NetworkPath");
        Class<?> pairClass = Class.forName("org.antarcticgardens.cna.util.HashSortedPair");
        Class<?> contextClass = Class.forName("org.antarcticgardens.cna.content.electricity.network.NetworkPathConductivityContext");
        Class<?> configClass = Class.forName("org.antarcticgardens.cna.config.CNAConfig");

        Method getConnectedConnectors = connectorClass.getMethod("getConnectedConnectors");
        Method calculatePathConductivity = contextClass.getMethod("calculatePathConductivity", pathClass);
        Method getConnectionConductivity = contextClass.getMethod("getConnectionConductivity", pairClass);
        Method addNodeToBeginning = pathClass.getMethod("addNodeToBeginning", connectorClass);
        Method getLength = pathClass.getMethod("getLength");
        Method getFirstNode = pathClass.getMethod("getFirstNode");
        Constructor<?> pathCtor = pathClass.getDeclaredConstructor();
        pathCtor.setAccessible(true);
        Constructor<?> pairCtor = pairClass.getDeclaredConstructor(connectorClass, connectorClass);
        pairCtor.setAccessible(true);

        Method getServer = configClass.getMethod("getServer");
        Object serverConfig = getServer.invoke(null);
        Field maxDepthField = serverConfig.getClass().getField("maxPathfindingDepth");
        Object maxDepthConfigValue = maxDepthField.get(serverConfig);
        Method getMaxDepth = maxDepthConfigValue.getClass().getMethod("get");
        int maxDepth = (Integer) getMaxDepth.invoke(maxDepthConfigValue);

        // Optimized BFS: HashSet for O(1) visited, ArrayDeque for queue
        Set<Object> visited = new HashSet<>();
        Deque<Object[]> queue = new ArrayDeque<>();
        queue.add(new Object[]{a, null, 0});
        visited.add(a);

        while (!queue.isEmpty()) {
            Object[] element = queue.poll();
            Object connector = element[0];
            int depth = (int) element[2];

            if (connector.equals(b)) {
                Object path = fucksable$unwrapPath(element, pathCtor, addNodeToBeginning, getLength, getFirstNode, getConnectionConductivity, pairCtor);
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

    private Object fucksable$unwrapPath(Object[] element,
                                         Constructor<?> pathCtor,
                                         Method addNodeToBeginning,
                                         Method getLength,
                                         Method getFirstNode,
                                         Method getConnectionConductivity,
                                         Constructor<?> pairCtor) throws Exception {
        Object path = pathCtor.newInstance();

        Object[] current = element;
        while (current != null) {
            Object connector = current[0];

            if ((int) getLength.invoke(path) != 0) {
                Object firstNode = getFirstNode.invoke(path);
                Object pair = pairCtor.newInstance(connector, firstNode);
                if ((long) getConnectionConductivity.invoke(context, pair) <= 0) {
                    return null;
                }
            }

            addNodeToBeginning.invoke(path, connector);
            current = (Object[]) current[1];
        }

        if ((int) getLength.invoke(path) < 2) {
            return null;
        }

        return path;
    }
}
