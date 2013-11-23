package de.oceanlabs.mcp.mcinjector;

import java.util.ArrayList;

public class JsonStruct
{
	public EnclosingMethod enclosingMethod = null;
	public ArrayList<InnerClass> innerClasses = null;

	public static class EnclosingMethod
	{
		public final String owner;
		public final String name;
		public final String desc;

		EnclosingMethod(String owner, String name, String desc)
		{
			this.owner = owner;
			this.name = name;
			this.desc = desc;
		}
	}

	public static class InnerClass
	{
		public final String inner_class;
		public final String outer_class;
		public final String inner_name;
		public final String access;

		InnerClass(String inner_class, String outer_class, String inner_name, String access)
		{
			this.inner_class = inner_class;
			this.outer_class = outer_class;
			this.inner_name = inner_name;
			this.access = access;
		}

		public int getAccess()
		{
			return Integer.parseInt(access == null ? "0" : access, 16);
		}
	}

	public void setEnclosing(String owner, String name, String desc)
	{
		enclosingMethod = new EnclosingMethod(owner, name, desc);
	}

	public void addInner(String cls, String outer, String name, int access)
	{
		if (innerClasses == null) innerClasses = new ArrayList<InnerClass>();
		for (InnerClass i : innerClasses)
		{
			if (i.inner_class.equals(cls)) return; //Prevent duplicates
		}

		innerClasses.add(new InnerClass(cls, outer, name, access == 0 ? null : Integer.toHexString(access)));
	}
}
