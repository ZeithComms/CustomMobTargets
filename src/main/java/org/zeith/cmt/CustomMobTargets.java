package org.zeith.cmt;

import com.zeitheron.hammercore.HammerCore;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLFingerprintViolationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(modid = CustomMobTargets.MOD_ID, name = CustomMobTargets.MOD_NAME, version = "@VERSION@", certificateFingerprint = "@FINGERPRINT@", updateJSON = "http://dccg.herokuapp.com/api/fmluc/@CF_ID@")
public class CustomMobTargets
{
	public static final String MOD_ID = "cmt";
	public static final String MOD_NAME = "Custom Mob Targets";

	public static final Logger LOG = LogManager.getLogger();

	@Mod.EventHandler
	public void certificateViolation(FMLFingerprintViolationEvent e)
	{
		LOG.warn("*****************************");
		LOG.warn("WARNING: Somebody has been tampering with " + CustomMobTargets.MOD_NAME + " jar!");
		LOG.warn("It is highly recommended that you redownload mod from https://www.curseforge.com/projects/@CF_ID@ !");
		LOG.warn("*****************************");
		HammerCore.invalidCertificates.put(CustomMobTargets.MOD_ID, "https://www.curseforge.com/projects/@CF_ID@");
	}

	@Mod.EventHandler
	public void preInit(FMLPreInitializationEvent e)
	{
		Config.setup(new File(e.getModConfigurationDirectory(), "CustomMobTargets"));
	}

	@Mod.EventHandler
	public void serverStarting(FMLServerStartingEvent e)
	{
		Config.reload();
		e.registerServerCommand(new CMTCommand());
	}
}