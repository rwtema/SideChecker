package sidechecker;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.LoaderException;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SideCheckerTransformer implements IClassTransformer {
    private static final String propCrashWarning = "SideChecker.crashOnWarning";
    private static final String propCrashError = "SideChecker.crashOnError";
    private static final String propClientSafeAnnotation = "SideChecker.clientSafeAnnotation";
    private static final String propFilter = "SideChecker.filter";

    public static Logger logger = LogManager.getLogger("SideOnlyChecker");

    public static LaunchClassLoader classLoader = (LaunchClassLoader) SideCheckerTransformer.class.getClassLoader();

    private static String clientSafeName = null;
    private static boolean crashOnSeriousError = false;
    private static boolean crashOnWarning = false;

    private static String curClass, curMethod, curFile;
    private static final List<String> warnings = new ArrayList<String>();
    private static final List<String> errors = new ArrayList<String>();

    public static String filter = null;

    public static void init() {
        if (!isDevEnviroment())
            throw new RuntimeException("Can't use SideChecker in a non-deobfuscated enviroment.");

        logger.info("Starting SideChecker Routine");

        filter = System.getProperty(propFilter);
        crashOnWarning = System.getProperty(propCrashWarning) != null;
        crashOnSeriousError = crashOnWarning || (System.getProperty(propCrashError) != null);

        String altName = System.getProperty(propClientSafeAnnotation);
        if (altName != null) {
            clientSafeName = 'L' + altName.replace('.', '/') + ';';
        }

        files = new ArrayList<File>();
        List<URL> urls = classLoader.getSources();
        File[] sources = new File[urls.size()];
        try {
            for (int i = 0; i < urls.size(); i++) {
                sources[i] = new File(urls.get(i).toURI());
                if (sources[i].isDirectory()) files.add(sources[i]);
            }

        } catch (URISyntaxException e) {
            FMLLog.log(Level.ERROR, e, "Unable to process our input to locate the minecraft code");
            throw new LoaderException(e);
        }

        ClassInfo.init();
        needsInit = false;
    }

    public static List<File> files;


    public static boolean needsInit = true;

    public static String[] exceptions = new String[]{
            "cpw.mods.fml.",
            "netty.",
            "net.minecraft.",
            "net.minecraftforge."
    };

    @Override
    public byte[] transform(String s, String s2, byte[] bytes) {
        if (needsInit) {
            init();
        }

        if (s == null || bytes == null) //should never happen
            return null;

        for (String exception : exceptions) {
            if (s.startsWith(exception))
                return bytes;
        }

        if (filter != null) {
            if (!s.startsWith(filter)) // basic hack to allow users to filter out other mods
                return bytes;
        } else {
            boolean flag = false;   // is the class from a 'directory' class
            final String replace = s.replace('.', '\\') + ".class";
            for (File f : files) {
                File t = new File(f, replace);
                if (t.exists()) {
                    flag = true;
                    break;
                }
            }

            if (!flag)
                return bytes;
        }


        ClassNode classNode = new ClassNode();
        ClassReader reader = new ClassReader(bytes);

        reader.accept(classNode, 0);

        curClass = s;
        curFile = classNode.sourceFile;
        curMethod = "";

        ClassInfo.registerClass(s, bytes);

        if (hasClientAnnotation(classNode.visibleAnnotations))
            return bytes;  // class is client-side and has a license to be so

        if (classNode.superName != null && ClassInfo.isClientClass(classNode.superName)) {
            logger.info("----------------------------------------------------------------");
            logger.info("Error: Class " + s + " extends client-side class " + classNode.superName + " but does not include annotation");
            logger.info(log("Class: " + s, "class", 1));
            logger.info("----------------------------------------------------------------");
            if (crashOnSeriousError)
                throwException();

            return bytes;
        }

        for (String interfaces : classNode.interfaces) {
            if (ClassInfo.isClientClass(interfaces)) {
                logger.info("----------------------------------------------------------------");
                logger.info("Error: Class " + s + " extends client-side class " + classNode.superName + " but does not include annotation");
                logger.info(log("Class: " + s, "class", 1));
                logger.info("----------------------------------------------------------------");
                if (crashOnSeriousError)
                    throwException();

                return bytes;
            }
        }


        for (MethodNode method : classNode.methods) {
            curMethod = method.name;
            if (shouldProcess(method.visibleAnnotations)) {
                if (ClassInfo.hasClientMethod(classNode.superName, method.name, method.desc)) {
                    int line = -1;
                    for (AbstractInsnNode instruction : method.instructions.toArray()) {
                        if (instruction.getType() == AbstractInsnNode.LINE) {
                            line = ((LineNumberNode) instruction).line;
                            break;
                        }
                    }
                    warnings.add(log("Method: ", method.name, line));
                }

                method.accept(ClientCheckerMethodVisitor.instance);
            }
        }

        for (FieldNode fieldNode : classNode.fields) {
            if (!hasClientAnnotation(fieldNode.visibleAnnotations)) {
                if (fieldNode.desc.startsWith("L"))
                    if (ClassInfo.isClientClass(stripToType(fieldNode.desc))) {
                        errors.add(log("Field Type: ", fieldNode.desc, 1));
                    }
            }
        }

        boolean crash = false;

        if (!warnings.isEmpty() || !errors.isEmpty()) {
            logger.info("----------------------------------------------------------------");
            if (!warnings.isEmpty()) {
                logger.info("Warning: Class " + s + " overrides client-side methods and does not include the SideOnly annotation");
                for (String method : warnings) {
                    logger.info(method);
                }

                if (crashOnWarning)
                    crash = true;
                warnings.clear();
            }

            if (!errors.isEmpty()) {
                logger.info("Error: Class " + s + " has references to client-side code in non-client-side fields/methods");

                for (String problem : errors) {
                    logger.info(problem);
                }
                errors.clear();

                if (crashOnSeriousError)
                    crash = true;
            }
            logger.info("----------------------------------------------------------------");
        }

        if (crash)
            throwException();

        return bytes;
    }

    private static void throwException() {
        //System.exit(-1);
        logger.info("Serious errors were found. The system will now exit");
        FMLCommonHandler.instance().exitJava(-1, true);
    }

    public static String log(String info, String method, int line) {
        return "\t" + info + "\tat " + (new StackTraceElement(curClass, method, curFile, line)).toString();
    }

    public static boolean shouldProcess(List<AnnotationNode> anns) {
        if (hasClientSafeAnnotation(anns)) {
            if ("net.minecraft.block.Block".equals(curClass) && "getRenderType".equals(curMethod)) {
                errors.add("\tIn the interest of sanity, @ClientSafe is disabled for getRenderType().");
                errors.add("\tgetRenderType() is a server-side method used by the server to get details about block structure.");
                errors.add("\tUse your SidedProxies to ensure this works properly.");
                return true;
            }

            return false;
        }

        return !hasClientAnnotation(anns);
    }

    private static boolean hasClientSafeAnnotation(List<AnnotationNode> anns) {
        if (anns == null || clientSafeName == null)
            return false;

        for (AnnotationNode ann : anns) {
            if (ann.desc.equals(clientSafeName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasClientAnnotation(List<AnnotationNode> anns) {
        if (anns == null)
            return false;
        for (AnnotationNode ann : anns) {
            if (ann.desc.equals(Type.getDescriptor(SideOnly.class)) && ann.values != null) {
                for (int x = 0; x < ann.values.size() - 1; x += 2) {
                    Object key = ann.values.get(x);
                    Object value = ann.values.get(x + 1);
                    if (key instanceof String && key.equals("value")) {
                        if (value instanceof String[]) {
                            if (((String[]) value)[1].equals(Side.CLIENT.name())) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    private static boolean isDevEnviroment() {
        return (Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment");
    }

    public static class ClientCheckerMethodVisitor extends MethodVisitor {
        public static ClientCheckerMethodVisitor instance = new ClientCheckerMethodVisitor();

        public ClientCheckerMethodVisitor() {
            super(Opcodes.ASM4);
        }

        int line;

        @Override
        public void visitCode() {
            line = -1;
        }

        @Override
        public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String desc, boolean visible) {
            return super.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, desc, visible);
        }

        @Override
        public void visitLineNumber(int line, Label start) {
            this.line = line;
        }

        @Override
        public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
            if (name == null || "this".equals(name) || desc == null || !desc.startsWith("L")) {
                return;
            }

            String type = stripToType(desc);

            if (ClassInfo.isClientClass(type)) {
                errors.add(log("Local Variable: " + name, curMethod, line));
            }
        }


        @Override
        @SuppressWarnings("deprecation")
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            visitMethodInsn(opcode, owner, name, desc, opcode == Opcodes.INVOKEINTERFACE);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean s) {
            if (ClassInfo.hasClientMethod(owner, name, desc)) {
                errors.add(log("Method Reference: " + name, curMethod, line));
            }
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            if (ClassInfo.hasClientField(owner, name))
                errors.add(log("Field Reference: " + name, curMethod, line));
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (ClassInfo.isClientClass(Type.getType(type).getInternalName())) {
                errors.add(log("Type Reference: " + type, curMethod, line));
            }
        }
    }

    private static String stripToType(String desc) {
        String type;
        if (desc.endsWith(";"))
            type = desc.substring(1, desc.length() - 1);
        else
            type = desc.substring(1);
        return type;
    }


    // Caches info regarding class's client-side methods/fields
    public static class ClassInfo {
        public static HashMap<String, ClassInfo> cached = new HashMap<String, ClassInfo>();

        public static ClassInfo blankClass = new ClassInfo();

        public static boolean hasClientMethod(String owner, String name, String desc) {
            final ClassInfo classInfo = getClassInfo(owner);
            return classInfo.hasClientMethod(name, desc);
        }

        public static void init() {
            cached.put("java/lang/Object", new ClassInfo());
        }

        public static boolean isClientClass(String clazz) {
            return getClassInfo(clazz).isClient;
        }

        public static boolean hasClientField(String owner, String name) {
            return getClassInfo(owner).isClientField(name);
        }

        private boolean hasClientMethod(String name, String desc) {
            return isClient || getSafe(methods, name);
        }

        private boolean getSafe(HashMap<String, Boolean> list, String key) {
            Boolean result = list.get(key);
            return result == null ? false : result;
        }

        private boolean isClientField(String name) {
            return isClient || getSafe(fields, name);
        }

        public static ClassInfo getClassInfo(String className) {
            if (className.startsWith("java/") || className.startsWith("io/"))
                return blankClass;

            if (cached.containsKey(className))
                return cached.get(className);

            ClassInfo value = new ClassInfo(className);
            cached.put(className, value);
            return value;
        }


        HashMap<String, Boolean> methods = new HashMap<String, Boolean>();
        HashMap<String, Boolean> fields = new HashMap<String, Boolean>();

        boolean isClient = false;


        private ClassInfo(String className) {
            this(getBytes(className));
        }

        private ClassInfo() {
            // Blank ClassInfo
        }

        private static byte[] getBytes(String className) {
            byte[] bytes;
            try {
                String properName = className.replace('/', '.');
                bytes = classLoader.getClassBytes(properName);
            } catch (IOException e) {
                return null;    // Can't find class so I guess no client-side code
            }

            return bytes;
        }

        private ClassInfo(byte[] bytes) {
            if (bytes == null)
                return;

            ClassNode classNode = new ClassNode();
            ClassReader classReader = new ClassReader(bytes);
            classReader.accept(classNode, 0);

            isClient = hasClientAnnotation(classNode.visibleAnnotations);

            if (isClient)
                return;

            for (MethodNode method : classNode.methods) {
                methods.put(method.name, hasClientAnnotation(method.visibleAnnotations));
            }

            for (FieldNode field : classNode.fields) {
                fields.put(field.name, hasClientAnnotation(field.visibleAnnotations));
            }

            if (classNode.superName != null)
                if (!classNode.superName.startsWith("java/"))
                    join(getClassInfo(classNode.superName));

            for (String iface : classNode.interfaces)
                join(getClassInfo(iface));
        }

        public void join(ClassInfo info) {
            if (isClient || info.isClient) {
                isClient = true;
                return;
            }


            merge(fields, info.fields);
            merge(methods, info.methods);

        }

        @SuppressWarnings("unchecked")
        public void merge(Map a, Map b) {
            for (Object key : b.keySet())
                if (!a.containsKey(key))
                    a.put(key, b.get(key));
        }

        public static void registerClass(String s, byte[] bytes) {
            s = s.replace('.', '/');
            cached.put(s, new ClassInfo(bytes));
        }
    }
}
