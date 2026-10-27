package dev.xeu.forums.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.xeu.forums.Nav
import dev.xeu.forums.data.ForumRepository

/** A member's profile, shown natively (avatar, title and forum stats). */
@Composable
fun ProfileScreen(url: String, nav: Nav) {
    val loader = rememberLoader(url) { ForumRepository.profileByUrl(url) }
    val accents = LocalAccents.current
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { pad ->
        RefreshBox(loader.refreshing, loader.reload, Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                val p = (loader.state as? Load.Ok)?.value
                HeroHeader {
                    IconButton(
                        onClick = { nav.back() },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.18f)),
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = accents.onHero) }
                    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape).padding(3.dp)) {
                            AvatarImage(p?.avatarUrl, p?.username ?: " ", 88)
                        }
                        Text(
                            p?.username ?: "",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = accents.onHero,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        if (!p?.userTitle.isNullOrBlank()) Text(
                            p!!.userTitle, style = MaterialTheme.typography.bodyMedium,
                            color = accents.onHero.copy(alpha = 0.85f), textAlign = TextAlign.Center,
                        )
                    }
                }
                when (val s = loader.state) {
                    Load.Loading -> LoadingBox(Modifier.height(240.dp))
                    is Load.Err -> ErrorBox(s.message, loader.reload, Modifier.height(300.dp))
                    is Load.Ok -> {
                        if (s.value.stats.isNotEmpty()) {
                            SectionLabel("About")
                            SectionCard {
                                s.value.stats.forEachIndexed { i, (k, v) ->
                                    if (i > 0) androidx.compose.material3.HorizontalDivider(
                                        Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(k, style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(v, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
