import java.util.logging.Level;
import java.util.logging.Logger;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ExceptorClassAdapter extends ClassVisitor implements Opcodes
{
    private static final Logger log = Logger.getLogger("MCInjector");
    private MCInjector exc;
    String className;

    public ExceptorClassAdapter(ClassVisitor cv, MCInjector exc)
    {
        super(Opcodes.ASM4, cv);

        this.exc = exc;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
    {
        ExceptorClassAdapter.log.log(Level.FINE, "Class: " + name + " Extends: " + superName);

        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
    {
        MethodVisitor mv;
        ExceptorClassAdapter.log.log(Level.FINER, "Name: " + name + " Desc: " + desc + " Sig: " + signature);

        String clsSig = this.className + "." + name + desc;
        if ((exceptions != null) && (exceptions.length > 0))
        {
            String exceptionList = "=";
            for (String exception : exceptions)
            {
                if (exceptionList.equals("="))
                {
                    exceptionList += exception;
                }
                else
                {
                    exceptionList += "," + exception;
                }
            }
            ExceptorClassAdapter.log.log(Level.FINEST, clsSig + exceptionList);
        }

        String excList = this.exc.mappings.getProperty(clsSig);
        if (excList != null)
        {
            ExceptorClassAdapter.log.log(Level.FINE, "Adding Exceptions: " + excList + " to " + clsSig);
            exceptions = ExceptorClassAdapter.getExceptions(excList);
        }

        mv = this.cv.visitMethod(access, name, desc, signature, exceptions);
        return mv;
    }

    private static String[] getExceptions(String exceptionList)
    {
        return exceptionList.split(",");
    }
}
