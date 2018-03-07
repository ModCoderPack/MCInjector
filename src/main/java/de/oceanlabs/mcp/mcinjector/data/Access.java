package de.oceanlabs.mcp.mcinjector.data;

import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PROTECTED;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import de.oceanlabs.mcp.mcinjector.MCInjector;

public enum Access
{
    INSTANCE;


    public static enum Level
    {
        PRIVATE, DEFAULT, PROTECTED, PUBLIC;
        public static Level getFromBytecode(int acc)
        {
            if ((acc & ACC_PRIVATE)   == ACC_PRIVATE  ) return PRIVATE;
            if ((acc & ACC_PROTECTED) == ACC_PROTECTED) return PROTECTED;
            if ((acc & ACC_PUBLIC)    == ACC_PUBLIC   ) return PUBLIC;
            return DEFAULT;
        }
        public int setAccess(int acc)
        {
            acc &= ~(ACC_PRIVATE | ACC_PROTECTED | ACC_PUBLIC);
            acc |= this == PRIVATE   ? ACC_PRIVATE   : 0;
            acc |= this == PROTECTED ? ACC_PROTECTED : 0;
            acc |= this == PUBLIC    ? ACC_PUBLIC    : 0;
            return acc;
        }
    }

    private Map<String, Level> changes = new HashMap<>();

    public boolean load(Path file)
    {
        this.changes.clear();
        try
        {
            MCInjector.LOG.fine("Loading Access Changes from: " + file);
            Files.readAllLines(file).forEach(line ->
            {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    return;

                int idx = line.indexOf(' ');
                Level level = Level.valueOf(line.substring(0, idx));
                String key = line.substring(idx + 1);
                MCInjector.LOG.fine("    Access loaded " + level + " " + key);
                this.changes.put(key, level);
            });
        }
        catch (IOException e)
        {
            e.printStackTrace();
            MCInjector.LOG.warning("Could not load Access list: " + e.toString());
            return false;
        }
        return true;
    }

    public boolean dump(Path file)
    {
        try
        {
            List<String> ret = this.changes.entrySet().stream()
            .sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey()))
            .map(e -> e.getValue() + " " + e.getKey())
            .collect(Collectors.toList());
            Files.write(file, String.join("\n", ret).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            MCInjector.LOG.warning("Could not dump Access list: " + e.toString());
            return false;
        }
        return true;
    }

    public Level getLevel(String className)
    {
        return this.changes.get(className);
    }

    public Level getLevel(String cls, String name)
    {
        return this.changes.get(cls + " " + name);
    }

    public Level getLevel(String cls, String name, String desc)
    {
        return this.changes.get(cls + " " + name + " " + desc);
    }

}
