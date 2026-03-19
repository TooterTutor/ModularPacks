package io.github.tootertutor.ModularPacks.screens.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import io.github.tootertutor.ModularPacks.config.ScreenType;
import io.github.tootertutor.ModularPacks.screens.declarations.FeedingModuleDeclaration;
import io.github.tootertutor.ModularPacks.screens.declarations.JukeboxModuleDeclaration;
import io.github.tootertutor.ModularPacks.screens.declarations.MagnetModuleDeclaration;
import io.github.tootertutor.ModularPacks.screens.declarations.RestockModuleDeclaration;
import io.github.tootertutor.ModularPacks.screens.declarations.VoidModuleDeclaration;

public final class BuiltInModuleDeclarations {

    private static final Map<String, ModuleDeclaration> DECLARATIONS = new LinkedHashMap<>();

    static {
        register(new FeedingModuleDeclaration());
        register(new MagnetModuleDeclaration());
        register(new VoidModuleDeclaration());
        register(new JukeboxModuleDeclaration());
        register(new RestockModuleDeclaration());
    }

    private BuiltInModuleDeclarations() {
    }

    public static ModuleDeclaration find(String moduleType) {
        if (moduleType == null)
            return null;
        return DECLARATIONS.get(moduleType.toLowerCase(Locale.ROOT));
    }

    public static ScreenType findPrimaryScreenType(String moduleType) {
        ModuleDeclaration declaration = find(moduleType);
        return declaration == null ? ScreenType.NONE : declaration.primaryScreenType();
    }

    public static Collection<ModuleDeclaration> all() {
        return DECLARATIONS.values();
    }

    private static void register(ModuleDeclaration declaration) {
        DECLARATIONS.put(declaration.moduleType().toLowerCase(Locale.ROOT), declaration);
    }
}
