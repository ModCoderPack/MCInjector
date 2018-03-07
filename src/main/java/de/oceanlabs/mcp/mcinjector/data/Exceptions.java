package de.oceanlabs.mcp.mcinjector.data;

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

public enum Exceptions
{
    INSTANCE;

    private Map<String, String[]> exceptions = new HashMap<>();

    public boolean load(Path file)
    {
        this.exceptions.clear();
        try
        {
            MCInjector.LOG.fine("Loading Exceptions from: " + file);
            Files.readAllLines(file).forEach(line ->
            {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    return;

                int idx = line.indexOf(' ', line.indexOf(' ') + 1);
                if (idx == -1)
                {
                    MCInjector.LOG.warning("    Illegal Access Config Line: " + line);
                    return;
                }

                String key = line.substring(0, idx);
                String[] excs = line.substring(idx + 1).split(" ");
                MCInjector.LOG.fine("    Exceptions loaded " + key + ": (" + String.join(", ", excs) + ")");
                this.exceptions.put(key, excs);
            });
        }
        catch (IOException e)
        {
            e.printStackTrace();
            MCInjector.LOG.warning("Could not load Exception list: " + e.toString());
            return false;
        }
        return true;
    }

    public boolean dump(Path file)
    {
        try
        {
            List<String> ret = this.exceptions.entrySet().stream()
            .sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey()))
            .map(e -> e.getKey() + " " + String.join(" ", e.getValue()))
            .collect(Collectors.toList());
            Files.write(file, String.join("\n", ret).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            MCInjector.LOG.warning("Could not dump Exceptions list: " + e.toString());
            return false;
        }
        return true;
    }

    public String[] getExceptions(String cls, String name, String desc)
    {
        String[] ret = this.exceptions.get(cls + "/" + name + " " + desc);
        return ret == null ? new String[0] : ret;
    }

    public void setExceptions(String cls, String name, String desc, String[] excs)
    {
        this.exceptions.put(cls + "/" + name + " " + desc, excs);
    }
}
