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

public enum Constructors
{
    INSTANCE;

    private Map<String, Integer> fromDesc = new HashMap<>();
    private Map<Integer, String> fromID = new HashMap<>();
    private int maxID  = 0;

    public boolean load(Path file)
    {
        this.fromDesc.clear();
        this.fromID.clear();
        try
        {
            MCInjector.LOG.fine("Loading Constructors from: " + file);
            Files.readAllLines(file).forEach(line ->
            {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    return;

                String[] parts = line.split(" " );
                int id = Integer.parseInt(parts[0]);
                MCInjector.LOG.fine("    Constructor ID loaded " + id + " " + parts[0] + " " + parts[1]);
                this.setID(parts[1], parts[2], id);
            });
        }
        catch (IOException e)
        {
            e.printStackTrace();
            MCInjector.LOG.warning("Could not load Constructors list: " + e.toString());
            return false;
        }
        return true;
    }

    public boolean dump(Path file)
    {
        try
        {
            List<String> ret = this.fromID.entrySet().stream()
            .sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey()))
            .map(e -> e.getKey() + " " + e.getValue())
            .collect(Collectors.toList());
            Files.write(file, String.join("\n", ret).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            MCInjector.LOG.warning("Could not dump Constructors list: " + e.toString());
            return false;
        }
        return true;
    }

    public void setID(String cls, String desc, int id)
    {
        if (id < 0)
            throw new IllegalArgumentException("ID must be positive: " + id);
        this.maxID = Math.max(this.maxID, id);
        this.fromDesc.put(cls + " " + desc, id);
        this.fromID  .put(id, cls + " " + desc);
    }

    public int getID(String cls, String desc, boolean generate)
    {
        Integer id = this.fromDesc.get(cls + " " + desc);
        if (id == null)
        {
            if (!generate)
                return -1;
            id = ++maxID;
            this.setID(cls, desc, id);
        }
        return id;
    }
}
