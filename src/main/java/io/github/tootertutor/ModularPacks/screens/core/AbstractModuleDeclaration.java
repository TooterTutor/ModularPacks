package io.github.tootertutor.ModularPacks.screens.core;

import java.util.Collection;
import java.util.List;

import io.github.tootertutor.ModularPacks.config.ScreenType;

public abstract class AbstractModuleDeclaration implements ModuleDeclaration {

    private final String moduleType;
    private final String displayName;
    private final ScreenType primaryScreenType;
    private final List<ModuleScreenDeclaration> screens;

    protected AbstractModuleDeclaration(String moduleType, String displayName, ScreenType primaryScreenType,
            ModuleScreenDeclaration... screens) {
        this.moduleType = moduleType;
        this.displayName = displayName;
        this.primaryScreenType = primaryScreenType;
        this.screens = List.of(screens);
    }

    @Override
    public String moduleType() {
        return moduleType;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public ScreenType primaryScreenType() {
        return primaryScreenType;
    }

    @Override
    public Collection<ModuleScreenDeclaration> screens() {
        return screens;
    }
}
