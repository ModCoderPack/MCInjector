package de.oceanlabs.mcp.mcinjector;
import static joptsimple.internal.Reflection.invoke;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.oceanlabs.mcp.mcinjector.lvt.LVTNaming;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.ValueConverter;

public class MCInjector
{
    public static final Logger LOG = Logger.getLogger("MCInjector");
    public static final String VERSION = "MCInjector v" + Optional.ofNullable(MCInjector.class.getPackage().getImplementationVersion()).orElse("Unknown") + " by Searge, LexManos, Fesh0r";

    private Path fileIn, fileOut;
    private Path excIn, excOut;
    private Path accIn, accOut;
    private Path ctrIn;
    private Path prmIn, prmOut;
    private List<Path> classpath;
    private LVTNaming lvt;

    public MCInjector(Path fileIn, Path fileOut)
    {
        this.fileIn = fileIn;
        this.fileOut = fileOut;
    }

    public MCInjector log(Path log)
    {
        if (log == null)
            return this;

        try
        {
            FileHandler filehandler = new FileHandler(log.toString());
            filehandler.setFormatter(new LogFormatter());
            MCInjector.LOG.addHandler(filehandler);
        }
        catch (IOException e)
        {
            System.out.println("Failed to setup logger: " + e.toString());
            e.printStackTrace();
        }
        return this;
    }

    public MCInjector log() { return log(System.out); }
    public MCInjector log(PrintStream stream)
    {
        MCInjector.LOG.addHandler(new Handler()
        {
            @Override
            public void publish(LogRecord record)
            {
                stream.println(String.format(record.getMessage(), record.getParameters()));
            }
            @Override public void flush() {}
            @Override public void close() throws SecurityException {}
        });
        return this;
    }

    private MCInjector classpath(List<Path> clsPath) {
        this.classpath = clsPath;
        return this;
    }

    public MCInjector exceptions(Path exc)
    {
        this.excIn = exc;
        return this;
    }

    public MCInjector exceptionsOut(Path out)
    {
        this.excOut = out;
        return this;
    }

    public MCInjector access(Path acc)
    {
        this.accIn = acc;
        return this;
    }

    public MCInjector accessOut(Path out)
    {
        this.accOut = out;
        return this;
    }

    public MCInjector constructors(Path ctrs)
    {
        this.ctrIn = ctrs;
        return this;
    }

    public MCInjector parameters(Path ctrs)
    {
        this.prmIn = ctrs;
        return this;
    }

    public MCInjector parametersOut(Path out)
    {
        this.prmOut = out;
        return this;
    }

    public MCInjector lvt(LVTNaming lvt)
    {
        this.lvt = lvt;
        return this;
    }


    public void process() throws IOException
    {
        MCInjectorImpl.process(fileIn, fileOut,
                               accIn, accOut,
                               ctrIn,
                               excIn, excOut,
                               prmIn, prmOut,
                               lvt,
                               classpath);
    }

    private static ValueConverter<Path> PATH_ARG = new ValueConverter<Path>()
    {
        public Path convert( String value )
        {
            return Paths.get(value);
        }

        public Class<Path> valueType()
        {
            return Path.class;
        }

        public String valuePattern()
        {
            return null;
        }
    };
    private static ValueConverter<Path[]> MULTIPATH_ARG = new ValueConverter<Path[]>()
    {
        public Path[] convert(String value )
        {
            return Arrays.stream(value.split(File.pathSeparator)).map(Paths::get).toArray(Path[]::new);
        }

        public Class<Path[]> valueType()
        {
            return Path[].class;
        }

        public String valuePattern()
        {
            return null;
        }
    };
    private static ValueConverter<Level> LEVEL_ARG = new ValueConverter<Level>()
    {
        public Level convert( String value )
        {
            return Level.parse(value.toUpperCase(Locale.ENGLISH));
        }

        public Class<Level> valueType()
        {
            return Level.class;
        }

        public String valuePattern()
        {
            return null;
        }
    };

