package com.islandskiesastro.astroplanner

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import astroplanner.composeapp.generated.resources.Res
import astroplanner.composeapp.generated.resources.ic_default_cluster
import astroplanner.composeapp.generated.resources.ic_default_galaxy
import astroplanner.composeapp.generated.resources.ic_default_nebula
import astroplanner.composeapp.generated.resources.ic_default_planet
import astroplanner.composeapp.generated.resources.ic_default_star
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun defaultImagePainter(type: ObjectType): Painter = painterResource(
    when (type) {
        ObjectType.STAR    -> Res.drawable.ic_default_star
        ObjectType.GALAXY  -> Res.drawable.ic_default_galaxy
        ObjectType.NEBULA  -> Res.drawable.ic_default_nebula
        ObjectType.CLUSTER -> Res.drawable.ic_default_cluster
        ObjectType.PLANET  -> Res.drawable.ic_default_planet
        ObjectType.VARIABLE_STAR -> Res.drawable.ic_default_star
        ObjectType.UNKNOWN -> Res.drawable.ic_default_star
    }
)
