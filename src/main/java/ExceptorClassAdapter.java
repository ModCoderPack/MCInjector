import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

public class ExceptorClassAdapter extends ClassAdapter
{
    private static final Logger log = Logger.getLogger("Exceptor");
    private Exceptor exc;
    String className;

    public ExceptorClassAdapter(ClassVisitor cv, Exceptor exc)
    {
        super(cv);

        this.exc = exc;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
    {
        log.log(Level.FINE, "Class: " + name + " Extends: " + superName);

        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
    {
        MethodVisitor mv;
        log.log(Level.FINER, "Name: " + name + " Desc: " + desc + " Sig: " + signature);

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
            log.log(Level.FINEST, clsSig + exceptionList);
        }

        String excList = this.exc.mappings.getProperty(clsSig);
        if (excList != null)
        {
            log.log(Level.FINE, "Adding Exceptions: " + excList + " to " + clsSig);
            exceptions = getExceptions(excList);
        }

        mv = cv.visitMethod(access, name, desc, signature, exceptions);
        return mv;
    }

    private String[] getExceptions(String exceptionList)
    {
        return exceptionList.split(",");
    }
}
