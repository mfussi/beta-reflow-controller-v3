package com.tangentlines.reflowclient.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import org.jetbrains.compose.resources.Font
import reflow_mpp_client.app.generated.resources.Res
import reflow_mpp_client.app.generated.resources.cizel
import reflow_mpp_client.app.generated.resources.notosans

// Variable font families (compose-time; fine to keep @Composable)
@Composable
internal fun createCizelFamily() = FontFamily(
    Font(Res.font.cizel, style = FontStyle.Normal, weight = FontWeight.W100, variationSettings = FontVariation.Settings(FontVariation.weight(100))),
    Font(Res.font.cizel, style = FontStyle.Normal, weight = FontWeight.W200, variationSettings = FontVariation.Settings(FontVariation.weight(200))),
    Font(Res.font.cizel, style = FontStyle.Normal, weight = FontWeight.W300, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(Res.font.cizel, style = FontStyle.Normal, weight = FontWeight.W400, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(Res.font.cizel, style = FontStyle.Normal, weight = FontWeight.W500, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(Res.font.cizel, style = FontStyle.Normal, weight = FontWeight.W600, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(Res.font.cizel, style = FontStyle.Normal, weight = FontWeight.W700, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(Res.font.cizel, style = FontStyle.Normal, weight = FontWeight.W800, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    Font(Res.font.cizel, style = FontStyle.Normal, weight = FontWeight.W900, variationSettings = FontVariation.Settings(FontVariation.weight(900))),
)

@Composable
internal fun createNotosansFamily() = FontFamily(
    Font(Res.font.notosans, style = FontStyle.Normal, weight = FontWeight.W100, variationSettings = FontVariation.Settings(FontVariation.weight(100))),
    Font(Res.font.notosans, style = FontStyle.Normal, weight = FontWeight.W200, variationSettings = FontVariation.Settings(FontVariation.weight(200))),
    Font(Res.font.notosans, style = FontStyle.Normal, weight = FontWeight.W300, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(Res.font.notosans, style = FontStyle.Normal, weight = FontWeight.W400, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(Res.font.notosans, style = FontStyle.Normal, weight = FontWeight.W500, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(Res.font.notosans, style = FontStyle.Normal, weight = FontWeight.W600, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(Res.font.notosans, style = FontStyle.Normal, weight = FontWeight.W700, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(Res.font.notosans, style = FontStyle.Normal, weight = FontWeight.W800, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    Font(Res.font.notosans, style = FontStyle.Normal, weight = FontWeight.W900, variationSettings = FontVariation.Settings(FontVariation.weight(900))),
)

// Convenience helpers when you want explicit sizes
@Composable
fun textStyleBody(size: TextUnit) = TextStyle(
    fontFamily = createNotosansFamily(),
    fontSize = size,
    lineHeight = size * 1.25f,
    fontWeight = FontWeight.W400,
    lineBreak = LineBreak.Paragraph,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.FirstLineTop,
        mode = LineHeightStyle.Mode.Fixed,
    )
)

@Composable fun textStyleH1(size: TextUnit) = TextStyle(
    fontFamily = createCizelFamily(),
    fontSize   = size,
    lineHeight = size * 1.2f,
    fontWeight = FontWeight.W900,
)

@Composable fun textStyleH2(size: TextUnit) = TextStyle(
    fontFamily = createCizelFamily(),
    fontSize   = size,
    lineHeight = size * 1.2f,
    fontWeight = FontWeight.W900,
)

@Composable fun textStyleH3(size: TextUnit) = TextStyle(
    fontFamily = createCizelFamily(),
    fontSize   = size,
    lineHeight = size * 1.2f,
    fontWeight = FontWeight.W700,
)

@Composable fun textStyleH4(size: TextUnit) = TextStyle(
    fontFamily = createNotosansFamily(),
    fontSize   = size,
    lineHeight = size * 1.2f,
    fontWeight = FontWeight.W300,
)