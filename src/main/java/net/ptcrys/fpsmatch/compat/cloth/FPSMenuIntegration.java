package net.ptcrys.fpsmatch.compat.cloth;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.fml.ModLoadingContext;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import org.jetbrains.annotations.Nullable;

public class FPSMenuIntegration {

    public static ConfigBuilder getConfigBuilder() {
        ConfigBuilder root = ConfigBuilder.create().setTitle(Component.literal("FPSMatch"));
        root.setGlobalized(true);
        root.setGlobalizedExpanded(false);
        ConfigEntryBuilder entryBuilder = root.entryBuilder();

        FPSMClothConfig.initClient(root, entryBuilder);

        return root;
    }

    @SuppressWarnings("removal")
    public static void registerModsPage() {
        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class, () -> (container, parent) -> getConfigScreen(parent));
    }

    public static Screen getConfigScreen(@Nullable Screen parent) {
        return FPSMenuIntegration.getConfigBuilder().setParentScreen(parent).build();
    }
}
