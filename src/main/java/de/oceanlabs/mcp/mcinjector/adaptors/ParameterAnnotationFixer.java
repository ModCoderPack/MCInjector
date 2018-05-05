package de.oceanlabs.mcp.mcinjector.adaptors;

import static org.objectweb.asm.Opcodes.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;

import de.oceanlabs.mcp.mcinjector.MCInjectorImpl;

public class ParameterAnnotationFixer extends ClassVisitor
{
    private static final Logger LOGGER = Logger.getLogger("MCInjector");

    public ParameterAnnotationFixer(ClassVisitor cn, MCInjectorImpl mci)
    {
        super(Opcodes.ASM5, cn);
        // Extra version check, since marking as synthetic does not work in ASM 6.1
        // (a different technique must be used then)
        boolean asm61;
        try
        {
            MethodNode.class.getField("visibleAnnotableParameterCount");
            MethodNode.class.getField("invisibleAnnotableParameterCount");
            asm61 = true;
        }
        catch (Exception ex)
        {
            asm61 = false;
        }
        if (asm61)
        {
            throw new UnsupportedOperationException(
                    "Parameter annotation fixer only works with ASM 6.0 and below, not ASM 6.1");
        }
    }

    @Override
    public void visitEnd()
    {
        super.visitEnd();

        ClassNode cls = MCInjectorImpl.getClassNode(cv);
        Type[] syntheticParams = getExpectedSyntheticParams(cls);
        if (syntheticParams != null)
        {
            for (MethodNode mn : cls.methods)
            {
                if (mn.name.equals("<init>"))
                {
                    processConstructor(cls, mn, syntheticParams);
                }
            }
        }
    }

    /**
     * Checks if the given class might have synthetic parameters in the
     * constructor. There are two cases where this might happen:
     * <ol>
     * <li>If the given class is an inner class, the first parameter is the
     * instance of the outer class.</li>
     * <li>If the given class is an enum, the first parameter is the enum
     * constant name and the second parameter is its ordinal.</li>
     * </ol>
     *
     * @return An array of types for synthetic parameters if the class can have
     *         synthetic parameters, otherwise null.
     */
    private Type[] getExpectedSyntheticParams(ClassNode cls)
    {
        // Check for enum
        // http://hg.openjdk.java.net/jdk8/jdk8/langtools/file/1ff9d5118aae/src/share/classes/com/sun/tools/javac/comp/Lower.java#l2866
        if ((cls.access & ACC_ENUM) != 0)
        {
            LOGGER.fine("  Considering " + cls.name
                    + " for extra parameter annotations as it is an enum");
            return new Type[] { Type.getObjectType("java/lang/String"), Type.INT_TYPE };
        }

        // Check for inner class
        InnerClassNode info = null;
        for (InnerClassNode node : cls.innerClasses) // note: cls.innerClasses is never null
        {
            if (node.name.equals(cls.name))
            {
                info = node;
                break;
            }
        }
        // http://hg.openjdk.java.net/jdk8/jdk8/langtools/file/1ff9d5118aae/src/share/classes/com/sun/tools/javac/code/Symbol.java#l398
        if (info == null)
        {
            LOGGER.fine("  Not considering " + cls.name
                    + " for extra parameter annotations as it is not an inner class");
            return null; // It's not an inner class
        }
        if ((info.access & (ACC_STATIC | ACC_INTERFACE)) != 0)
        {
            LOGGER.fine("  Not considering " + cls.name
                    + " for extra parameter annotations as is an interface or static");
            return null; // It's static or can't have a constructor
        }

        // http://hg.openjdk.java.net/jdk8/jdk8/langtools/file/1ff9d5118aae/src/share/classes/com/sun/tools/javac/jvm/ClassReader.java#l2011
        if (info.innerName == null)
        {
            LOGGER.fine("  Not considering " + cls.name
                    + " for extra parameter annotations as it is annonymous");
            return null; // It's an anonymous class
        }

        LOGGER.fine("  Considering " + cls.name
                + " for extra parameter annotations as it is an inner class of "
                + info.outerName);

        return new Type[] { Type.getObjectType(info.outerName) };
    }

    /**
     * Removes the parameter annotations for the given synthetic parameters,
     * if there are parameter annotations and the synthetic parameters exist.
     */
    private void processConstructor(ClassNode cls, MethodNode mn, Type[] syntheticParams) {
        String methodInfo = mn.name + mn.desc + " in " + cls.name;
        Type[] params = Type.getArgumentTypes(mn.desc);

        if (beginsWith(params, syntheticParams))
        {
            mn.visibleParameterAnnotations = process(methodInfo,
                    "RuntimeVisibleParameterAnnotations", params.length,
                    syntheticParams.length, mn.visibleParameterAnnotations);
            mn.invisibleParameterAnnotations = process(methodInfo,
                    "RuntimeInvisibleParameterAnnotations", params.length,
                    syntheticParams.length, mn.invisibleParameterAnnotations);
        }
        else
        {
            LOGGER.warning("Unexpected lack of synthetic args to the constructor: expected "
                            + Arrays.toString(syntheticParams) + " at the start of " + methodInfo);
        }
    }

    private boolean beginsWith(Type[] values, Type[] prefix)
    {
        if (values.length < prefix.length)
        {
            return false;
        }
        for (int i = 0; i < prefix.length; i++)
        {
            if (!values[i].equals(prefix[i]))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * An annotation that marks the given parameter annotation for removal.
     * ASM 6.0 and below internally remove these in MethodWriter.visitParameterAnnotation
     * so manually adding them allows us to mark annotations entries for removal.
     */
    private static final List<AnnotationNode> SYNTHETIC_NODE = new ArrayList<AnnotationNode>();
    {
        SYNTHETIC_NODE.add(new AnnotationNode("Ljava/lang/Synthetic;"));
    }

    /**
     * Removes annotation nodes corresponding to synthetic parameters, after
     * the existence of synthetic parameters has already been checked.
     *
     * @param methodInfo
     *            A description of the method, for logging
     * @param attributeName
     *            The name of the attribute, for logging
     * @param numParams
     *            The number of parameters in the method
     * @param numSynthetic
     *            The number of synthetic parameters (should not be 0)
     * @param annotations
     *            The current array of annotation nodes, may be null
     * @return The new array of annotation nodes, may be null
     */
    private List<AnnotationNode>[] process(String methodInfo,
            String attributeName, int numParams, int numSynthetic,
            List<AnnotationNode>[] annotations)
    {
        if (annotations == null)
        {
            LOGGER.finer("    " + methodInfo + " does not have a "
                    + attributeName + " attribute");
            return null;
        }

        int numAnnotations = annotations.length;
        if (numParams == numAnnotations)
        {
            LOGGER.info("Found extra " + attributeName + " entries in "
                    + methodInfo + ": marking " + numSynthetic + " as synthetic");
            Arrays.fill(annotations, 0, numSynthetic, SYNTHETIC_NODE);
            return annotations;
        }
        else if (numParams == numAnnotations - numSynthetic)
        {
            LOGGER.info("Number of " + attributeName + " entries in "
                    + methodInfo + " is already as we want");
            return annotations;
        }
        else
        {
            LOGGER.warning("Unexpected number of " + attributeName
                    + " entries in " + methodInfo + ": " + numAnnotations);
            return annotations;
        }
    }
}
