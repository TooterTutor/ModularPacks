package io.github.tootertutor.ModularPacks.screens.core;

import io.github.tootertutor.ModularPacks.config.ScreenType;

@FunctionalInterface
public interface ScreenTitleResolver {

    String resolve(ScreenType screenType, String moduleType, String filterMode);
}
