package dev.fucksable;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.InputStream;
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

                    Class<?> defaultArtifactVersionClass = Class.forName("org.apache.maven.artifact.versioning.DefaultArtifactVersion");
                    Object threshold = defaultArtifactVersionClass.getConstructor(String.class).newInstance("2.0.0");
                    // 不能用 getMethod("compareTo", artifactVersionClass) 查找：
                    // 在 Mohist/Youer 等服务端上，ArtifactVersion.compareTo 是 Comparable<T> 的桥接方法，
                    // 编译后参数类型为 Object，精确签名查找会抛 NoSuchMethodException。
                    // 直接通过 Comparable 接口调用，由 JVM 多态分派到具体实现。
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    int result = ((Comparable) version).compareTo(threshold);
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
     * Fallback：通过读取 class 字节码检测 RapierPhysicsPipeline.addConstraint 的参数类型。
     * <p>
     * V1 (1.x) 第一个参数为 ServerSubLevel，V2 (2.x) 第一个参数为 PhysicsPipelineBody。
     * <p>
     * 注意：不能用 Class.forName 加载 RapierPhysicsPipeline，因为此方法在 mixin prepare 阶段被调用
     * （通过 shouldApplyMixin），此时加载一个被 mixin 处理的类会触发 ReEntrantTransformerError。
     * 改用 ClassLoader.getResourceAsStream + ASM 读取方法描述符，避免触发类加载。
     */
    private static int detectByClassSignature() {
        String className = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline";
        String classResource = className.replace('.', '/') + ".class";
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (loader == null) loader = FuckSableMixinConfigPlugin.class.getClassLoader();
            try (InputStream in = loader.getResourceAsStream(classResource)) {
                if (in == null) {
                    FuckSable.LOGGER.warn("Sable class resource not found: {}, disabling both constraint self-fix mixins", classResource);
                    sableVersionState = 0;
                    return sableVersionState;
                }
                byte[] bytes = in.readAllBytes();
                ClassReader reader = new ClassReader(bytes);
                final int[] detected = {0};
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        if (detected[0] != 0) return null;
                        if (!"addConstraint".equals(name) || descriptor == null || descriptor.isEmpty()) return null;
                        // descriptor 格式: (Lxxx/yyy;Lxxx/yyy;...)Lreturn;
                        // 取第一个参数类型
                        if (descriptor.length() < 3 || descriptor.charAt(1) != 'L') return null;
                        int start = 2;
                        int end = descriptor.indexOf(';', start);
                        if (end < 0) return null;
                        String firstParam = descriptor.substring(start, end);
                        if (firstParam.contains("PhysicsPipelineBody")) {
                            detected[0] = 2;
                        } else {
                            detected[0] = 1;
                        }
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                sableVersionState = detected[0];
                if (sableVersionState == 2) {
                    FuckSable.LOGGER.info("Detected Sable >=2.0.0 by class signature (addConstraint uses PhysicsPipelineBody), enabling V2 mixin");
                } else if (sableVersionState == 1) {
                    FuckSable.LOGGER.info("Detected Sable <2.0.0 by class signature (addConstraint first param is not PhysicsPipelineBody), enabling V1 mixin");
                } else {
                    FuckSable.LOGGER.warn("addConstraint method not found in {}, disabling both constraint self-fix mixins", className);
                }
                return sableVersionState;
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
