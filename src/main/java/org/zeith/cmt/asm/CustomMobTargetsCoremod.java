package org.zeith.cmt.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

@IFMLLoadingPlugin.MCVersion("1.12.2")
public class CustomMobTargetsCoremod
		implements IFMLLoadingPlugin, IClassTransformer
{
	public static final Logger LOG = LogManager.getLogger("CustomMobTargets [PLUGIN]");

	@Override
	public String[] getASMTransformerClass()
	{
		return new String[]{ getClass().getName() };
	}

	@Override
	public String getModContainerClass()
	{
		return null;
	}

	@Nullable
	@Override
	public String getSetupClass()
	{
		return null;
	}

	static File asm = new File(".", "asm");

	@Override
	public void injectData(Map<String, Object> data)
	{
		File dir = (File) data.get("mcLocation");
		asm = new File(dir, "asm");
	}

	@Override
	public String getAccessTransformerClass()
	{
		return null;
	}

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass)
	{
		if(transformedName.equals("net.minecraft.entity.EntityLiving"))
			return handleEntityLiving(transformedName, basicClass, transformedName.equals(name));

		return basicClass;
	}

	private byte[] handleEntityLiving(String name, byte[] bytes, boolean deobfEnv)
	{
		return transform(name, bytes, node ->
		{
			String EntityLiving = node.name;
			for(MethodNode method : node.methods)
			{
				if(method.name.equals("<init>"))
				{
					LOG.info("Pathching " + name + "'s constructor.");

					AbstractInsnNode[] insnArr = method.instructions.toArray();

					for(int i = insnArr.length - 1; i >= 0; i--)
					{
						AbstractInsnNode insn = insnArr[i];
						if(insn instanceof MethodInsnNode && insn.getOpcode() == Opcodes.INVOKEVIRTUAL)
						{
							MethodInsnNode targetInsn = (MethodInsnNode) insn;
							if(targetInsn.owner.equals(node.name) && targetInsn.desc.equals("()V"))
							{
								InsnList inject = new InsnList();

								inject.add(new VarInsnNode(Opcodes.ALOAD, 0));
								inject.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/zeith/cmt/asm/ASM", "patchEntityAI", String.format("(L%s;)V", EntityLiving), false));

								method.instructions.insertBefore(targetInsn, inject);
								break;
							}
						}
					}
				}
			}
		});
	}

	private static byte[] transform(String name, byte[] bytes, Consumer<ClassNode> handler)
	{
		LOG.info("ASMing into " + name);
		ClassNode node = new ClassNode();
		ClassReader classReader = new ClassReader(bytes);
		classReader.accept(node, 0);
		handler.accept(node);
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		node.accept(writer);
		byte[] data = writer.toByteArray();
		if(asm.isDirectory())
			try(FileOutputStream fos = new FileOutputStream(new File(asm, name + ".class")))
			{
				fos.write(data);
			} catch(IOException e)
			{
				e.printStackTrace();
			}
		return data;
	}
}