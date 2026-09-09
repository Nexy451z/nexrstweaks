package com.nexy451z.nexrstweaks;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(NexRSTweaks.MODID)
public class NexRSTweaks {
    public static final String MODID = "nexrstweaks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NexRSTweaks() {
        LOGGER.info("[NexRSTweaks] init");
    }
}
