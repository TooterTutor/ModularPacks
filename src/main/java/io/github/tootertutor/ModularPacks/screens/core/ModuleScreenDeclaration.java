package io.github.tootertutor.ModularPacks.screens.core;

import io.github.tootertutor.ModularPacks.config.ScreenType;

public record ModuleScreenDeclaration(
        ScreenType screenType,
        ScreenDefinition screenDefinition) {
}
