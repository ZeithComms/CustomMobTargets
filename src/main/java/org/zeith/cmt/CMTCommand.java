package org.zeith.cmt;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.command.CommandTreeBase;

public class CMTCommand
		extends CommandTreeBase
{
	public CMTCommand()
	{
		addSubcommand(new Reload());
	}

	@Override
	public String getName()
	{
		return "cmt";
	}

	@Override
	public int getRequiredPermissionLevel()
	{
		return 2;
	}

	@Override
	public String getUsage(ICommandSender sender)
	{
		return "?";
	}
}

class Reload
		extends CommandBase
{
	@Override
	public String getName()
	{
		return "reload";
	}

	@Override
	public String getUsage(ICommandSender sender)
	{
		return "?";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args)
	{
		Config.reload();
		notifyCommandListener(sender, this, "Reload successful!");
	}
}