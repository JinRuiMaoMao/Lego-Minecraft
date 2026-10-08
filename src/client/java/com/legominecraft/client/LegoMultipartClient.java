package com.legominecraft.client;

import alexiil.mc.lib.multipart.api.render.PartStaticModelRegisterEvent;
import com.legominecraft.client.model.LegoPartModelBaker;
import com.legominecraft.client.model.TechnicPartModelBaker;
import com.legominecraft.client.model.VanillaBlockModelBaker;
import com.legominecraft.multipart.LegoPartModelKey;
import com.legominecraft.multipart.TechnicPartModelKey;
import com.legominecraft.multipart.VanillaBlockModelKey;
import net.fabricmc.api.ClientModInitializer;

public class LegoMultipartClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		PartStaticModelRegisterEvent.EVENT.register(renderer -> {
			renderer.register(LegoPartModelKey.class, LegoPartModelBaker.INSTANCE);
			renderer.register(TechnicPartModelKey.class, TechnicPartModelBaker.INSTANCE);
			renderer.register(VanillaBlockModelKey.class, VanillaBlockModelBaker.INSTANCE);
		});
	}
}
