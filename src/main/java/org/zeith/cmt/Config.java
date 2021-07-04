package org.zeith.cmt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.ResourceLocation;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class Config
{
	static final Map<ResourceLocation, AttackEntity> ENTITY_MAP = new HashMap<>();

	static File dst;

	static void setup(File dstDir)
	{
		dst = dstDir;
		reload();
	}

	public static AttackEntity get(ResourceLocation id)
	{
		return ENTITY_MAP.get(id);
	}

	public static void reload()
	{
		if(dst == null) return;

		if(!dst.isDirectory())
		{
			if(dst.mkdirs()) __template();
			else return;
		}

		ENTITY_MAP.clear();

		for(File file : getAllJsons())
		{
			try(FileReader r = new FileReader(file))
			{
				JsonObject root = new JsonParser().parse(r).getAsJsonObject();
				AttackEntity ent = new AttackEntity(root);
				ENTITY_MAP.put(new ResourceLocation(ent.entity), ent);
			} catch(IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	static File[] getAllJsons()
	{
		return dst.listFiles(f -> !f.getName().startsWith("__") && f.getName().toLowerCase(Locale.ROOT).endsWith(".json"));
	}

	static void __template()
	{
		File file = new File(dst, "__creeper.json");
		try
		{
			Files.write(file.toPath(), ("{\n" +
					"  \"entity\": \"minecraft:creeper\",\n" +
					"  \"targets\": [\n" +
					"    {\n" +
					"\t  \"priority\": 2,\n" +
					"\t  \"check_sight\": true,\n" +
					"\t  \"entity\": \"minecraft:sheep\"\n" +
					"    }\n" +
					"  ]\n" +
					"}").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
		} catch(IOException | ClassCastException | IllegalStateException | UnsupportedOperationException e)
		{
			e.printStackTrace();
		}

		file = new File(dst, "__README.txt");
		try
		{
			Files.write(file.toPath(), ("This folder is searched for JSON files that do not start with a double underscore.\n" +
					"All JSON files must have an \"entity\" string key (mod:entity) and a \"targets\" JSON array.\n" +
					"The \"targets\" array contains a list of JSON objects, for each entity target.\n" +
					"Each entity target object must have an \"entity\" string key (mod:entity), optionally integer \"priority\" and boolean \"check_sight\" entries may be added.\n" +
					"\"priority\" responds to how important the AI task for attacking is.\n" +
					"\"check_sight\" responds for the sight checking, in other words, should the entity target another entity if there is no direct sight.\n" +
					"\n" +
					"One extra note is the \"/cmt\" command\n" +
					"You can reload configs by running \"/cmt reload\"").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
		} catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	public static class AttackEntity
	{
		public final String entity;
		public final List<AttackTarget> targets;

		public AttackEntity(String entity, List<AttackTarget> targets)
		{
			this.entity = entity;
			this.targets = Collections.unmodifiableList(new ArrayList<>(targets));
		}

		public AttackEntity(JsonObject root)
		{
			this.entity = root.get("entity").getAsString();
			List<AttackTarget> targets = new ArrayList<>();
			JsonArray targetsJSON = root.getAsJsonArray("targets");
			for(JsonElement element : targetsJSON) targets.add(new AttackTarget(element.getAsJsonObject()));
			this.targets = Collections.unmodifiableList(targets);
		}
	}

	public static class AttackTarget
	{
		public final int priority;
		public final boolean check_sight;
		public final String entity;

		public AttackTarget(JsonObject root)
		{
			this.priority = root.get("priority").getAsInt();
			this.check_sight = root.get("check_sight").getAsBoolean();
			this.entity = root.get("entity").getAsString();
		}

		public AttackTarget(int priority, boolean check_sight, String entity)
		{
			this.priority = priority;
			this.check_sight = check_sight;
			this.entity = entity;
		}
	}
}