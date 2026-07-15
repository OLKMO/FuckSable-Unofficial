package dev.fucksable;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

/**
 * Mixin 配置插件，根据运行时 Sable 版本选择对应版本的 constraint self-fix mixin。
 * <p>
 * Sable 1.x 中 RapierPhysicsPipeline.addConstraint 参数为 ServerSubLevel；
 * Sable 2.0.3+ 改为 PhysicsPipelineBody。本插件按版本启用 V1 或 V2 mixin，
 * 避免 Mixin 描述符不匹配导致 apply 阶段崩溃。
 * <p>
 * 使用纯反射访问 NeoForge 加载期 API，避免编译期对具体 forgespi 包的依赖。
 */
public class FuckSableMixinConfigPlugin implements IMixinConfigPlugin {

    // 0 = 未知, 1 = <2.0.0 (V1/ServerSubLevel), 2 = >=2.0.0 (V2/PhysicsPipelineBody)
    private static int sableVersionState = 0;
    private static boolean versionChecked = false;

    private static int getSableVersionState() {
        if (versionChecked) return sableVersionState;
        versionChecked = true;
        try {
            Class<?> fmlLoaderClass = Class.forName("net.neoforged.fml.loading.FMLLoader");
            Method getLoadingModList = fmlLoaderClass.getMethod("getLoadingModList");
            Object modList = getLoadingModList.invoke(null);

            // ModList.getMods() 返回 List<IModInfo>，直接遍历找 sable
            Method getMods = modList.getClass().getMethod("getMods");
            @SuppressWarnings("unchecked")
            List<Object> modInfos = (List<Object>) getMods.invoke(modList);

            for (Object info : modInfos) {
                Method getModId = info.getClass().getMethod("getModId");
                String modId = (String) getModId.invoke(info);
                if ("sable".equals(modId)) {
                    Method getVersion = info.getClass().getMethod("getVersion");
                    Object version = getVersion.invoke(info);

                    Class<?> artifactVersionClass = Class.forName("org.apache.maven.artifact.versioning.ArtifactVersion");
                    Class<?> defaultArtifactVersionClass = Class.forName("org.apache.maven.artifact.versioning.DefaultArtifactVersion");
                    Object threshold = defaultArtifactVersionClass.getConstructor(String.class).newInstance("2.0.0");
                    Method compareTo = artifactVersionClass.getMethod("compareTo", artifactVersionClass);
                    int result = (int) compareTo.invoke(version, threshold);
                    boolean v2 = result >= 0;
                    sableVersionState = v2 ? 2 : 1;
                    FuckSable.LOGGER.info("Detected Sable version {}, enabling {} constraint self-fix mixin",
                        version, v2 ? "V2 (PhysicsPipelineBody)" : "V1 (ServerSubLevel)");
                    return sableVersionState;
                }
            }
        } catch (Throwable t) {
            FuckSable.LOGGER.warn("FML API version detection failed, falling back to class signature detection", t);
        }

        // Fallback: 直接检测 RapierPhysicsPipeline.addConstraint 方法签名
        sableVersionState = detectByClassSignature();
        return sableVersionState;
    }

    /**
     * Fallback：直接检测 RapierPhysicsPipeline.addConstraint 的参数类型。
     * V1 (1.x) 参数为 ServerSubLevel，V2 (2.x) 参数为 PhysicsPipelineBody。
     */
    private static int detectByClassSignature() {
        try {
            Class<?> pipelineClass = Class.forName("dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline");
            for (Method m : pipelineClass.getDeclaredMethods()) {
                if ("addConstraint".equals(m.getName()) && m.getParameterCount() >= 2) {
                    Class<?> firstParam = m.getParameterTypes()[0];
                    String paramTypeName = firstParam.getName();
                    if (paramTypeName.contains("PhysicsPipelineBody")) {
                        sableVersionState = 2;
                        FuckSable.LOGGER.info("Detected Sable >=2.0.0 by class signature (addConstraint uses PhysicsPipelineBody), enabling V2 mixin");
                        return sableVersionState;
                    } else {
                        sableVersionState = 1;
                        FuckSable.LOGGER.info("Detected Sable <2.0.0 by class signature (addConstraint uses {}), enabling V1 mixin", paramTypeName);
                        return sableVersionState;
                    }
                }
            }
        } catch (Throwable t) {
            FuckSable.LOGGER.error("Class signature detection also failed, disabling both constraint self-fix mixins to avoid crash", t);
        }
        sableVersionState = 0;
        return sableVersionState;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("RapierConstraintSelfFixMixinV1")) {
            int state = getSableVersionState();
            if (state == 0) return false;
            return state == 1;
        }
        if (mixinClassName.endsWith("RapierConstraintSelfFixMixinV2")) {
            int state = getSableVersionState();
            if (state == 0) return false;
            return state == 2;
        }
        return true;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
