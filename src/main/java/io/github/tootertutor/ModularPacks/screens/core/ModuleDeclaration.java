package io.github.tootertutor.ModularPacks.screens.core;

import java.util.Collection;

import io.github.tootertutor.ModularPacks.config.ScreenType;

public interface ModuleDeclaration {

    String moduleType();

    String displayName();

    ScreenType primaryScreenType();

    Collection<ModuleScreenDeclaration> screens();

    default ScreenDefinition getScreenDefinition(ScreenType screenType) {
        if (screenType == null)
            return null;
        for (ModuleScreenDeclaration declaration : screens()) {
            if (declaration.screenType() == screenType)
                return declaration.screenDefinition();
        }
        return null;
    }
}