    public static void main(String[] args) throws Exception
    {
        OptionParser parser = new OptionParser();
        OptionSpec<Void>      help    = parser.accepts("help")     .forHelp();
        OptionSpec<Void>      ver     = parser.accepts("version")  .forHelp();
        OptionSpec<Path>      in      = parser.accepts("in")       .withRequiredArg().withValuesConvertedBy(PATH_ARG).required();
        OptionSpec<Path>      out     = parser.accepts("out")      .withRequiredArg().withValuesConvertedBy(PATH_ARG);
        OptionSpec<Path>      cfg     = parser.accepts("cfg")      .withRequiredArg().withValuesConvertedBy(PATH_ARG);
        OptionSpec<Path>      log     = parser.accepts("log")      .withRequiredArg().withValuesConvertedBy(PATH_ARG);
        OptionSpec<Path>      exc     = parser.accepts("exc")      .withRequiredArg().withValuesConvertedBy(PATH_ARG);
        OptionSpec<Path>      excOut  = parser.accepts("excOut")   .withRequiredArg().withValuesConvertedBy(PATH_ARG);
        OptionSpec<Path>      acc     = parser.accepts("acc")      .withRequiredArg().withValuesConvertedBy(PATH_ARG);
        OptionSpec<Path>      accOut  = parser.accepts("accOut")   .withRequiredArg().withValuesConvertedBy(PATH_ARG);
        OptionSpec<Path>      ctr     = parser.accepts("ctr")      .withRequiredArg().withValuesConvertedBy(PATH_ARG);
        OptionSpec<Path>      ctrOut  = parser.accepts("ctrOut")   .withRequiredArg().withValuesConvertedBy(PATH_ARG).describedAs("legacy, can't be used anymore");
        OptionSpec<Path>      prm     = parser.accepts("prm")      .withRequiredArg().withValuesConvertedBy(PATH_ARG);
        OptionSpec<Path>      prmOut  = parser.accepts("prmOut")   .withRequiredArg().withValuesConvertedBy(PATH_ARG);
        OptionSpec<Level>     logLvl  = parser.accepts("level")    .withRequiredArg().withValuesConvertedBy(LEVEL_ARG).defaultsTo(Level.INFO);
        OptionSpec<LVTNaming> lvt     = parser.accepts("lvt")      .withRequiredArg().ofType(LVTNaming.class).defaultsTo(LVTNaming.STRIP);
        OptionSpec<Path[]>    clsPath = parser.acceptsAll(Arrays.stream(new String[]{"classpath", "e"}).collect(Collectors.toList())).withRequiredArg().withValuesConvertedBy(MULTIPATH_ARG);

        try
        {
            OptionSet o;
            Set<Path> extraArgsFiles = new HashSet<>();
            while (true) {
                o = parser.parse(Stream.concat(Arrays.stream(args), extraArgsFiles.stream().flatMap(path -> {
                    try {
                        return Files.lines(path);
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                })).toArray(String[]::new));
                if (o.valuesOf(cfg).size() == extraArgsFiles.size()) {
                    break;
                }
                extraArgsFiles.addAll(o.valuesOf(cfg));
            }
            if (o.has(help))
            {
                System.out.println(VERSION);
                parser.printHelpOn(System.out);
                return;
            }
            else if (o.has(ver))
            {
                System.out.println(VERSION);
                return;
            }
            else if (o.has(ctrOut))
            {
                System.out.println("ctrOut is using the legacy format and is no longer supported!");
                return;
            }


            MCInjector.LOG.setUseParentHandlers(false);
            MCInjector.LOG.setLevel(o.valueOf(logLvl));

            LOG.info(MCInjector.VERSION);
            LOG.info("Input:        " + o.valueOf(in));
            LOG.info("Output:       " + o.valueOf(out));
            LOG.info("Log:          " + o.valueOf(log));
            LOG.info("Exceptions:   " + o.valueOf(exc));
            LOG.info("              " + o.valueOf(excOut));
            LOG.info("Access:       " + o.valueOf(acc));
            LOG.info("              " + o.valueOf(accOut));
            LOG.info("Constructors: " + o.valueOf(ctr));
            LOG.info("Extra Params: " + o.valueOf(prm));
            LOG.info("              " + o.valueOf(prmOut));
            LOG.info("LVT:          " + o.valueOf(lvt));
            if (o.hasArgument(clsPath))
                for(Path[] pp : o.valuesOf(clsPath))
                    for(Path p : pp)
                        LOG.info("Classpath:    " + p);

            try
            {
                new MCInjector(o.valueOf(in), o.valueOf(out))
                    .log()
                    .lvt(o.valueOf(lvt))
                    .log(o.valueOf(log))
                    .classpath(o.valuesOf(clsPath).stream().flatMap(Arrays::stream).collect(Collectors.toList()))
                    .exceptions(o.valueOf(exc))
                    .exceptionsOut(o.valueOf(excOut))
                    .access(o.valueOf(acc))
                    .accessOut(o.valueOf(accOut))
                    .constructors(o.valueOf(ctr))
                    .parameters(o.valueOf(prm))
                    .parametersOut(o.valueOf(prmOut))
                    .process();
            }
            catch (Exception e)
            {
                System.err.println("ERROR: " + e.getMessage());
                MCInjector.LOG.log(Level.SEVERE, "ERROR", e);
                e.printStackTrace();
                System.exit(1);
            }
        }
        catch (OptionException e)
        {
            parser.printHelpOn(System.out);
            e.printStackTrace();
        }
    }
}
